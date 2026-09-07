package com.cyprienbrisset.fukkatsunop.system.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.cyprienbrisset.fukkatsunop.system.DarkModeManager
import com.cyprienbrisset.fukkatsunop.system.ScreenLock

object VoiceCommandDispatcher {

    fun dispatch(ctx: Context, text: String) {
        val t = text.trim().lowercase()
        when {
            t.contains("mode nuit") || t.contains("nuit")    -> DarkModeManager.apply(ctx, true)
            t.contains("mode jour") || t.contains("jour")    -> DarkModeManager.apply(ctx, false)
            t.contains("écran") || t.contains("verrouille") || t.contains("éteins") -> ScreenLock.lockOrRequest(ctx)
            t.startsWith("ouvre") || t.startsWith("lance") || t.startsWith("ouvrir") -> {
                val appName = t
                    .removePrefix("ouvrir")
                    .removePrefix("ouvre")
                    .removePrefix("lance")
                    .trim()
                launchAppByName(ctx, appName)
            }
            else -> { /* unknown command */ }
        }
    }

    fun buildCommandGrammar(ctx: Context): String {
        val apps = ctx.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { info ->
                runCatching { ctx.packageManager.getApplicationLabel(info).toString().lowercase() }.getOrNull()
            }
            .filter { it.length in 3..20 }
            .take(40)
            .map { "ouvre $it" }
        val fixed = listOf("mode nuit", "mode jour", "éteins l'écran", "verrouille", "météo", "alarme", "[unk]")
        val all = (apps + fixed).joinToString(", ") { "\"$it\"" }
        return "[$all]"
    }

    private fun launchAppByName(ctx: Context, name: String) {
        if (name.isBlank()) return
        val pm = ctx.packageManager
        val best = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { info ->
                val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrNull() ?: return@mapNotNull null
                val score = similarity(label.lowercase(), name)
                if (score > 0.5f) Pair(info.packageName, score) else null
            }
            .maxByOrNull { it.second }
            ?.first ?: return
        pm.getLaunchIntentForPackage(best)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(it)
        }
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.contains(b) || b.contains(a)) return 0.9f
        val longer = maxOf(a.length, b.length)
        if (longer == 0) return 1f
        return (longer - levenshtein(a, b)).toFloat() / longer
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length]
    }
}
