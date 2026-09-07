package com.cyprienbrisset.myportal.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object ScreenLock {
    private fun admin(ctx: Context) = ComponentName(ctx, MyDeviceAdminReceiver::class.java)

    fun isActive(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(admin(ctx))

    fun lockOrRequest(ctx: Context) {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        if (dpm.isAdminActive(admin(ctx))) {
            dpm.lockNow()
        } else {
            ctx.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(ctx))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Autorise Fukkatsu No P à éteindre l'écran.")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
