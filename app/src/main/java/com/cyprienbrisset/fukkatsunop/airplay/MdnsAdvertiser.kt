package com.cyprienbrisset.fukkatsunop.airplay

import timber.log.Timber

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager

object AirPlayPrefs {
    private const val PREFS = "airplay_prefs"
    private const val KEY_NAME = "airplay_name"

    fun getName(ctx: Context): String {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)
        if (saved != null) return saved
        val deviceName = android.provider.Settings.Global.getString(ctx.contentResolver, "device_name")
        return deviceName?.takeIf { it.isNotBlank() } ?: "Portal"
    }

    fun setName(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAME, name.trim().ifBlank { "Portal" }).apply()
    }
}

class MdnsAdvertiser(private val context: Context, private val name: String = AirPlayPrefs.getName(context)) {
    private var nsdManager: NsdManager? = null
    private var listener: NsdManager.RegistrationListener? = null
    private var raopListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("airplay").also {
            it.setReferenceCounted(false)
            it.acquire()
        }

        AirPlayPairing.init(context)
        Timber.d("AirPlay identity: name=$name txt=${AirPlayIdentity.airplayTxt()}")
        val airplay = NsdServiceInfo().apply {
            serviceName = name
            serviceType = "_airplay._tcp"
            port = 7000
            AirPlayIdentity.airplayTxt().forEach { (k, v) -> setAttribute(k, v) }
        }
        // Macs also look for the RAOP (AirTunes) service of the same device, as UxPlay advertises it.
        val raop = NsdServiceInfo().apply {
            serviceName = AirPlayIdentity.raopName(name)
            serviceType = "_raop._tcp"
            port = 7000
            AirPlayIdentity.raopTxt().forEach { (k, v) -> setAttribute(k, v) }
        }

        val reg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, err: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, err: Int) {}
        }
        val raopReg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, err: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, err: Int) {}
        }
        listener = reg
        raopListener = raopReg
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager?.registerService(airplay, NsdManager.PROTOCOL_DNS_SD, reg)
        nsdManager?.registerService(raop, NsdManager.PROTOCOL_DNS_SD, raopReg)
    }

    fun stop() {
        listener?.let { runCatching { nsdManager?.unregisterService(it) } }
        raopListener?.let { runCatching { nsdManager?.unregisterService(it) } }
        listener = null
        raopListener = null
        nsdManager = null
        multicastLock?.release()
        multicastLock = null
    }
}
