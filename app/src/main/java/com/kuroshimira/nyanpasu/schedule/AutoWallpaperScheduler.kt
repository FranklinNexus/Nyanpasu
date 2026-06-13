package com.kuroshimira.nyanpasu.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kuroshimira.nyanpasu.ui.MainActivity
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import com.kuroshimira.nyanpasu.work.WallpaperWorker
import com.kuroshimira.nyanpasu.work.WallpaperWorkNames
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 调度结果，供 UI 提示用户（如 Daily 回退到 24h WorkManager）。 */
enum class ScheduleResult {
    DAILY_ALARM,
    PERIODIC,
    DAILY_FALLBACK_PERIODIC,
}

/**
 * 自动换壁纸调度：Daily 7:00 用 [AlarmManager.setAlarmClock] 精确触发；
 * 6/12/24 小时间隔用 WorkManager 周期任务。Worker 在 IS_AUTO 时从 SharedPreferences 读最新配置。
 */
object AutoWallpaperScheduler {

    private const val TAG = "AutoWallpaperScheduler"

    const val WORK_PERIODIC = WallpaperWorkNames.AUTO_PERIODIC
    const val ACTION_ALARM = "com.kuroshimira.nyanpasu.ACTION_AUTO_WALLPAPER_ALARM"
    private const val ALARM_REQUEST_CODE = 1001
    private const val SHOW_INTENT_REQUEST_CODE = 1002
    private const val DAILY_HOUR = 7
    private const val DAILY_MINUTE = 0

    private val INTERVAL_HOURS = intArrayOf(-1, 6, 12, 24)

    fun millisUntilNextClock(hourOfDay: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis.coerceAtLeast(now.timeInMillis + TimeUnit.MINUTES.toMillis(1))
    }

    fun scheduleIfEnabled(context: Context): ScheduleResult? {
        val prefs = WallpaperPrefs.prefs(context)
        if (!WallpaperPrefs.isAutoUpdateEnabled(prefs) || !WallpaperPrefs.canApplyWallpaper(prefs)) {
            cancelAll(context)
            return null
        }
        return schedule(context)
    }

    fun schedule(context: Context): ScheduleResult {
        cancelScheduling(context)
        val prefs = WallpaperPrefs.prefs(context)
        val scheduleIndex = WallpaperPrefs.readScheduleIndex(prefs)
        return if (scheduleIndex == 0) {
            if (scheduleDailyAlarm(context)) {
                ScheduleResult.DAILY_ALARM
            } else {
                Log.w(TAG, "Exact alarm unavailable; falling back to 24h WorkManager for daily mode")
                schedulePeriodicWork(context, 3)
                ScheduleResult.DAILY_FALLBACK_PERIODIC
            }
        } else {
            schedulePeriodicWork(context, scheduleIndex)
            ScheduleResult.PERIODIC
        }
    }

    /** 仅取消闹钟与周期任务，不打断进行中的手动 Refresh。 */
    fun cancelScheduling(context: Context) {
        cancelDailyAlarm(context)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
        WallpaperWorkNames.cancelLegacyApplyWorks(context)
    }

    /** 关闭自动更新或清空壁纸目标时：连同 urgent 队列一并取消。 */
    fun cancelAll(context: Context) {
        cancelScheduling(context)
        WorkManager.getInstance(context).cancelUniqueWork(WallpaperWorkNames.APPLY_URGENT)
        WorkManager.getInstance(context).cancelUniqueWork(WallpaperWorkNames.APPLY_AUTO)
        WorkManager.getInstance(context).cancelUniqueWork(WallpaperWorkNames.APPLY_DUAL_COMPLEMENT)
    }

    fun onDailyAlarmFired(context: Context) {
        val prefs = WallpaperPrefs.prefs(context)
        if (!WallpaperPrefs.isAutoUpdateEnabled(prefs) || !WallpaperPrefs.canApplyWallpaper(prefs)) {
            return
        }
        enqueueAutoWallpaperWork(context)
        if (WallpaperPrefs.readScheduleIndex(prefs) == 0) {
            scheduleDailyAlarm(context)
        }
    }

    fun enqueueAutoWallpaperWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    "IS_URGENT" to true,
                    "IS_AUTO" to true,
                ),
            )
            .addTag(WallpaperWorkNames.TAG_AUTO_WALLPAPER)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WallpaperWorkNames.APPLY_AUTO,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun scheduleDailyAlarm(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }
        val triggerAt = millisUntilNextClock(DAILY_HOUR, DAILY_MINUTE)
        val operation = alarmPendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showAppPendingIntent(context)),
                operation,
            )
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
        Log.d(TAG, "Daily alarm scheduled at $triggerAt")
        return true
    }

    private fun cancelDailyAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context))
    }

    private fun schedulePeriodicWork(context: Context, scheduleIndex: Int) {
        val index = WallpaperPrefs.coerceScheduleIndex(scheduleIndex)
        val hours = INTERVAL_HOURS[index].toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    "IS_URGENT" to true,
                    "IS_AUTO" to true,
                ),
            )
            .addTag(WallpaperWorkNames.TAG_AUTO_PERIODIC)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        Log.d(TAG, "Periodic work scheduled every ${hours}h")
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AutoWallpaperAlarmReceiver::class.java).apply {
            action = ACTION_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            SHOW_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
