package com.cyprienbrisset.fukkatsunop.system

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cyprienbrisset.fukkatsunop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object UpdateChecker {

    // ── Configurer cette URL avant la première release ────────────────────────
    // Pointer vers le version.json hébergé sur GitHub Releases ou Coolify.
    // Format attendu : { "versionCode": 2, "versionName": "1.1.0", "apkUrl": "https://...", "notes": "..." }
    const val MANIFEST_URL = "https://raw.githubusercontent.com/cyprienbrisset/fukkatsu-nop/main/version.json"

    private const val PREFS          = "update_checker"
    private const val KEY_REMOTE_CODE = "remote_version_code"
    private const val KEY_REMOTE_NAME = "remote_version_name"
    private const val KEY_APK_URL    = "apk_url"
    private const val KEY_NOTES      = "notes"
    private const val CHANNEL_ID     = "app_update"
    private const val NOTIF_ID       = 9002
    private const val ACTION_CHECK   = "com.cyprienbrisset.fukkatsunop.UPDATE_CHECK"

    // ── Lecture de l'état mis en cache ────────────────────────────────────────

    /** Nom de version distante si supérieure à l'installée, sinon null. */
    fun availableVersionName(ctx: Context): String? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code  = prefs.getInt(KEY_REMOTE_CODE, 0)
        return if (code > BuildConfig.VERSION_CODE)
            prefs.getString(KEY_REMOTE_NAME, null)
        else null
    }

    fun apkUrl(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APK_URL, null)

    fun notes(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NOTES, null)

    // ── Vérification réseau ───────────────────────────────────────────────────

    suspend fun check(ctx: Context) = withContext(Dispatchers.IO) {
        runCatching {
            ensureChannel(ctx)
            val body = OkHttpClient()
                .newCall(Request.Builder().url(MANIFEST_URL).build())
                .execute().use { it.body?.string() } ?: return@withContext

            val j = JSONObject(body)
            val remoteCode = j.getInt("versionCode")
            val remoteName = j.getString("versionName")
            val apkUrl     = j.getString("apkUrl")
            val notes      = j.optString("notes", "")

            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_REMOTE_CODE, remoteCode)
                .putString(KEY_REMOTE_NAME, remoteName)
                .putString(KEY_APK_URL, apkUrl)
                .putString(KEY_NOTES, notes)
                .apply()

            if (remoteCode > BuildConfig.VERSION_CODE) {
                notify(ctx, remoteName, apkUrl)
            }
        }
        // Échec réseau : ignoré silencieusement — réessai au prochain cycle quotidien
    }

    // ── Planification quotidienne ─────────────────────────────────────────────

    fun scheduleDaily(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + AlarmManager.INTERVAL_DAY,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(ctx),
        )
    }

    // ── Lancement de la mise à jour ───────────────────────────────────────────

    fun startUpdate(ctx: Context) {
        val url = apkUrl(ctx) ?: return
        val i = Intent(ctx, AppUpdateService::class.java).putExtra(AppUpdateService.EXTRA_APK_URL, url)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
        else ctx.startService(i)
    }

    // ── Interne ───────────────────────────────────────────────────────────────

    private fun pendingIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, NOTIF_ID,
        Intent(ACTION_CHECK, null, ctx, UpdateCheckReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun updatePendingIntent(ctx: Context, apkUrl: String) = PendingIntent.getService(
        ctx, NOTIF_ID + 1,
        Intent(ctx, AppUpdateService::class.java).putExtra(AppUpdateService.EXTRA_APK_URL, apkUrl),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Mises à jour du launcher", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }

    private fun notify(ctx: Context, version: String, apkUrl: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Mise à jour disponible")
            .setContentText("Fukkatsu No P $version est disponible → Réglages pour installer")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.stat_sys_download,
                "Mettre à jour",
                updatePendingIntent(ctx, apkUrl),
            )
            .build()
        )
    }
}
