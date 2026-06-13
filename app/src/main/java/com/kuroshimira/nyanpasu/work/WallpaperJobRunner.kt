package com.kuroshimira.nyanpasu.work

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.work.Data
import com.kuroshimira.nyanpasu.search.SetuSearchEngine
import com.kuroshimira.nyanpasu.wallpaper.ImageProcessor
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplier
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult
import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import com.kuroshimira.nyanpasu.wallpaper.WallpaperHistory
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
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
        val prefs = WallpaperPrefs.prefs(context)
        val homeRequired = homeState > 0

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
        if (needsIndependentLockWallpaper(isUrgent, homeRequired, sync, lockState)) {
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
        if (needsIndependentLockWallpaper(isUrgent, homeRequired, sync, lockState)) {
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

            if (homeRequired) {
                WallpaperFiles.saveBitmapSafely(context, processedHome, targetFilename)
            } else if (lockState > 0) {
                WallpaperFiles.saveBitmapSafely(context, processedHome, WallpaperFiles.LOCK)
            }

            if (isUrgent) {
                WallpaperPrefs.appendRecentFetchedUrl(prefs, homeUrl)
                if (lockUrl.isNotEmpty()) {
                    WallpaperPrefs.appendRecentFetchedUrl(prefs, lockUrl)
                }
            }

            if (!isUrgent) {
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

            if (!sync && lockState == 2 && lockBitmapForApply == null) {
                Log.e(TAG, "Independent lock required but no lock bitmap; aborting apply")
                return WallpaperJobOutcome(
                    ok = false,
                    applyResult = WallpaperApplyResult(homeOk = false, lockOk = false),
                    lockSearchFailed = lockSearchFailed,
                    lockDownloadFailed = lockDownloadFailed,
                )
            }

            if (sync && lockState > 0) {
                WallpaperFiles.saveBitmapSafely(context, processedHome, WallpaperFiles.LOCK)
            }

            lockBitmapForApply?.let { processedLock ->
                WallpaperFiles.saveBitmapSafely(context, processedLock, WallpaperFiles.LOCK)
            }

            val applyResult = WallpaperApplier.applyForStates(
                context = context,
                homeBitmap = processedHome,
                homeState = homeState,
                lockState = lockState,
                lockBitmap = lockBitmapForApply,
            )

            val lockRequired = lockState > 0 && (sync || lockState == 2)
            val ok = (!homeRequired || applyResult.homeOk) &&
                (!lockRequired || applyResult.lockOk) &&
                !(lockState == 2 && !sync && lockSearchFailed && lockRequired) &&
                !(lockState == 2 && !sync && lockDownloadFailed)

            if (!ok) {
                Log.e(TAG, "Wallpaper apply incomplete home=${applyResult.homeOk} lock=${applyResult.lockOk}")
            }

            return WallpaperJobOutcome(
                ok = ok,
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

    private fun needsIndependentLockWallpaper(
        isUrgent: Boolean,
        homeRequired: Boolean,
        sync: Boolean,
        lockState: Int,
    ): Boolean = isUrgent && !sync && lockState == 2 && (homeRequired || lockState > 0)

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

        if (lockUrl.isEmpty() || lockUrl == homeUrl) {
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

        if (lockUrl.isEmpty() || lockUrl == homeUrl) {
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
