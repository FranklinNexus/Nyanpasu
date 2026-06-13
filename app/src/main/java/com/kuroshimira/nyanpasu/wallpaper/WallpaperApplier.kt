package com.kuroshimira.nyanpasu.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 主屏 / 锁屏壁纸写入结果，供 Worker 与 UI 共用。 */
data class WallpaperApplyResult(
    val homeOk: Boolean = true,
    val lockOk: Boolean = true,
) {
    val fullySucceeded: Boolean get() = homeOk && lockOk
    val homeFailed: Boolean get() = !homeOk
    val lockFailed: Boolean get() = !lockOk
}

object WallpaperApplier {

    private const val TAG = "WallpaperApplier"

    fun isSyncMode(homeState: Int, lockState: Int): Boolean =
        WallpaperTargetMode.isSyncMode(homeState, lockState)

    /** 低层写入；一般应通过 [applyForStates] 调用以走 OEM 策略。 */
    suspend fun setWallpaper(context: Context, bitmap: Bitmap, which: Int): Boolean =
        setWallpaperWithRetry(context, bitmap, which)

    /** 按 Home/Lock 三态写入系统壁纸；独立锁屏可传入 [lockBitmap]。 */
    suspend fun applyForStates(
        context: Context,
        homeBitmap: Bitmap,
        homeState: Int,
        lockState: Int,
        lockBitmap: Bitmap? = null,
    ): WallpaperApplyResult {
        val sync = isSyncMode(homeState, lockState)
        val dual = WallpaperTargetMode.isDualMode(homeState, lockState)

        if (dual && lockBitmap == null) {
            Log.w(TAG, "Skip apply: dual mode but no lock bitmap")
            return WallpaperApplyResult(homeOk = false, lockOk = false)
        }

        var homeOk = true
        var lockOk = true

        when {
            sync && homeState > 0 && lockState > 0 -> {
                val (h, l) = applySyncWallpaper(context, homeBitmap)
                homeOk = h
                lockOk = l
            }
            homeState > 0 -> {
                homeOk = setWallpaperWithRetry(context, homeBitmap, WallpaperManager.FLAG_SYSTEM)
            }
            sync && lockState > 0 -> {
                lockOk = setWallpaperWithRetry(context, homeBitmap, WallpaperManager.FLAG_LOCK)
            }
        }

        if (dual && lockBitmap != null) {
            when {
                homeState > 0 -> {
                    if (!homeOk) {
                        lockOk = false
                    } else {
                        lockOk = applyLockAfterHome(context, lockBitmap)
                    }
                }
                else -> {
                    lockOk = setWallpaperWithRetry(context, lockBitmap, WallpaperManager.FLAG_LOCK)
                    homeOk = lockOk
                }
            }
        }

        Log.d(
            TAG,
            "applyForStates home=$homeState lock=$lockState sync=$sync " +
                "oem=${WallpaperOemCompat.syncApplyOrder()} homeOk=$homeOk lockOk=$lockOk",
        )
        return WallpaperApplyResult(homeOk = homeOk, lockOk = lockOk)
    }

    /** 将已保存的主/锁屏文件写入系统壁纸（预览与系统不同步时补救）。 */
    suspend fun applyFromStoredFiles(
        context: Context,
        homeState: Int,
        lockState: Int,
    ): WallpaperApplyResult {
        val dual = WallpaperTargetMode.isDualMode(homeState, lockState)
        val sync = isSyncMode(homeState, lockState)
        val homeFile = WallpaperFiles.homeFile(context)
        val lockFile = WallpaperFiles.lockFile(context)

        var homeBitmap: Bitmap? = null
        var lockBitmap: Bitmap? = null
        return try {
            when {
                dual -> {
                    if (!homeFile.exists() || !lockFile.exists()) {
                        return WallpaperApplyResult(homeOk = false, lockOk = false)
                    }
                    homeBitmap = decodeStored(context, homeFile) ?: return WallpaperApplyResult(homeOk = false, lockOk = false)
                    lockBitmap = decodeStored(context, lockFile) ?: return WallpaperApplyResult(homeOk = false, lockOk = false)
                    applyForStates(context, homeBitmap, homeState, lockState, lockBitmap)
                }
                homeState > 0 && lockState > 0 && sync -> {
                    if (!homeFile.exists()) return WallpaperApplyResult(homeOk = false, lockOk = false)
                    homeBitmap = decodeStored(context, homeFile) ?: return WallpaperApplyResult(homeOk = false, lockOk = false)
                    applyForStates(context, homeBitmap, homeState, lockState)
                }
                homeState > 0 -> {
                    if (!homeFile.exists()) return WallpaperApplyResult(homeOk = false, lockOk = false)
                    homeBitmap = decodeStored(context, homeFile) ?: return WallpaperApplyResult(homeOk = false, lockOk = false)
                    applyForStates(context, homeBitmap, homeState, lockState)
                }
                lockState > 0 -> {
                    if (!lockFile.exists()) return WallpaperApplyResult(homeOk = false, lockOk = false)
                    lockBitmap = decodeStored(context, lockFile) ?: return WallpaperApplyResult(homeOk = false, lockOk = false)
                    applyForStates(context, lockBitmap, homeState, lockState)
                }
                else -> WallpaperApplyResult(homeOk = true, lockOk = true)
            }
        } finally {
            homeBitmap?.takeIf { !it.isRecycled }?.recycle()
            lockBitmap
                ?.takeIf { it !== homeBitmap && !it.isRecycled }
                ?.recycle()
        }
    }

