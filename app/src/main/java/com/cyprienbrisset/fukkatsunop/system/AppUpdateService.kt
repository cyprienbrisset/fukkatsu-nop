package com.cyprienbrisset.fukkatsunop.system

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cyprienbrisset.fukkatsunop.BuildConfig
import com.cyprienbrisset.fukkatsunop.store.ApkDownloader
import com.cyprienbrisset.fukkatsunop.store.ApkFile
import com.cyprienbrisset.fukkatsunop.store.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppUpdateService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apkUrl = intent?.getStringExtra(EXTRA_APK_URL) ?: run { stopSelf(); return START_NOT_STICKY }
        UpdateChecker.ensureChannel(this)
        startForeground(NOTIF_ID, buildNotif("Préparation du téléchargement…", -1))

        scope.launch {
            try {
                // Téléchargement — réutilise ApkDownloader existant
                val files = ApkDownloader(this@AppUpdateService).download(
                    pkg = BuildConfig.APPLICATION_ID,
                    files = listOf(ApkFile("fukkatsu-nop", apkUrl, 0L)),
                ) { pct -> updateNotif("Téléchargement… $pct%", pct) }

                updateNotif("Installation en cours…", 100)

                // Installation — réutilise ApkInstaller existant (PackageInstaller)
                ApkInstaller(this@AppUpdateService).install(BuildConfig.APPLICATION_ID, files)

                // PackageInstaller envoie STATUS_PENDING_USER_ACTION → InstallResultReceiver
                // → dialogue de confirmation standard Android (1 tap)

            } catch (e: Exception) {
                updateNotif("Échec : ${e.message?.take(60)}", -1)
                delay(4_000L)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        super.onDestroy()
    }

    private fun buildNotif(text: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Mise à jour Fukkatsu No P")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (progress in 0..100) setProgress(100, progress, false)
                else setProgress(0, 0, true)
            }
            .build()

    private fun updateNotif(text: String, progress: Int) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotif(text, progress))
    }

    companion object {
        const val EXTRA_APK_URL = "apk_url"
        private const val NOTIF_ID   = 9003
        private const val CHANNEL_ID = "app_update"   // même canal qu'UpdateChecker
    }
}
