package com.cyprienbrisset.fukkatsunop.airplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.cyprienbrisset.fukkatsunop.ui.airplay.AirPlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AirPlayService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val CHANNEL = "airplay"
    private val NOTIF_ID = 8000

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("En attente de connexion"))
        scope.launch(Dispatchers.IO) { AirPlayReceiver.start(this@AirPlayService) }
        AirPlayReceiver.state.onEach { state ->
            val text = when (state) {
                is AirPlayState.Streaming -> if (state.isExtended) "Écran étendu Mac ● Live" else "Recopie Mac ● Live"
                is AirPlayState.Connecting -> "Connexion en cours…"
                is AirPlayState.Error -> "Erreur : ${state.msg}"
                else -> "En attente de connexion"
            }
            (getSystemService(NotificationManager::class.java)).notify(NOTIF_ID, buildNotification(text))
            // Auto-launch AirPlayActivity for extended display when macOS initiates
            if (state is AirPlayState.Streaming && state.isExtended) {
                startActivity(
                    Intent(this@AirPlayService, AirPlayActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(AirPlayActivity.EXTRA_EXTENDED, true)
                )
            }
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        AirPlayReceiver.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Écran Mac", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL)
            .setContentTitle("Écran Mac")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .build()
}
