package com.cyprienbrisset.fukkatsunop.system

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object FirmwareWatcher {
    private const val PREFS = "firmware_watcher"
    private const val KEY_BASELINE = "baseline_build"
    private const val CHANNEL_ID = "firmware_alert"
    private const val ACTION = "com.cyprienbrisset.fukkatsunop.FIRMWARE_CHECK"
    private const val NOTIF_ID = 9001

    fun currentBuild(): String = Build.DISPLAY

    fun init(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_BASELINE)) {
            prefs.edit().putString(KEY_BASELINE, Build.DISPLAY).apply()
        }
        ensureChannel(ctx)
    }

    fun check(ctx: Context) {
        ensureChannel(ctx)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baseline = prefs.getString(KEY_BASELINE, null) ?: run {
            prefs.edit().putString(KEY_BASELINE, Build.DISPLAY).apply()
            return
        }
        val current = Build.DISPLAY
        if (current != baseline) {
            notify(ctx, current)
            prefs.edit().putString(KEY_BASELINE, current).apply()
        }
    }

    fun scheduleDaily(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + AlarmManager.INTERVAL_DAY,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(ctx),
        )
    }

    private fun pendingIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, NOTIF_ID,
        Intent(ACTION, null, ctx, FirmwareCheckReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Firmware Portal", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun notify(ctx: Context, newBuild: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Mise à jour firmware détectée")
            .setContentText("Portal → $newBuild. Vérifier ADB et overlays.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Nouvelle version détectée : $newBuild\nVérifier que ADB reste actif et que les overlays système fonctionnent."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, notif)
    }
}
