package com.kuroshimira.nyanpasu.work

import android.graphics.Bitmap
import com.kuroshimira.nyanpasu.network.WallpaperImageIdentity
import com.kuroshimira.nyanpasu.wallpaper.WallpaperApplyResult
import com.kuroshimira.nyanpasu.wallpaper.WallpaperTargetMode

/** 壁纸任务不变量与结果判定（单点，避免 Worker / Prefetch / UI 各写一套）。 */
object WallpaperPipeline {

    fun needsDualSearch(isUrgent: Boolean, homeState: Int, lockState: Int): Boolean =
        isUrgent && WallpaperTargetMode.isDualMode(homeState, lockState)

    fun assertDistinctUrls(homeUrl: String, lockUrl: String): Boolean =
        lockUrl.isNotEmpty() && !WallpaperImageIdentity.isSameImage(homeUrl, lockUrl)

    fun canApply(homeState: Int, lockState: Int, lockBitmap: Bitmap?): Boolean {
        if (!WallpaperTargetMode.needsLockBitmap(homeState, lockState)) return true
        return lockBitmap != null
    }

    fun evaluateJobOk(
        homeState: Int,
        lockState: Int,
        homeRequired: Boolean,
        applyResult: WallpaperApplyResult,
        lockSearchFailed: Boolean,
        lockDownloadFailed: Boolean,
    ): Boolean {
        val dual = WallpaperTargetMode.isDualMode(homeState, lockState)
        val sync = WallpaperTargetMode.isSyncMode(homeState, lockState)
        val lockRequired = WallpaperTargetMode.lockRequired(lockState) && (sync || dual)
        return (!homeRequired || applyResult.homeOk) &&
            (!lockRequired || applyResult.lockOk) &&
            !(dual && lockSearchFailed && lockRequired) &&
            !(dual && lockDownloadFailed)
    }
}
