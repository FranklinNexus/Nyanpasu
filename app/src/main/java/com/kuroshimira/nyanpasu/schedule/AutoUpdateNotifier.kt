package com.kuroshimira.nyanpasu.schedule

import android.Manifest
import com.kuroshimira.nyanpasu.R
import com.kuroshimira.nyanpasu.ui.MainActivity
import com.kuroshimira.nyanpasu.wallpaper.WallpaperPrefs
import com.kuroshimira.nyanpasu.work.WallpaperJobOutcome
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AutoUpdateNotifier {

    private const val CHANNEL_ID = "wallpaper_auto_update"
    private const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun showSuccess(context: Context) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setContentIntent(openAppPendingIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun showJobResult(context: Context, outcome: WallpaperJobOutcome) {
        when {
            !outcome.ok && !outcome.applyResult.homeOk ->
                showFailure(context)
            outcome.ok && outcome.applyResult.fullySucceeded &&
                !outcome.lockSearchFailed && !outcome.lockDownloadFailed ->
                showSuccess(context)
            outcome.applyResult.homeOk && (outcome.applyResult.lockFailed ||
                outcome.lockSearchFailed || outcome.lockDownloadFailed) ->
                showPartialLockFailure(context)
            else ->
                showFailure(context)
        }
    }

    fun showPartialLockFailure(context: Context) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_lock_partial))
            .setContentIntent(openAppPendingIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun showFailure(context: Context) {
        recordFailure(context)
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.status_error))
            .setContentText(context.getString(R.string.error_download_failed))
            .setContentIntent(openAppPendingIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** 无通知权限时写入 prefs，下次打开 App 再 Toast。 */
    fun recordFailure(context: Context) {
        if (canNotify(context)) return
        WallpaperPrefs.setPendingAutoFailure(WallpaperPrefs.prefs(context), true)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
