package com.kuroshimira.nyanpasu.work

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager

/** WorkManager 唯一任务名与标签：手动 / 闹钟 / 周期 apply 分队列。 */
object WallpaperWorkNames {

    /** 手动 Refresh 专用队列；闹钟自动换壁纸用 [APPLY_AUTO]，避免与 REPLACE 互斥。 */
    const val APPLY_URGENT = "wallpaper_apply_urgent"

    /** Daily 闹钟触发的一次性自动 apply。 */
    const val APPLY_AUTO = "wallpaper_apply_auto"

    const val AUTO_PERIODIC = "AUTO_JOB"

    const val TAG_MANUAL_REFRESH = "MANUAL_REFRESH"
    /** 闹钟 / 一次性自动换壁纸任务 */
    const val TAG_AUTO_WALLPAPER = "AUTO_WALLPAPER"
    /** WorkManager 周期自动换壁纸（勿与手动 Refresh 共用 cancelByTag） */
    const val TAG_AUTO_PERIODIC = "AUTO_WALLPAPER_PERIODIC"

    /** 迁移前任务名，冷启动时取消以免孤儿任务写盘。 */
    const val LEGACY_MANUAL = "urgent_wallpaper"
    const val LEGACY_AUTO_ALARM = "auto_wallpaper_run"

    fun prefetchWorkName(slot: String): String = "prefetch_wallpaper_$slot"

    fun cancelLegacyApplyWorks(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(LEGACY_MANUAL)
        wm.cancelUniqueWork(LEGACY_AUTO_ALARM)
    }

    fun isManualApplyActive(infos: List<WorkInfo>): Boolean =
        infos.any {
            it.tags.contains(TAG_MANUAL_REFRESH) &&
                (it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED)
        }

    fun isApplyUrgentActive(infos: List<WorkInfo>): Boolean =
        infos.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }

    fun isPeriodicApplyRunning(infos: List<WorkInfo>): Boolean =
        infos.any { it.state == WorkInfo.State.RUNNING }
}
