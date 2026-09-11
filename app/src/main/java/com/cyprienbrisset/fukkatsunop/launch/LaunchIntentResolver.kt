package com.cyprienbrisset.fukkatsunop.launch

import android.content.Context
import android.content.Intent

data class InstalledApp(val label: String, val packageName: String)

/**
 * Resolves and launches installed apps. The [isLaunchable] seam keeps the
 * decision logic unit-testable without a real PackageManager.
 */
class LaunchIntentResolver(private val isLaunchable: (String) -> Boolean) {

    fun resolvablePackageOrNull(pkg: String): String? =
        if (isLaunchable(pkg)) pkg else null

    companion object {
        fun fromContext(context: Context): LaunchIntentResolver {
            val pm = context.packageManager
            return LaunchIntentResolver { pkg -> pm.getLaunchIntentForPackage(pkg) != null }
        }

        /** Launches [pkg]; returns true on success. */
        fun launch(context: Context, pkg: String): Boolean {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            return true
        }

        /** Lists launchable installed apps (for the tile picker). */
        fun installedLaunchableApps(context: Context): List<InstalledApp> {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            return pm.queryIntentActivities(intent, 0)
                .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
}