    private fun decodeStored(context: Context, file: java.io.File): Bitmap? {
        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return ImageProcessor.downscaleIfNeeded(raw, ImageProcessor.maxDownloadDimension(context))
    }

    private suspend fun applySyncWallpaper(
        context: Context,
        bitmap: Bitmap,
    ): Pair<Boolean, Boolean> {
        val stepDelay = WallpaperOemCompat.stepDelayMs()
        return when (WallpaperOemCompat.syncApplyOrder()) {
            WallpaperOemCompat.SyncApplyOrder.LOCK_HOME_LOCK -> {
                var lockOk = setWallpaperWithRetry(context, bitmap, WallpaperManager.FLAG_LOCK)
                if (stepDelay > 0) delay(stepDelay)
                var homeOk = setWallpaperWithRetry(context, bitmap, WallpaperManager.FLAG_SYSTEM)
                if (stepDelay > 0) delay(stepDelay)
                lockOk = reassertLock(context, bitmap, lockOk)
                homeOk to lockOk
            }
            WallpaperOemCompat.SyncApplyOrder.HOME_LOCK_LOCK -> {
                var homeOk = setWallpaperWithRetry(context, bitmap, WallpaperManager.FLAG_SYSTEM)
                if (!homeOk) return false to false
                if (stepDelay > 0) delay(stepDelay)
                var lockOk = setWallpaperWithRetry(context, bitmap, WallpaperManager.FLAG_LOCK)
                if (WallpaperOemCompat.shouldReassertLockAfterHome()) {
                    if (stepDelay > 0) delay(stepDelay)
                    lockOk = reassertLock(context, bitmap, lockOk)
                }
                homeOk to lockOk
            }
        }
    }

    private suspend fun applyLockAfterHome(context: Context, lockBitmap: Bitmap): Boolean {
        val stepDelay = WallpaperOemCompat.stepDelayMs()
        if (stepDelay > 0) delay(stepDelay)
        var lockOk = setWallpaperWithRetry(context, lockBitmap, WallpaperManager.FLAG_LOCK)
        if (WallpaperOemCompat.shouldReassertLockAfterHome()) {
            if (stepDelay > 0) delay(stepDelay)
            lockOk = reassertLock(context, lockBitmap, lockOk)
        }
        return lockOk
    }

    private suspend fun reassertLock(
        context: Context,
        bitmap: Bitmap,
        previousOk: Boolean,
    ): Boolean {
        val again = setWallpaperWithRetry(context, bitmap, WallpaperManager.FLAG_LOCK)
        return again || previousOk
    }

    private suspend fun setWallpaperWithRetry(
        context: Context,
        bitmap: Bitmap,
        which: Int,
    ): Boolean {
        val isLock = which and WallpaperManager.FLAG_LOCK != 0
        val attempts = WallpaperOemCompat.retryCount()
        val retryDelay = WallpaperOemCompat.retryDelayMs()
        val allowBackupVariants =
            when {
                isLock && WallpaperOemCompat.preferNoBackupForLock() ->
                    listOf(false, true)
                else ->
                    listOf(true, false)
            }

        repeat(attempts) { attempt ->
            for (allowBackup in allowBackupVariants) {
                val copy = copyForWallpaperApply(bitmap) ?: continue
                val ok = trySetBitmap(context, copy, which, allowBackup)
                if (copy !== bitmap && !copy.isRecycled) copy.recycle()
                if (ok) return true
            }
            if (attempt < attempts - 1 && retryDelay > 0) {
                delay(retryDelay)
            }
        }
        Log.e(TAG, "setBitmap exhausted retries which=$which")
        return false
    }

    private suspend fun trySetBitmap(
        context: Context,
        bitmap: Bitmap,
        which: Int,
        allowBackup: Boolean,
    ): Boolean {
        val safe = ImageProcessor.forWallpaperManager(bitmap)
        val ownsSafe = safe !== bitmap
        return try {
            withContext(Dispatchers.Main) {
                WallpaperManager.getInstance(context.applicationContext)
                    .setBitmap(safe, null, allowBackup, which)
            }
            verifyApplied(context, which)
        } catch (e: Exception) {
            Log.w(TAG, "setBitmap failed which=$which backup=$allowBackup: ${e.message}")
            false
        } finally {
            if (ownsSafe && !safe.isRecycled) safe.recycle()
        }
    }

    /** API 24+ 轻量校验；部分 OEM getWallpaperId 误报 -1，setBitmap 未抛错则视为成功。 */
    private fun verifyApplied(context: Context, which: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        return try {
            val id = WallpaperManager.getInstance(context.applicationContext).getWallpaperId(which)
            if (id == -1) {
                Log.w(TAG, "getWallpaperId=-1 which=$which; assuming apply ok")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "verifyApplied skipped which=$which: ${e.message}")
            true
        }
    }

    private fun copyForWallpaperApply(source: Bitmap): Bitmap? {
        if (source.isRecycled) return null
        return source.copy(Bitmap.Config.ARGB_8888, false)
    }
}
