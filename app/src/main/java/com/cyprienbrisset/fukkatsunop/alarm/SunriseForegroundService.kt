package com.cyprienbrisset.fukkatsunop.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.cyprienbrisset.fukkatsunop.ui.alarm.SunriseActivity

class SunriseForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var step = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must be first on every path (API 26+ contract).
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification(minutesLeft()))
        if (intent?.action == ACTION_STOP) { doStop(); return START_NOT_STICKY }
        step = 0
        acquireWakeLock()
        applyBrightness(MIN_BRIGHTNESS)
        scheduleRamp()
        return START_NOT_STICKY
    }

    private fun scheduleRamp() {
        handler.removeCallbacksAndMessages(null)
        val tick = object : Runnable {
            override fun run() {
                step++
                val frac = (step.toFloat() / RAMP_STEPS).coerceIn(0f, 1f)
                val brt = (MIN_BRIGHTNESS + frac * (MAX_BRIGHTNESS - MIN_BRIGHTNESS)).toInt()
                applyBrightness(brt)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIF_ID, buildNotification(minutesLeft()))
                if (step < RAMP_STEPS) handler.postDelayed(this, RAMP_INTERVAL_MS)
            }
        }
        handler.postDelayed(tick, RAMP_INTERVAL_MS)
    }

    private fun applyBrightness(v: Int) {
        if (!Settings.System.canWrite(this)) return
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceIn(1, 255))
    }

    private fun minutesLeft(): Long =
        ((RAMP_STEPS - step).coerceAtLeast(0) * RAMP_INTERVAL_MS / 60_000L)

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "myportal:sunrise",
        ).also { it.acquire(DURATION_MS + 60_000L) }
    }

    private fun doStop() {
        handler.removeCallbacksAndMessages(null)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        sendBroadcast(Intent(ACTION_FINISH_ACTIVITY))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { doStop(); super.onDestroy() }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Lever de soleil", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(minutesLeft: Long) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("日の出 — Lever de soleil")
            .setContentText(if (minutesLeft > 0) "Réveil dans $minutesLeft min" else "Réveil en approche…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(
                PendingIntent.getActivity(
                    this, NOTIF_ID,
                    Intent(this, SunriseActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .putExtra(SunriseActivity.EXTRA_DURATION_MS, DURATION_MS),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
                false,
            )
            .build()

    companion object {
        const val CHANNEL_ID = "sunrise"
        const val NOTIF_ID = 1002
        const val ACTION_STOP = "com.cyprienbrisset.fukkatsunop.SUNRISE_STOP"
        const val ACTION_FINISH_ACTIVITY = "com.cyprienbrisset.fukkatsunop.SUNRISE_FINISH"
        const val DURATION_MS = 20 * 60 * 1000L   // 20 minutes
        const val RAMP_STEPS = 40                  // tick toutes les 30 s
        const val RAMP_INTERVAL_MS = 30_000L
        const val MIN_BRIGHTNESS = 8               // ~3 %
        const val MAX_BRIGHTNESS = 255             // 100 %

        fun start(context: Context, alarmId: Long) {
            val i = Intent(context, SunriseForegroundService::class.java)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) =
            context.startService(Intent(context, SunriseForegroundService::class.java)
                .apply { action = ACTION_STOP })
    }
}
