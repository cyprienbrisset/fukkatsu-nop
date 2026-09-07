package com.cyprienbrisset.fukkatsunop.store

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import java.io.File

class ApkInstaller(private val context: Context) {
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun requestPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(packageName: String, apks: List<File>) {
        // On Android 9 (Portal 1st gen), PackageInstallerActivity shows a white overlay
        // but touch events pass through — AccessibilityService auto-clicks Install.
        // We use installViaSession on ALL API levels so all split APKs (ABI, density…)
        // are committed together; installing only base.apk causes crashes on apps with
        // native libs.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            ensureAccessibilityServiceEnabled()
        }
        installViaSession(packageName, apks)
    }

    private fun ensureAccessibilityServiceEnabled() {
        val serviceId = "${context.packageName}/.store.InstallAccessibilityService"
        val current = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: ""
        if (current.split(":").contains(serviceId)) return
        val updated = if (current.isEmpty()) serviceId else "$current:$serviceId"
        runCatching {
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updated,
            )
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }
    }

    private fun installViaSession(packageName: String, apks: List<File>) {
        val compatible = filterCompatibleApks(apks)
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setInstallLocation(android.content.pm.PackageInfo.INSTALL_LOCATION_AUTO)
            setOriginatingUid(android.os.Process.myUid())
            setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            val total = compatible.sumOf { it.length() }
            if (total > 0) setSize(total)
        }
        val sessionId = pi.createSession(params)
        pi.openSession(sessionId).use { s ->
            compatible.forEach { apk ->
                s.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(InstallResultReceiver.ACTION).setPackage(context.packageName), flags,
            )
            s.commit(pending.intentSender)
        }
    }

    /**
     * Drop ABI splits that the device can't run. gplayapi fetches splits based on the device
     * profile (Pixel 3a: arm64-v8a + armeabi-v7a), but Portal 1st gen is 32-bit only. Installing
     * an arm64 split on an armeabi-v7a-only device causes INSTALL_FAILED_CPU_ABI_INCOMPATIBLE.
     *
     * Rule: if an APK name contains an ABI token (arm64_v8a, armeabi_v7a, x86, x86_64…), keep
     * it only if the corresponding ABI is in Build.SUPPORTED_ABIS. Non-ABI splits (base, language,
     * density) are always kept.
     */
    private fun filterCompatibleApks(apks: List<File>): List<File> {
        // Build.SUPPORTED_ABIS uses hyphens; APK split names use underscores.
        val supported = Build.SUPPORTED_ABIS.map { it.replace('-', '_') }.toSet()
        val knownAbis = setOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86")
        return apks.filter { apk ->
            val name = apk.nameWithoutExtension
            val abiInName = knownAbis.firstOrNull { name.contains(it) }
            abiInName == null || abiInName in supported
        }
    }
}
