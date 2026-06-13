package com.kuroshimira.nyanpasu.work

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.Data
import com.kuroshimira.nyanpasu.network.WallpaperImageIdentity
import com.kuroshimira.nyanpasu.search.SetuSearchEngine
import com.kuroshimira.nyanpasu.wallpaper.ImageProcessor
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplier
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult
import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import com.kuroshimira.nyanpasu.wallpaper.WallpaperHistory
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import com.kuroshimira.nyanpasu.wallpaper.WallpaperTargetMode
import com.kuroshimira.nyanpasu.wallpaper.WallpaperWriteGuard
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

object WallpaperJobRunner {

    private const val TAG = "WallpaperJobRunner"
    private const val SEARCH_TIMEOUT_HOME_MS = 90_000L
    private const val SEARCH_TIMEOUT_LOCK_MS = 60_000L

    suspend fun run(
        context: Context,
        styleValue: Int,
        strictTags: Array<String>,
        softTags: Array<String>,
        homeState: Int,
        lockState: Int,
        r18Mode: Int,
        isUrgent: Boolean,
        bufferSlot: String,
        recentUrls: Set<String>,
        downloadBitmap: suspend (String) -> Bitmap?,
    ): WallpaperJobOutcome = withContext(Dispatchers.IO) {
        val targetFilename = when {
            isUrgent -> WallpaperFiles.HOME
            bufferSlot == "b" -> WallpaperFiles.BUFFER_B
            else -> WallpaperFiles.BUFFER_A
        }
        val sync = WallpaperApplier.isSyncMode(homeState, lockState)
        val dual = WallpaperTargetMode.isDualMode(homeState, lockState)
        val prefs = WallpaperPrefs.prefs(context)
        val homeRequired = WallpaperTargetMode.homeRequired(homeState)

        val searchCtx = SetuSearchEngine.Context(
            styleValue = styleValue,
            strictTags = strictTags,
            softTags = softTags,
            recentUrls = recentUrls,
        )

        var homeUrl = ""
        if (homeRequired) {
            homeUrl = try {
                withTimeout(SEARCH_TIMEOUT_HOME_MS) {
                    SetuSearchEngine.search(context, searchCtx, r18Mode)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: TimeoutCancellationException) {
                Log.e(TAG, "Setu search (home) timed out")
                ""
            }
            coroutineContext.ensureActive()
            if (homeUrl.isEmpty()) {
                Log.e(TAG, "Setu search returned no URL")
                return@withContext WallpaperJobOutcome.searchFailed()
            }
        } else if (lockState > 0) {
            homeUrl = try {
                withTimeout(SEARCH_TIMEOUT_HOME_MS) {
                    SetuSearchEngine.search(context, searchCtx, r18Mode)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: TimeoutCancellationException) {
                Log.e(TAG, "Setu search (lock-only) timed out")
                ""
            }
            coroutineContext.ensureActive()
            if (homeUrl.isEmpty()) {
                Log.e(TAG, "Setu search (lock-only) returned no URL")
                return@withContext WallpaperJobOutcome.searchFailed()
            }
        } else {
            return@withContext WallpaperJobOutcome(ok = true)
        }

        var lockUrl = ""
        var lockSearchFailed = false
        if (WallpaperPipeline.needsDualSearch(isUrgent, homeState, lockState)) {
            val resolved = resolveIndependentLockUrl(context, searchCtx, r18Mode, homeUrl)
            lockUrl = resolved.first
            lockSearchFailed = resolved.second
        }

        val homeBitmap = downloadBitmap(homeUrl)
            ?: return@withContext WallpaperJobOutcome.downloadFailed()

        val processedImage = ImageProcessor.centerCrop(context, homeBitmap, recycleSource = true)
        val processedHome =
            if (homeRequired) {
                processedImage
            } else {
                processedImage // placeholder for lock-only sync; not saved to HOME
            }
        var lockBitmapForApply: Bitmap? = null
        var lockDownloadFailed = false
        if (WallpaperPipeline.needsDualSearch(isUrgent, homeState, lockState)) {
            if (homeRequired) {
                if (lockUrl.isNotEmpty()) {
                    val lockBmp = downloadBitmap(lockUrl)
                    if (lockBmp != null) {
                        lockBitmapForApply = ImageProcessor.centerCrop(context, lockBmp, recycleSource = true)
                    } else {
                        lockDownloadFailed = true
                    }
                }
            } else {
                lockBitmapForApply = processedImage
            }
        }

        suspend fun persistAndApply(): WallpaperJobOutcome {
            if (isUrgent) {
                WallpaperHistory.backup(context)
            }

            if (!isUrgent) {
                if (homeRequired) {
                    WallpaperFiles.saveBitmapSafely(context, processedHome, targetFilename)
                } else if (lockState > 0) {
                    WallpaperFiles.saveBitmapSafely(context, processedHome, WallpaperFiles.LOCK)
                }
                val fingerprint =
                    WallpaperPrefs.prefetchSnapshotFingerprint(
                        styleValue = styleValue,
                        r18Mode = r18Mode,
                        homeState = homeState,
                        lockState = lockState,
                        strictTags = strictTags,
                        softTags = softTags,
                    )
                WallpaperPrefs.saveBufferSourceUrl(prefs, bufferSlot, homeUrl, fingerprint)
                WallpaperPrefs.appendRecentFetchedUrl(prefs, homeUrl)
                return WallpaperJobOutcome(ok = true)
            }

            if (dual && lockBitmapForApply == null) {
                Log.e(TAG, "Dual mode required but no lock bitmap; aborting without saving files")
                return WallpaperJobOutcome(
                    ok = false,
                    applyResult = WallpaperApplyResult(homeOk = false, lockOk = false),
                    lockSearchFailed = lockSearchFailed,
                    lockDownloadFailed = lockDownloadFailed,
                )
            }

            val applyResult = WallpaperApplier.applyForStates(
                context = context,
                homeBitmap = processedHome,
                homeState = homeState,
                lockState = lockState,
                lockBitmap = lockBitmapForApply,
            )

            val ok = WallpaperPipeline.evaluateJobOk(
                homeState = homeState,
                lockState = lockState,
                homeRequired = homeRequired,
                applyResult = applyResult,
                lockSearchFailed = lockSearchFailed,
                lockDownloadFailed = lockDownloadFailed,
            )

            fun persistHomeFiles() {
                if (homeRequired) {
                    WallpaperFiles.saveBitmapSafely(context, processedHome, targetFilename)
                    WallpaperPrefs.saveHomeSourceUrl(prefs, homeUrl)
                    WallpaperPrefs.appendRecentFetchedUrl(prefs, homeUrl)
                } else if (lockState > 0) {
                    WallpaperFiles.saveBitmapSafely(context, processedHome, WallpaperFiles.LOCK)
                }
            }

            fun persistLockFiles() {
                if (sync && lockState > 0) {
                    WallpaperFiles.saveBitmapSafely(context, processedHome, WallpaperFiles.LOCK)
                }
                lockBitmapForApply?.let { processedLock ->
                    WallpaperFiles.saveBitmapSafely(context, processedLock, WallpaperFiles.LOCK)
                }
                if (lockUrl.isNotEmpty()) {
                    WallpaperPrefs.appendRecentFetchedUrl(prefs, lockUrl)
                }
            }

            if (!ok) {
                Log.e(TAG, "Wallpaper apply incomplete home=${applyResult.homeOk} lock=${applyResult.lockOk}")
                if (applyResult.homeOk) {
                    persistHomeFiles()
                    WallpaperPrefs.markSystemSyncedIfComplete(context, homeState, lockState, applyResult)
                }
                return WallpaperJobOutcome(
                    ok = false,
                    applyResult = applyResult,
                    lockSearchFailed = lockSearchFailed,
                    lockDownloadFailed = lockDownloadFailed,
                )
            }

            persistHomeFiles()
            persistLockFiles()

            WallpaperPrefs.markSystemSyncedIfComplete(context, homeState, lockState, applyResult)
            WallpaperPrefs.clearDualComplementCooldown(prefs)

            return WallpaperJobOutcome(
                ok = true,
                applyResult = applyResult,
                lockSearchFailed = lockSearchFailed,
                lockDownloadFailed = lockDownloadFailed,
            )
        }

        try {
            return@withContext WallpaperWriteGuard.withWriteLock { persistAndApply() }
        } finally {
            processedHome.takeIf { !it.isRecycled }?.recycle()
            lockBitmapForApply
                ?.takeIf { it !== processedHome && !it.isRecycled }
                ?.recycle()
        }
    }

    /**
     * 粉蓝/蓝粉且主屏文件已存在：只搜索/下载锁屏图，保留当前主屏，并写入系统。
     */
    suspend fun runLockComplement(
        context: Context,
        styleValue: Int,
        strictTags: Array<String>,
        softTags: Array<String>,
        homeState: Int,
        lockState: Int,
        r18Mode: Int,
        downloadBitmap: suspend (String) -> Bitmap?,
    ): WallpaperJobOutcome = withContext(Dispatchers.IO) {
        if (!WallpaperTargetMode.isDualMode(homeState, lockState)) {
            return@withContext WallpaperJobOutcome(ok = true)
        }
        if (!WallpaperFiles.hasHomeWallpaper(context)) {
            return@withContext WallpaperJobOutcome.searchFailed()
        }

        val prefs = WallpaperPrefs.prefs(context)
        val homeFile = WallpaperFiles.homeFile(context)
        val homeBitmap =
            BitmapFactory.decodeFile(homeFile.absolutePath)?.let {
                ImageProcessor.downscaleIfNeeded(it, ImageProcessor.maxDownloadDimension(context))
            } ?: return@withContext WallpaperJobOutcome.downloadFailed()

        val homeUrl = WallpaperPrefs.readHomeSourceUrl(prefs)
        if (homeUrl.isBlank()) {
            Log.w(TAG, "Complement: missing HOME_SOURCE_URL, lock dedupe may be weaker")
        }
        val searchCtx =
            SetuSearchEngine.Context(
                styleValue = styleValue,
                strictTags = strictTags,
                softTags = softTags,
                recentUrls = WallpaperPrefs.readRecentFetchedUrls(prefs),
            )
        val (lockUrl, lockSearchFailed) = resolveIndependentLockUrl(context, searchCtx, r18Mode, homeUrl)
        if (lockSearchFailed || lockUrl.isEmpty()) {
            homeBitmap.takeIf { !it.isRecycled }?.recycle()
            return@withContext WallpaperJobOutcome(
                ok = false,
                applyResult = WallpaperApplyResult(homeOk = false, lockOk = false),
                lockSearchFailed = true,
            )
        }

        val lockRaw = downloadBitmap(lockUrl)
        if (lockRaw == null) {
            homeBitmap.takeIf { !it.isRecycled }?.recycle()
            return@withContext WallpaperJobOutcome(
                ok = false,
                applyResult = WallpaperApplyResult(homeOk = false, lockOk = false),
                lockDownloadFailed = true,
            )
        }

        val lockBitmap = ImageProcessor.centerCrop(context, lockRaw, recycleSource = true)
        try {
            return@withContext WallpaperWriteGuard.withWriteLock {
                WallpaperHistory.backup(context)

                val applyResult =
                    WallpaperApplier.applyForStates(
                        context = context,
                        homeBitmap = homeBitmap,
                        homeState = homeState,
                        lockState = lockState,
                        lockBitmap = lockBitmap,
                    )
                val ok =
                    WallpaperPipeline.evaluateJobOk(
                        homeState = homeState,
                        lockState = lockState,
                        homeRequired = WallpaperTargetMode.homeRequired(homeState),
                        applyResult = applyResult,
                        lockSearchFailed = false,
                        lockDownloadFailed = false,
                    )
                if (!ok) {
                    return@withWriteLock WallpaperJobOutcome(ok = false, applyResult = applyResult)
                }

                WallpaperFiles.saveBitmapSafely(context, lockBitmap, WallpaperFiles.LOCK)
                WallpaperPrefs.appendRecentFetchedUrl(prefs, lockUrl)
                WallpaperPrefs.markSystemSyncedIfComplete(context, homeState, lockState, applyResult)
                WallpaperPrefs.clearDualComplementCooldown(prefs)
                WallpaperJobOutcome(ok = true, applyResult = applyResult)
            }
        } finally {
            homeBitmap.takeIf { !it.isRecycled }?.recycle()
            lockBitmap.takeIf { !it.isRecycled }?.recycle()
        }
    }

    private suspend fun resolveIndependentLockUrl(
        context: Context,
        searchCtx: SetuSearchEngine.Context,
        r18Mode: Int,
        homeUrl: String,
    ): Pair<String, Boolean> {
        val lockCtx = searchCtx.copy(recentUrls = searchCtx.recentUrls + homeUrl)
        var lockUrl = try {
            withTimeout(SEARCH_TIMEOUT_LOCK_MS) {
                SetuSearchEngine.search(context, lockCtx, r18Mode)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: TimeoutCancellationException) {
            Log.e(TAG, "Setu search (lock) timed out")
            ""
        }

        if (lockUrl.isEmpty() || WallpaperImageIdentity.isSameImage(lockUrl, homeUrl)) {
            lockUrl = try {
                withTimeout(SEARCH_TIMEOUT_LOCK_MS) {
                    SetuSearchEngine.searchDistinct(context, lockCtx, r18Mode, homeUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: TimeoutCancellationException) {
                Log.e(TAG, "Setu searchDistinct timed out")
                ""
            }
        }

        if (lockUrl.isEmpty() || WallpaperImageIdentity.isSameImage(lockUrl, homeUrl)) {
            Log.w(TAG, "Independent lock: no distinct URL (home=$homeUrl lock=$lockUrl)")
            return "" to true
        }
        return lockUrl to false
    }
}

/** Worker 一次壁纸任务的完整结果。 */
data class WallpaperJobOutcome(
    val ok: Boolean,
    val applyResult: WallpaperApplyResult = WallpaperApplyResult(),
    val lockSearchFailed: Boolean = false,
    val lockDownloadFailed: Boolean = false,
) {
    fun isPreApplyFailure(): Boolean = !ok && !applyResult.homeOk

    fun toWorkData(): Data =
        Data.Builder()
            .putBoolean(KEY_OK, ok)
            .putBoolean(KEY_HOME_OK, applyResult.homeOk)
            .putBoolean(KEY_LOCK_OK, applyResult.lockOk)
            .putBoolean(KEY_LOCK_SEARCH_FAILED, lockSearchFailed)
            .putBoolean(KEY_LOCK_DOWNLOAD_FAILED, lockDownloadFailed)
            .build()

    companion object {
        const val KEY_OK = "OUTCOME_OK"
        const val KEY_HOME_OK = "HOME_OK"
        const val KEY_LOCK_OK = "LOCK_OK"
        const val KEY_LOCK_SEARCH_FAILED = "LOCK_SEARCH_FAILED"
        const val KEY_LOCK_DOWNLOAD_FAILED = "LOCK_DOWNLOAD_FAILED"

        fun searchFailed(): WallpaperJobOutcome =
            WallpaperJobOutcome(
                ok = false,
                applyResult = WallpaperApplyResult(homeOk = false, lockOk = false),
            )

        fun downloadFailed(): WallpaperJobOutcome =
            WallpaperJobOutcome(
                ok = false,
                applyResult = WallpaperApplyResult(homeOk = false, lockOk = false),
            )

        fun fromWorkData(data: Data): WallpaperJobOutcome {
            val ok = data.getBoolean(KEY_OK, false)
            val defaultHomeOk = if (ok) true else false
            return WallpaperJobOutcome(
                ok = ok,
                applyResult = WallpaperApplyResult(
                    homeOk = data.getBoolean(KEY_HOME_OK, defaultHomeOk),
                    lockOk = data.getBoolean(KEY_LOCK_OK, defaultHomeOk),
                ),
                lockSearchFailed = data.getBoolean(KEY_LOCK_SEARCH_FAILED, false),
                lockDownloadFailed = data.getBoolean(KEY_LOCK_DOWNLOAD_FAILED, false),
            )
        }
    }
}
