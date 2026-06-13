package com.kuroshimira.nyanpasu.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 每日 7:00 闹钟：触发自动换壁纸并预约下一次。 */
class AutoWallpaperAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AutoWallpaperScheduler.ACTION_ALARM) return
        AutoWallpaperScheduler.onDailyAlarmFired(context.applicationContext)
    }
}
