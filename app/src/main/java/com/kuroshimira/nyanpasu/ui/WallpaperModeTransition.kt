package com.kuroshimira.nyanpasu.ui

import android.content.Context
import com.kuroshimira.nyanpasu.wallpaper.WallpaperFiles
import com.kuroshimira.nyanpasu.wallpaper.WallpaperTargetMode

/** 主屏/锁屏切换后的文件与预取一致性处理。 */
object WallpaperModeTransition {

    enum class DualLockState {
        /** lock 与 home 不同，可继续预览 */
        DistinctLockReady,
        /** 已删除与 home 相同的 lock，需重新下载锁屏图 */
        StaleLockCleared,
        /** 尚无 lock 文件 */
        LockMissing,
    }

    fun onTargetChanged(context: Context, homeState: Int, lockState: Int): DualLockState? {
        if (!WallpaperTargetMode.isDualMode(homeState, lockState)) return null
        val lock = WallpaperFiles.lockFile(context)
        if (!lock.exists() || lock.length() == 0L) {
            return DualLockState.LockMissing
        }
        return if (WallpaperFiles.dropLockIfSameAsHome(context)) {
            DualLockState.StaleLockCleared
        } else {
            DualLockState.DistinctLockReady
        }
    }
}
