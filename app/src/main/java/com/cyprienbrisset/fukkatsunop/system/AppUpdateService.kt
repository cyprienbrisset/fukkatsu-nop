package com.cyprienbrisset.fukkatsunop.system

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.cyprienbrisset.fukkatsunop.BuildConfig
import com.cyprienbrisset.fukkatsunop.store.ApkDownloader
import com.cyprienbrisset.fukkatsunop.store.ApkFile
import com.cyprienbrisset.fukkatsunop.store.InstallTrampolineActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppUpdateService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)

    // No read/call timeout — APK download can be 100+ MB on slow WiFi.
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apkUrl = intent?.getStringExtra(EXTRA_APK_URL) ?: run { stopSelf(); return START_NOT_STICKY }
        UpdateChecker.ensureChannel(this)
        startForeground(NOTIF_ID, buildNotif("Préparation du téléchargement…", -1))
        UpdateProgress.set(0)

        scope.launch {
            try {
                val files = ApkDownloader(this@AppUpdateService, http).download(
                    pkg = BuildConfig.APPLICATION_ID,
                    files = listOf(ApkFile("fukkatsu-nop", apkUrl, 0L)),
                ) { pct ->
                    UpdateProgress.set(pct)
                    updateNotif("Téléchargement… $pct%", pct)
                }

                UpdateProgress.set(100)
                updateNotif("Installation en cours…", 100)

                // Sur Portal Android 9, la session PackageInstaller aboutit à un écran blanc.
                // On passe par ACTION_INSTALL_PACKAGE via FileProvider (même chemin que FukkaStore).
                val apk = files.first()
                val contentUri = FileProvider.getUriForFile(
                    this@AppUpdateService,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    apk,
                )
                startActivity(
                    Intent(this@AppUpdateService, InstallTrampolineActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(InstallTrampolineActivity.EXTRA_APK_URI, contentUri.toString())
                        .putExtra(InstallTrampolineActivity.EXTRA_PACKAGE, BuildConfig.APPLICATION_ID),
                )
                // Laisser le temps à l'activité d'ouvrir le fichier, puis libérer l'overlay.
                // Si l'install réussit le process est tué de toute façon ; si elle échoue
                // l'overlay disparaît et l'utilisateur reprend la main.
                delay(4_000L)
                stopSelf()

            } catch (e: Exception) {
                UpdateProgress.set(-1)
                updateNotif("Échec : ${e.message?.take(60)}", -1)
                delay(4_000L)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        UpdateProgress.set(-1)
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
        private const val CHANNEL_ID = "app_update"
    }
}
