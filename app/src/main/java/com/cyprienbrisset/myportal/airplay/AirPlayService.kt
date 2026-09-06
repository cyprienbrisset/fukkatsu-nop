package com.cyprienbrisset.myportal.airplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AirPlayService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val CHANNEL = "airplay"
    private val NOTIF_ID = 8000

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("En attente de connexion"))
        AirPlayReceiver.start(this)
        AirPlayReceiver.state.onEach { state ->
            val text = when (state) {
                is AirPlayState.Streaming -> "Mac connecté ● Live"
                is AirPlayState.Connecting -> "Connexion en cours…"
                is AirPlayState.Error -> "Erreur : ${state.msg}"
                else -> "En attente de connexion"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(text))
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL)
            .setContentTitle("Écran Mac")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .build()
}
