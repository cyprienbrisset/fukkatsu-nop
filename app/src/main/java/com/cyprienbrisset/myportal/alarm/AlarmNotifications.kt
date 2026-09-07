package com.cyprienbrisset.myportal.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object AlarmNotifications {
    const val CHANNEL_ID = "alarm"
    const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Réveil", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alarmes de Fukkatsu No P"
                setBypassDnd(true)
                setSound(null, null) // sound handled by the foreground service
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Full-screen-intent notification that opens the ring screen. */
    fun buildRinging(context: Context, alarmId: Long, label: String): Notification {
        ensureChannel(context)
        val fullScreen = PendingIntent.getActivity(
            context, alarmId.toInt(),
            Intent(context, AlarmRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (label.isBlank()) "Réveil" else label)
            .setContentText("Appuyez pour ouvrir")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }
}
