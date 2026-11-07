package com.cyprienbrisset.fukkatsunop.integration

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

data class RecentApp(val packageName: String, val label: String)

/**
 * Apps the user has opened and not closed yet.
 *
 * Android 9/10 gives a launcher no list of running tasks, so this combines the apps
 * launched from Fukkatsu (persisted) with the system usage history when the
 * GET_USAGE_STATS app-op is granted (provision script). Closing an app hides it
 * until it is used again.
 */
object OpenAppsRepository {
    private const val PREFS = "open_apps"
    private const val KEY_LAUNCHED = "launched"   // "pkg\tlabel\tts" lines
    private const val KEY_CLOSED = "closed"       // "pkg\tts" lines
    private const val WINDOW_MS = 12 * 60 * 60 * 1000L
    private const val MAX = 12

    private val _apps = MutableStateFlow<List<RecentApp>>(emptyList())
    val apps: StateFlow<List<RecentApp>> = _apps.asStateFlow()

    fun record(ctx: Context, packageName: String, label: String) {
        val launched = readLaunched(ctx).filterKeys { it != packageName } + (packageName to (label to now()))
        writeLaunched(ctx, launched)
        writeClosed(ctx, readClosed(ctx) - packageName)
        refresh(ctx)
    }

    fun close(ctx: Context, packageName: String) {
        stop(ctx, packageName)
        writeClosed(ctx, readClosed(ctx) + (packageName to now()))
        refresh(ctx)
    }

    fun closeAll(ctx: Context) {
        val closed = readClosed(ctx).toMutableMap()
        _apps.value.forEach { stop(ctx, it.packageName); closed[it.packageName] = now() }
        writeClosed(ctx, closed)
        refresh(ctx)
    }

    fun refresh(ctx: Context) {
        val since = now() - WINDOW_MS
        val closed = readClosed(ctx)
        val lastUse = HashMap<String, Long>()
        readLaunched(ctx).forEach { (pkg, v) -> if (v.second > since) lastUse[pkg] = v.second }
        usageForeground(ctx, since).forEach { (pkg, ts) -> if (ts > (lastUse[pkg] ?: 0)) lastUse[pkg] = ts }

        val pm = ctx.packageManager
        _apps.value = lastUse
            .filter { (pkg, ts) -> pkg != ctx.packageName && ts > (closed[pkg] ?: 0) && pm.getLaunchIntentForPackage(pkg) != null }
            .entries.sortedByDescending { it.value }
            .take(MAX)
            .mapNotNull { (pkg, _) ->
                val label = readLaunched(ctx)[pkg]?.first
                    ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
                    ?: return@mapNotNull null
                RecentApp(pkg, label)
            }
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun usageForeground(ctx: Context, since: Long): Map<String, Long> {
        if (!hasUsageAccess(ctx)) return emptyMap()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val out = HashMap<String, Long>()
        runCatching {
            val events = usm.queryEvents(since, now())
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                @Suppress("DEPRECATION")
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) out[e.packageName] = e.timeStamp
            }
        }.onFailure { Timber.w(it, "usage events") }
        return out
    }

    /** Force-stop when the Portal grants FORCE_STOP_PACKAGES, otherwise kill its background process. */
    private fun stop(ctx: Context, pkg: String) {
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return
        val forced = runCatching {
            ActivityManager::class.java.getMethod("forceStopPackage", String::class.java).invoke(am, pkg)
        }.isSuccess
        if (!forced) runCatching { am.killBackgroundProcesses(pkg) }
    }

    private fun now() = System.currentTimeMillis()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readLaunched(ctx: Context): Map<String, Pair<String, Long>> =
        prefs(ctx).getString(KEY_LAUNCHED, "").orEmpty().lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size == 3) p[0] to (p[1] to (p[2].toLongOrNull() ?: 0L)) else null
        }.toMap()

    private fun writeLaunched(ctx: Context, m: Map<String, Pair<String, Long>>) {
        val keep = m.entries.sortedByDescending { it.value.second }.take(30)
        prefs(ctx).edit().putString(KEY_LAUNCHED, keep.joinToString("\n") { "${it.key}\t${it.value.first.replace('\t', ' ')}\t${it.value.second}" }).apply()
    }

    private fun readClosed(ctx: Context): Map<String, Long> =
        prefs(ctx).getString(KEY_CLOSED, "").orEmpty().lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size == 2) p[0] to (p[1].toLongOrNull() ?: 0L) else null
        }.toMap()

    private fun writeClosed(ctx: Context, m: Map<String, Long>) {
        prefs(ctx).edit().putString(KEY_CLOSED, m.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }
}
