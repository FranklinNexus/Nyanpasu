package com.kuroshimira.nyanpasu.ui

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.databinding.ActivityMainBinding
import com.kuroshimira.nyanpasu.schedule.AutoWallpaperScheduler
import com.kuroshimira.nyanpasu.schedule.ScheduleResult
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs

/**
 * 自动换壁纸开关、调度间隔 UI；从 [MainActivity] 拆出。
 */
class ScheduleUiController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun homeState(): Int
        fun lockState(): Int
        fun onColdStartRefillPrefetch()
        fun onPrefsChangedInvalidatePrefetch()
        fun onRequestAutoUpdatePermissions()
    }

    fun refreshOnColdStart() {
        rescheduleIfEnabled()
        callbacks.onColdStartRefillPrefetch()
    }

    fun refreshAfterPrefsChanged() {
        rescheduleIfEnabled()
        callbacks.onPrefsChangedInvalidatePrefetch()
    }

    fun updateInfoText(index: Int) {
        binding.tvScheduleInfo.text =
            activity.getString(
                R.string.schedule_auto_label,
                WallpaperPrefs.scheduleLabel(activity, index),
            )
    }

    fun showScheduleDialog() {
        val labels =
            Array(WallpaperPrefs.SCHEDULE_COUNT) { WallpaperPrefs.scheduleLabel(activity, it) }
        AlertDialog.Builder(activity)
            .setTitle(R.string.schedule_dialog_title)
            .setItems(labels) { _, which ->
                val index = WallpaperPrefs.coerceScheduleIndex(which)
                WallpaperPrefs.prefs(activity).edit()
                    .putInt(WallpaperPrefs.KEY_SCHEDULE_INDEX, index)
                    .apply()
                updateInfoText(index)
                if (binding.switchDaily.isChecked) {
                    callbacks.onRequestAutoUpdatePermissions()
                    reportResult(AutoWallpaperScheduler.schedule(activity))
                }
            }
            .show()
    }

    fun reportResult(result: ScheduleResult) {
        if (result == ScheduleResult.DAILY_FALLBACK_CHAIN) {
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_alarm_fallback),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** 双目标关闭时自动关每日开关并取消调度。 */
    fun rescheduleIfEnabled() {
        if (!binding.switchDaily.isChecked) return
        if (callbacks.homeState() == 0 && callbacks.lockState() == 0) {
            binding.switchDaily.isChecked = false
            WallpaperPrefs.prefs(activity)
                .edit()
                .putBoolean(WallpaperPrefs.KEY_DAILY_ENABLED, false)
                .apply()
            AutoWallpaperScheduler.cancelScheduling(activity)
            Toast.makeText(
                activity,
                activity.getString(R.string.toast_auto_need_target),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            reportResult(AutoWallpaperScheduler.schedule(activity))
        }
    }

    /** 从设置页返回：只升级 Daily 精确闹钟，不重置已排期的周期/链。 */
    fun reconcileOnResume() {
        if (!binding.switchDaily.isChecked) return
        if (callbacks.homeState() == 0 && callbacks.lockState() == 0) {
            rescheduleIfEnabled()
            return
        }
        AutoWallpaperScheduler.reconcileOnResume(activity)?.let { reportResult(it) }
    }
}
