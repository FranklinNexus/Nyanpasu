package com.kuroshimira.nyanpasu.work

import android.graphics.BitmapFactory
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.wallpaper.ImageProcessor
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplier
import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import com.kuroshimira.nyanpasu.wallpaper.WallpaperWriteGuard

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 双缓冲预取：文件 I/O、迁移、空槽补队；通过 [Callbacks] 与 Activity / WorkManager 解耦。
 */
class PrefetchCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun enqueuePrefetch(slot: String)
        fun enqueueUrgentDownload()
        fun reloadPreview()
        fun onWallpaperApplyFailed(message: String)
    }

    private var lastRefillAtMs = 0L

    fun migrateIfNeeded() {
        WallpaperFiles.migrateLegacyBuffer(context)
        purgeStalePrefetchSlots()
    }

    /** Tag/口味变更后作废旧预取图，避免 Instant Load 仍用旧偏好。 */
    fun invalidatePrefetchSlots() {
        WallpaperFiles.clearPrefetchBuffers(context)
        WallpaperPrefs.clearBufferSourceUrls(WallpaperPrefs.prefs(context))
        listOf("a", "b").forEach { slot ->
            WorkManager.getInstance(context).cancelUniqueWork(WallpaperWorkNames.prefetchWorkName(slot))
        }
    }

    fun hasAppliedWallpaper(): Boolean {
        val prefs = WallpaperPrefs.prefs(context)
        val (homeState, lockState) = WallpaperPrefs.readHomeLockState(prefs)
        if (homeState > 0 && WallpaperFiles.hasHomeWallpaper(context)) return true
        if (lockState > 0) {
            val lock = WallpaperFiles.lockFile(context)
            return lock.exists() && lock.length() > 0L
        }
        return false
    }

    /** 预取完成 / 冷启动：尝试 buffer 或 lock-only 预取，必要时 urgent 下载。 */
    private suspend fun applyPrefetchIfNeeded(fallbackToUrgent: Boolean) {
        if (hasAppliedWallpaper()) return
        val prefs = WallpaperPrefs.prefs(context)
        if (needsUrgentForIndependentLock(prefs)) {
            callbacks.enqueueUrgentDownload()
            return
        }
        val ready = matchingPrefetchFile()
        if (ready != null) {
            if (applyBufferToHome(ready)) refillEmptySlots(force = true)
            return
        }
        if (applyLockPrefetchIfReady()) {
            refillEmptySlots(force = true)
        } else if (fallbackToUrgent) {
            callbacks.enqueueUrgentDownload()
        }
    }

    fun maybeApplyToPreview() {
        scope.launch { applyPrefetchIfNeeded(fallbackToUrgent = false) }
    }

    fun ensureInitialWallpaper() {
        scope.launch { applyPrefetchIfNeeded(fallbackToUrgent = true) }
    }

    /** 独立锁屏（蓝灯）需双图，预取槽只有一张 → 走 urgent 下载。 */
    fun requiresDualWallpaperDownload(): Boolean =
        needsUrgentForIndependentLock(WallpaperPrefs.prefs(context))

    private fun needsUrgentForIndependentLock(prefs: SharedPreferences): Boolean {
        val (homeState, lockState) = WallpaperPrefs.readHomeLockState(prefs)
        return lockState == 2 && !WallpaperApplier.isSyncMode(homeState, lockState)
    }

    /** @param force true 时跳过 debounce（如用户手动 Refresh 成功后补槽） */
    fun refillEmptySlots(force: Boolean = false) {
        if (!force) {
            val now = System.currentTimeMillis()
            synchronized(refillLock) {
                if (now - lastRefillAtMs < REFILL_DEBOUNCE_MS) return
                lastRefillAtMs = now
            }
        } else {
            synchronized(refillLock) { lastRefillAtMs = System.currentTimeMillis() }
        }
        if (!WallpaperPrefs.canApplyWallpaper(WallpaperPrefs.prefs(context))) return
        migrateIfNeeded()
        purgeStalePrefetchSlots()
        for (slot in listOf("a", "b")) {
            val f = WallpaperFiles.bufferFile(context, slot)
            if (!f.exists() || f.length() == 0L) {
                callbacks.enqueuePrefetch(slot)
            }
        }
    }

    /** 清除指纹不匹配或无指纹的旧预取文件，避免 refill 误判槽位已满。 */
    private fun purgeStalePrefetchSlots() {
        val prefs = WallpaperPrefs.prefs(context)
        val expected = WallpaperPrefs.prefetchSnapshotFingerprint(prefs)
        for (slot in listOf("a", "b")) {
            val f = WallpaperFiles.bufferFile(context, slot)
            if (!f.exists() || f.length() == 0L) continue
            val slotFp = WallpaperPrefs.readBufferFingerprint(prefs, slot)
            if (slotFp == null || slotFp != expected) {
                Log.d(TAG, "Purging stale prefetch slot=$slot fp=$slotFp expected=$expected")
                f.delete()
                WallpaperPrefs.clearBufferSourceUrl(prefs, slot)
            }
        }
    }

    /** @return 是否成功 promote 并 apply 到系统壁纸 */
    suspend fun applyBufferToHome(prefetch: File): Boolean {
        if (!prefetch.exists() || prefetch.length() == 0L) return false
        if (needsUrgentForIndependentLock(WallpaperPrefs.prefs(context))) {
            callbacks.enqueueUrgentDownload()
            return false
        }
        var needsRefill = false
        val applied = WallpaperWriteGuard.withWriteLock {
            if (!prefetch.exists() || prefetch.length() == 0L) return@withWriteLock false

            val promoted = withContext(Dispatchers.IO) {
                WallpaperFiles.promotePrefetchToHome(context, prefetch)
            }
            val slot = prefetchSlot(prefetch)
            if (!promoted) {
                Log.e(TAG, "Prefetch promote failed: ${prefetch.name}")
                callbacks.onWallpaperApplyFailed(
                    context.getString(R.string.error_download_failed),
                )
                WallpaperPrefs.clearBufferSourceUrl(WallpaperPrefs.prefs(context), slot)
                needsRefill = true
                return@withWriteLock false
            }

            val prefs = WallpaperPrefs.prefs(context)
            WallpaperPrefs.readBufferSourceUrl(prefs, slot)?.let { sourceUrl ->
                WallpaperPrefs.appendRecentFetchedUrl(prefs, sourceUrl)
            }
            WallpaperPrefs.clearBufferSourceUrl(prefs, slot)

            val homeFile = WallpaperFiles.homeFile(context)
            val bitmap = withContext(Dispatchers.IO) {
                val raw = BitmapFactory.decodeFile(homeFile.absolutePath) ?: return@withContext null
                ImageProcessor.downscaleIfNeeded(raw, ImageProcessor.maxDownloadDimension(context))
            }
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap after prefetch promote")
                callbacks.onWallpaperApplyFailed(
                    context.getString(R.string.error_download_failed),
                )
                needsRefill = true
                return@withWriteLock false
            }

            val homeState = prefs.getInt(WallpaperPrefs.KEY_HOME_STATE, 1)
            val lockState = prefs.getInt(WallpaperPrefs.KEY_LOCK_STATE, 0)
            val sync = WallpaperApplier.isSyncMode(homeState, lockState)

            try {
                if (sync && lockState > 0) {
                    withContext(Dispatchers.IO) {
                        homeFile.copyTo(WallpaperFiles.lockFile(context), overwrite = true)
                    }
                }
                val result = WallpaperApplier.applyForStates(
                    context = context,
                    homeBitmap = bitmap,
                    homeState = homeState,
                    lockState = lockState,
                )
                if (result.fullySucceeded) {
                    Log.d(TAG, "Wallpaper applied from prefetch buffer")
                    callbacks.reloadPreview()
                    true
                } else {
                    callbacks.onWallpaperApplyFailed(
                        when {
                            result.homeFailed && result.lockFailed ->
                                context.getString(R.string.error_apply_both_failed)
                            result.lockFailed ->
                                context.getString(R.string.error_apply_lock_failed)
                            else ->
                                context.getString(R.string.error_apply_generic)
                        },
                    )
                    callbacks.reloadPreview()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Prefetch apply failed", e)
                callbacks.onWallpaperApplyFailed(
                    context.getString(R.string.error_wallpaper_sync_failed),
                )
                false
            } finally {
                bitmap.takeIf { !it.isRecycled }?.recycle()
            }
        }
        if (needsRefill) refillEmptySlots(force = true)
        return applied
    }

    private fun matchingPrefetchFile(): File? {
        val prefs = WallpaperPrefs.prefs(context)
        val fingerprint = WallpaperPrefs.prefetchSnapshotFingerprint(prefs)
        return WallpaperFiles.firstReadyPrefetch(context, fingerprint)
    }

    /** 仅锁屏模式：预取 Worker 直接写入 lock 文件，此处 apply 到系统。 */
    private suspend fun applyLockPrefetchIfReady(): Boolean {
        val prefs = WallpaperPrefs.prefs(context)
        val (homeState, lockState) = WallpaperPrefs.readHomeLockState(prefs)
        if (homeState > 0 || lockState == 0) return false
        val lockFile = WallpaperFiles.lockFile(context)
        if (!lockFile.exists() || lockFile.length() == 0L) return false

        return WallpaperWriteGuard.withWriteLock {
            if (!lockFile.exists() || lockFile.length() == 0L) return@withWriteLock false
            val bitmap = withContext(Dispatchers.IO) {
                val raw = BitmapFactory.decodeFile(lockFile.absolutePath) ?: return@withContext null
                ImageProcessor.downscaleIfNeeded(raw, ImageProcessor.maxDownloadDimension(context))
            } ?: run {
                Log.e(TAG, "Failed to decode lock prefetch")
                callbacks.onWallpaperApplyFailed(context.getString(R.string.error_download_failed))
                return@withWriteLock false
            }
            try {
                val result = WallpaperApplier.applyForStates(
                    context = context,
                    homeBitmap = bitmap,
                    homeState = 0,
                    lockState = lockState,
                )
                if (result.fullySucceeded) {
                    Log.d(TAG, "Lock-only wallpaper applied from prefetch")
                    callbacks.reloadPreview()
                    true
                } else {
                    callbacks.onWallpaperApplyFailed(
                        context.getString(R.string.error_apply_lock_failed),
                    )
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lock prefetch apply failed", e)
                callbacks.onWallpaperApplyFailed(
                    context.getString(R.string.error_wallpaper_sync_failed),
                )
                false
            } finally {
                bitmap.takeIf { !it.isRecycled }?.recycle()
            }
        }
    }

    private fun prefetchSlot(prefetch: File): String =
        if (prefetch.name.contains(WallpaperFiles.BUFFER_B)) "b" else "a"

    companion object {
        private const val TAG = "PrefetchCoordinator"
        private const val REFILL_DEBOUNCE_MS = 30_000L
        private val refillLock = Any()
    }
}
