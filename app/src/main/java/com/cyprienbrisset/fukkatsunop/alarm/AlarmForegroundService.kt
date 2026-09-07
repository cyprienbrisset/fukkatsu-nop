package com.cyprienbrisset.fukkatsunop.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class AlarmForegroundService : Service() {
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rampStep = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Become foreground first on EVERY start path to satisfy the O+ startForegroundService contract.
        val alarmId = intent?.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1) ?: -1
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: ""
        startForeground(AlarmNotifications.NOTIF_ID, AlarmNotifications.buildRinging(this, alarmId, label))

        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            ACTION_SNOOZE -> {
                val id = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MIN, 10)
                if (id >= 0) AlarmSnooze.schedule(this, id, minutes)
                stopEverything(); return START_NOT_STICKY
            }
        }
        if (intent == null) { stopEverything(); return START_NOT_STICKY } // guard null re-delivery

        // Fresh ring — reset any prior sound/wakelock/ramp first (guards double-start).
        resetRinging()
        val ringUri = intent.getStringExtra(EXTRA_RINGTONE)
        acquireWakeLock()
        startRinging(ringUri)
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "myportal:alarm",
        ).also { it.acquire(10 * 60 * 1000L) }
    }

    private fun startRinging(ringUri: String?) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, volumeAtStep(0, RAMP_STEPS, maxVol), 0)

        val uri: Uri = ringUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, uri).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            @Suppress("DEPRECATION")
            streamType = AudioManager.STREAM_ALARM
            play()
        }
        scheduleRamp(am, maxVol)
    }

    private fun scheduleRamp(am: AudioManager, maxVol: Int) {
        val tick = object : Runnable {
            override fun run() {
                rampStep++
                am.setStreamVolume(AudioManager.STREAM_ALARM, volumeAtStep(rampStep, RAMP_STEPS, maxVol), 0)
                if (rampStep < RAMP_STEPS) handler.postDelayed(this, RAMP_INTERVAL_MS)
            }
        }
        handler.postDelayed(tick, RAMP_INTERVAL_MS)
    }

    private fun resetRinging() {
        handler.removeCallbacksAndMessages(null)
        rampStep = 0
        ringtone?.stop(); ringtone = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }

    private fun stopEverything() {
        resetRinging()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { stopEverything(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.cyprienbrisset.fukkatsunop.ALARM_STOP"
        const val ACTION_SNOOZE = "com.cyprienbrisset.fukkatsunop.ALARM_SNOOZE"
        const val EXTRA_LABEL = "label"
        const val EXTRA_RINGTONE = "ringtone"
        const val EXTRA_SNOOZE_MIN = "snooze_min"
        const val RAMP_STEPS = 30
        const val RAMP_INTERVAL_MS = 1000L

        fun start(context: Context, alarmId: Long, label: String, ringtoneUri: String?) {
            val i = Intent(context, AlarmForegroundService::class.java)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_RINGTONE, ringtoneUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }
        fun stop(context: Context) {
            context.startService(Intent(context, AlarmForegroundService::class.java).apply { action = ACTION_STOP })
        }
        fun snooze(context: Context, alarmId: Long, minutes: Int) {
            context.startService(Intent(context, AlarmForegroundService::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_SNOOZE_MIN, minutes)
            })
        }
    }
}
