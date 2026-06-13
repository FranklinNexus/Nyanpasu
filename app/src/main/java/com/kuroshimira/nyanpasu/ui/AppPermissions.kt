package com.kuroshimira.nyanpasu.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.schedule.AutoUpdateNotifier
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs

/** 存储 / 通知 / 精确闹钟权限请求与结果处理。 */
object AppPermissions {

    const val REQUEST_STORAGE = 101
    const val REQUEST_NOTIFICATION = 102

    fun requestStorageIfNeeded(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE,
                )
            }
        }
    }

    fun requestAutoUpdateIfNeeded(activity: AppCompatActivity) {
        AutoUpdateNotifier.ensureChannel(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION,
                )
            }
        }
        val scheduleIndex = WallpaperPrefs.readScheduleIndex(WallpaperPrefs.prefs(activity))
        if (scheduleIndex == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_exact_alarm_request),
                    Toast.LENGTH_LONG,
                ).show()
                try {
                    activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } catch (_: Exception) {
                    // 部分机型无此页面
                }
            }
        }
    }

    fun handleResult(activity: AppCompatActivity, requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.toast_storage_granted),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.toast_storage_denied),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            REQUEST_NOTIFICATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.toast_notification_denied),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}
