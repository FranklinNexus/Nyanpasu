package com.kuroshimira.nyanpasu.work
import com.kuroshimira.nyanpasu.network.NetworkImageLoader
import com.kuroshimira.nyanpasu.network.NetworkStatus
import com.kuroshimira.nyanpasu.network.WallpaperUrlPolicy
import com.kuroshimira.nyanpasu.schedule.AutoUpdateNotifier
import com.kuroshimira.nyanpasu.wallpaper.ImageProcessor
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import com.kuroshimira.nyanpasu.wallpaper.WallpaperWriteGuard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val DOWNLOAD_RETRY = 2
        private const val DOWNLOAD_RETRY_DELAY_MS = 400L
        private const val TAG = "WallpaperWorker"

        @Volatile
        private var imageLoaderInstance: ImageLoader? = null

        private fun imageLoader(context: Context): ImageLoader {
            return imageLoaderInstance ?: synchronized(this) {
                imageLoaderInstance ?:
                    NetworkImageLoader.forApp(context.applicationContext).also { imageLoaderInstance = it }
            }
        }
    }

    override suspend fun doWork(): Result {
        val isAuto = inputData.getBoolean("IS_AUTO", false)
        val isUrgent = inputData.getBoolean("IS_URGENT", false)
        val bufferSlot = inputData.getString("BUFFER_SLOT") ?: "a"

        val prefs = WallpaperPrefs.prefs(applicationContext)
        if (isAuto && !WallpaperPrefs.isAutoUpdateEnabled(prefs)) {
            Log.d(TAG, "Skipped: auto update disabled")
            return Result.success()
        }

        val params = resolveParams(isAuto, isUrgent, prefs)

        if (!isAuto && !isUrgent && !WallpaperPrefs.canApplyWallpaper(prefs)) {
            Log.w(TAG, "Skipped prefetch: Home and Lock both off")
            return Result.success()
        }

        if ((isAuto || isUrgent) && !WallpaperPrefs.canApplyWallpaper(prefs)) {
            Log.w(TAG, "Skipped: Home and Lock both off")
            return Result.success()
        }

        if (isUrgent && WallpaperWriteGuard.isWriteInProgress()) {
            Log.d(TAG, "Write in progress, retry later")
            return Result.retry()
        }

        if (isUrgent && !isAuto && (isPeriodicApplyRunning() || isAutoApplyActive())) {
            Log.d(TAG, "Another apply work active, defer manual")
            return Result.retry()
        }

        if (isUrgent && isAuto && shouldDeferAutoApply()) {
            Log.d(TAG, "Another apply work active, defer auto")
            return Result.retry()
        }

        if ((isAuto || isUrgent) && !NetworkStatus.isConnected(applicationContext)) {
            Log.w(TAG, "Skipped: no network")
            if (isAuto) {
                AutoUpdateNotifier.recordFailure(applicationContext)
                return Result.retry()
            }
            return Result.failure()
        }

        if (!isAuto && !isUrgent && isPrefetchSnapshotStale(prefs)) {
            Log.d(TAG, "Prefetch snapshot stale, skipping")
            return Result.success()
        }

        return try {
            val outcome = WallpaperJobRunner.run(
                context = applicationContext,
                styleValue = params.styleValue,
                strictTags = params.strictTags,
                softTags = params.softTags,
                homeState = params.homeState,
                lockState = params.lockState,
                r18Mode = params.r18Mode,
                isUrgent = isUrgent,
                bufferSlot = bufferSlot,
                recentUrls = WallpaperPrefs.readRecentFetchedUrls(prefs),
                downloadBitmap = { url -> downloadBitmap(imageLoader(applicationContext), url) },
            )
            val output = outcome.toWorkData()
            if (isAuto) {
                AutoUpdateNotifier.showJobResult(applicationContext, outcome)
            }
            if (outcome.ok) Result.success(output) else Result.failure(output)
        } catch (e: CancellationException) {
            Log.d(TAG, "Worker cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            if (isAuto) AutoUpdateNotifier.showFailure(applicationContext)
            Result.failure(WallpaperJobOutcome.downloadFailed().toWorkData())
        }
    }

    private data class JobParams(
        val styleValue: Int,
        val strictTags: Array<String>,
        val softTags: Array<String>,
        val homeState: Int,
        val lockState: Int,
        val r18Mode: Int,
    )

    /** 自动 / 手动 urgent 均读最新 Prefs；仅预取槽任务用 InputData 快照。 */
    private fun resolveParams(isAuto: Boolean, isUrgent: Boolean, prefs: android.content.SharedPreferences): JobParams {
        if (isAuto || isUrgent) {
            val (strict, soft) = WallpaperPrefs.readTags(prefs)
            return JobParams(
                styleValue = prefs.getInt(WallpaperPrefs.KEY_STYLE, WallpaperPrefs.DEFAULT_STYLE),
                strictTags = strict,
                softTags = soft,
                homeState = prefs.getInt(WallpaperPrefs.KEY_HOME_STATE, 1),
                lockState = prefs.getInt(WallpaperPrefs.KEY_LOCK_STATE, 0),
                r18Mode = prefs.getInt(WallpaperPrefs.KEY_R18_MODE, 0),
            )
        }
        return JobParams(
            styleValue = inputData.getInt("STYLE_VALUE", WallpaperPrefs.DEFAULT_STYLE),
            strictTags = inputData.getStringArray("STRICT_TAGS") ?: emptyArray(),
            softTags = inputData.getStringArray("SOFT_TAGS") ?: emptyArray(),
            homeState = inputData.getInt("HOME_STATE", 1),
            lockState = inputData.getInt("LOCK_STATE", 0),
            r18Mode = inputData.getInt("R18_MODE", 0),
        )
    }

    private fun isAutoApplyActive(): Boolean {
        val infos = WorkManager.getInstance(applicationContext)
            .getWorkInfosForUniqueWork(WallpaperWorkNames.APPLY_AUTO)
            .get()
        return WallpaperWorkNames.isApplyUrgentActive(infos)
    }

    private fun isPeriodicApplyRunning(): Boolean {
        val infos = WorkManager.getInstance(applicationContext)
            .getWorkInfosForUniqueWork(WallpaperWorkNames.AUTO_PERIODIC)
            .get()
        return WallpaperWorkNames.isPeriodicApplyRunning(infos, excludeId = id)
    }

    /** 预取任务入队后偏好变更时，跳过写入避免覆盖新槽位。 */
    private fun isPrefetchSnapshotStale(prefs: android.content.SharedPreferences): Boolean {
        val expected = inputData.getString("PREFETCH_FINGERPRINT") ?: return false
        if (expected.isEmpty()) return false
        return expected != WallpaperPrefs.prefetchSnapshotFingerprint(prefs)
    }

    /** 自动 / 周期 apply 让位给进行中的写入、手动 urgent 或其它自动任务。 */
    private fun shouldDeferAutoApply(): Boolean {
        if (WallpaperWriteGuard.isWriteInProgress()) return true
        if (isPeriodicApplyRunning()) return true
        val wm = WorkManager.getInstance(applicationContext)
        val urgentInfos = wm.getWorkInfosForUniqueWork(WallpaperWorkNames.APPLY_URGENT).get()
        if (WallpaperWorkNames.isManualApplyActive(urgentInfos)) return true
        val autoInfos = wm.getWorkInfosForUniqueWork(WallpaperWorkNames.APPLY_AUTO).get()
        return autoInfos.any {
            (it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED) &&
                it.id != id
        }
    }

    private suspend fun downloadBitmap(loader: ImageLoader, url: String): Bitmap? {
        if (!WallpaperUrlPolicy.isAllowed(url)) {
            Log.e(TAG, "download blocked by url policy")
            return null
        }
        val shortUrl = if (url.length > 96) url.take(96) + "…" else url
        val maxDim = ImageProcessor.maxDownloadDimension(applicationContext)
        for (attempt in 0 until DOWNLOAD_RETRY) {
            try {
                val request =
                    ImageRequest.Builder(applicationContext)
                        .data(url)
                        .size(maxDim)
                        .allowHardware(false)
                        .build()
                when (val outcome = loader.execute(request)) {
                    is SuccessResult -> {
                        val bmp = (outcome.drawable as? BitmapDrawable)?.bitmap
                        if (bmp != null) {
                            return bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp
                        }
                        Log.w(TAG, "download: no bitmap (attempt ${attempt + 1}) $shortUrl")
                    }
                    else -> {
                        Log.w(TAG, "download: ${outcome::class.simpleName} (attempt ${attempt + 1}) $shortUrl")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "download failed (attempt ${attempt + 1}) $shortUrl", e)
            }
            if (attempt < DOWNLOAD_RETRY - 1) delay(DOWNLOAD_RETRY_DELAY_MS)
        }
        Log.e(TAG, "download gave up: $shortUrl")
        return null
    }
}
