package com.cyprienbrisset.fukkatsunop.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(
                    Intent(context, InstallTrampolineActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(InstallTrampolineActivity.EXTRA_CONFIRM, confirm),
                )
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Installé", Toast.LENGTH_SHORT).show()
                InstallEvents.emit(InstallEvent(pkg, success = true))
            }
            else -> {
                Toast.makeText(context, "Échec install : ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}", Toast.LENGTH_LONG).show()
                InstallEvents.emit(InstallEvent(pkg, success = false))
            }
        }
    }

    companion object { const val ACTION = "com.cyprienbrisset.fukkatsunop.INSTALL_STATUS" }
}
