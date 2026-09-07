package com.cyprienbrisset.fukkatsunop.store

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process

/**
 * Transparent trampoline that relays install confirmation dialogs.
 *
 * Two modes:
 * - File-based (EXTRA_APK_URI set): starts ACTION_INSTALL_PACKAGE with a FileProvider
 *   content URI and waits for the result via onActivityResult. This path works on
 *   Android 9 (Portal 1st gen) where the session-based CONFIRM_PERMISSIONS action
 *   renders a blank PackageInstallerActivity.
 * - Session-based (EXTRA_CONFIRM set): relays the PackageInstaller STATUS_PENDING_USER_ACTION
 *   confirm intent from BroadcastReceiver context into a proper Activity context.
 */
class InstallTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apkUri = intent.getStringExtra(EXTRA_APK_URI)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)

        if (apkUri != null && pkg != null) {
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = Uri.parse(apkUri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, packageName)
                // Provide our UID so PackageInstallerActivity can look up our package name
                // and verify REQUEST_INSTALL_PACKAGES via AppOps → reaches initiateInstall()
                // instead of showing the anonymous-source dialog that renders blank on Portal 1st gen.
                putExtra("android.intent.extra.ORIGINATING_UID", Process.myUid())
            }
            @Suppress("DEPRECATION")
            startActivityForResult(installIntent, REQUEST_INSTALL)
            return
        }

        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(EXTRA_CONFIRM)
        if (confirm != null) startActivity(confirm)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_INSTALL) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE)
            InstallEvents.emit(InstallEvent(pkg, success = resultCode == RESULT_OK))
        }
        finish()
    }

    companion object {
        const val EXTRA_CONFIRM = "confirm_intent"
        const val EXTRA_APK_URI = "apk_uri"
        const val EXTRA_PACKAGE = "package_name"
        private const val REQUEST_INSTALL = 1
    }
}
