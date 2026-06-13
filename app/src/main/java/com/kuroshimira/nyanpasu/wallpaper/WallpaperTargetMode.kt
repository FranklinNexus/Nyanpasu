package com.kuroshimira.nyanpasu.wallpaper

/**
 * 主屏 / 锁屏三态（0=关, 1=粉, 2=蓝）的**唯一**语义来源。
 *
 * - **同色**（粉粉 / 蓝蓝）→ 联动，一张图
 * - **异色**（粉蓝 / 蓝粉）→ 双图独立
 */
object WallpaperTargetMode {

    const val OFF = 0
    const val PINK = 1
    const val BLUE = 2

    fun isEnabled(state: Int): Boolean = state > OFF

    /** 主屏、锁屏均开启且颜色相同 → 同一张壁纸 */
    fun isSyncMode(homeState: Int, lockState: Int): Boolean =
        homeState > OFF && lockState > OFF && homeState == lockState

    /** 主屏、锁屏均开启且颜色不同 → 两张独立壁纸（粉蓝 ≡ 蓝粉） */
    fun isDualMode(homeState: Int, lockState: Int): Boolean =
        homeState > OFF && lockState > OFF && homeState != lockState

    fun needsTwoImages(homeState: Int, lockState: Int): Boolean = isDualMode(homeState, lockState)

    fun homeRequired(homeState: Int): Boolean = homeState > OFF

    fun lockRequired(lockState: Int): Boolean = lockState > OFF

    /** apply / download 时锁屏是否必须有独立 bitmap */
    fun needsLockBitmap(homeState: Int, lockState: Int): Boolean = isDualMode(homeState, lockState)
}
