package com.kuroshimira.nyanpasu.work
import com.kuroshimira.nyanpasu.network.AppHttpClient
import com.kuroshimira.nyanpasu.network.ImageUrlFallback
import com.kuroshimira.nyanpasu.network.NetworkImageLoader
import com.kuroshimira.nyanpasu.network.NetworkStatus
import com.kuroshimira.nyanpasu.network.WallpaperUrlPolicy
import com.kuroshimira.nyanpasu.schedule.AutoUpdateNotifier
import com.kuroshimira.nyanpasu.schedule.AutoWallpaperScheduler
import com.kuroshimira.nyanpasu.wallpaper.ImageProcessor
import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import com.kuroshimira.nyanpasu.wallpaper.WallpaperWriteGuard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val DOWNLOAD_RETRY = 3
        private const val DOWNLOAD_RETRY_DELAY_MS = 800L
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

        val complementLock = inputData.getBoolean("COMPLEMENT_LOCK_ONLY", false)
        if (complementLock) {
            if (WallpaperFiles.isDualWallpaperComplete(applicationContext)) {
                Log.d(TAG, "Complement skipped: dual wallpaper already complete")
                return Result.success()
            }
            if (!NetworkStatus.shouldAttemptNetworkWork(applicationContext)) {
                Log.w(TAG, "Complement skipped: no usable network")
                return Result.failure()
            }
            if (WallpaperWriteGuard.isWriteInProgress()) {
                return Result.retry()
            }
            return try {
                val complementParams = resolveParams(isAuto = false, isUrgent = true, prefs)
                val outcome =
                    WallpaperJobRunner.runLockComplement(
                        context = applicationContext,
                        styleValue = complementParams.styleValue,
                        strictTags = complementParams.strictTags,
                        softTags = complementParams.softTags,
                        homeState = complementParams.homeState,
                        lockState = complementParams.lockState,
                        r18Mode = complementParams.r18Mode,
                        downloadBitmap = { url -> downloadBitmap(imageLoader(applicationContext), url) },
                    )
                if (outcome.ok) Result.success(outcome.toWorkData()) else Result.failure(outcome.toWorkData())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Complement worker failed", e)
                Result.failure(WallpaperJobOutcome.downloadFailed().toWorkData())
            }
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

        if ((isAuto || isUrgent) && !NetworkStatus.shouldAttemptNetworkWork(applicationContext)) {
            Log.w(TAG, "Skipped: no usable network")
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
                recentUrls = WallpaperPrefs.recentUrlsForDedup(prefs),
                downloadBitmap = { url -> downloadBitmap(imageLoader(applicationContext), url) },
            )
            val output = outcome.toWorkData()
            if (isAuto) {
                AutoUpdateNotifier.showJobResult(applicationContext, outcome)
                AutoWallpaperScheduler.onAutoWorkCompleted(applicationContext, tags)
            }
            if (outcome.ok) Result.success(output) else Result.failure(output)
        } catch (e: CancellationException) {
            Log.d(TAG, "Worker cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            if (isAuto) {
                AutoUpdateNotifier.showFailure(applicationContext)
                AutoWallpaperScheduler.onAutoWorkCompleted(applicationContext, tags)
            }
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

    /** 预取任务入队后偏好变更时，跳过写入避免覆盖新槽位。 */
    private fun isPrefetchSnapshotStale(prefs: android.content.SharedPreferences): Boolean {
        val expected = inputData.getString("PREFETCH_FINGERPRINT") ?: return false
        if (expected.isEmpty()) return false
        return expected != WallpaperPrefs.prefetchSnapshotFingerprint(prefs)
    }

    private fun isPeriodicAutoRun(): Boolean =
        tags.contains(WallpaperWorkNames.TAG_AUTO_PERIODIC)

    private fun isDailyChainAutoRun(): Boolean =
        tags.contains(WallpaperWorkNames.TAG_DAILY_CHAIN)

    private suspend fun workInfosFor(uniqueName: String): List<WorkInfo> =
        withContext(Dispatchers.IO) {
            WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWork(uniqueName)
                .get()
        }

    private suspend fun isAutoApplyActive(): Boolean {
        val infos = workInfosFor(WallpaperWorkNames.APPLY_AUTO)
        return WallpaperWorkNames.isApplyUrgentActive(infos)
    }

    private suspend fun isPeriodicApplyRunning(): Boolean {
        if (isPeriodicAutoRun()) return false
        val infos = workInfosFor(WallpaperWorkNames.AUTO_PERIODIC)
        return WallpaperWorkNames.isPeriodicApplyRunning(infos, excludeId = id)
    }

    /** 自动 / 周期 apply 让位给进行中的写入、手动 urgent 或其它自动任务。 */
    private suspend fun shouldDeferAutoApply(): Boolean {
        if (WallpaperWriteGuard.isWriteInProgress()) return true
        if (isPeriodicApplyRunning()) return true
        val urgentInfos = workInfosFor(WallpaperWorkNames.APPLY_URGENT)
        if (WallpaperWorkNames.isManualApplyActive(urgentInfos)) return true
        if (isDailyChainAutoRun()) return false
        val autoInfos = workInfosFor(WallpaperWorkNames.APPLY_AUTO)
        return autoInfos.any {
            (it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED) &&
                it.id != id
        }
    }

    private suspend fun downloadBitmap(loader: ImageLoader, url: String): Bitmap? {
        val candidates = ImageUrlFallback.candidates(url)
        if (candidates.isEmpty()) {
            Log.e(TAG, "download blocked: no allowed candidates")
            return null
        }
        val maxDim = ImageProcessor.maxDownloadDimension(applicationContext)
        for (candidate in candidates) {
            val shortUrl = if (candidate.length > 96) candidate.take(96) + "…" else candidate
            for (attempt in 0 until DOWNLOAD_RETRY) {
                downloadViaCoil(loader, candidate, maxDim)?.let {
                    Log.d(TAG, "download ok via coil $shortUrl")
                    return it
                }
                downloadViaOkHttp(candidate, maxDim)?.let {
                    Log.d(TAG, "download ok via okhttp $shortUrl")
                    return it
                }
                if (attempt < DOWNLOAD_RETRY - 1) delay(DOWNLOAD_RETRY_DELAY_MS)
            }
            Log.w(TAG, "download candidate failed: $shortUrl")
        }
        Log.e(TAG, "download gave up after ${candidates.size} candidate(s)")
        return null
    }

    private suspend fun downloadViaCoil(loader: ImageLoader, url: String, maxDim: Int): Bitmap? {
        return try {
            val request =
                ImageRequest.Builder(applicationContext)
                    .data(url)
                    .size(maxDim)
                    .allowHardware(false)
                    .build()
            when (val outcome = loader.execute(request)) {
                is SuccessResult -> {
                    val bmp = (outcome.drawable as? BitmapDrawable)?.bitmap
                    bmp?.copy(Bitmap.Config.ARGB_8888, false) ?: bmp
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "coil download failed $url: ${e.message}")
            null
        }
    }

    private suspend fun downloadViaOkHttp(url: String, maxDim: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).get().build()
                AppHttpClient.imageClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bytes = response.body?.bytes() ?: return@withContext null
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                    ImageProcessor.downscaleIfNeeded(decoded, maxDim)
                }
            } catch (e: Exception) {
                Log.w(TAG, "okhttp download failed $url: ${e.message}")
                null
            }
        }
}
