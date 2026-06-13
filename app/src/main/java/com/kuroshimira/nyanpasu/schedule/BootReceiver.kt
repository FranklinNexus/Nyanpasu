package com.kuroshimira.nyanpasu.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 应用更新后恢复自动换壁纸调度。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                AutoWallpaperScheduler.scheduleIfEnabled(context.applicationContext)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (intent.data?.schemeSpecificPart != context.packageName) return
                AutoWallpaperScheduler.scheduleIfEnabled(context.applicationContext)
            }
        }
    }
}
