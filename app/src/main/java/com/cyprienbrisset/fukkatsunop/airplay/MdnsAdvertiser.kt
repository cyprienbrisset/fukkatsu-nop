package com.cyprienbrisset.fukkatsunop.airplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager

class MdnsAdvertiser(private val context: Context) {
    private var nsdManager: NsdManager? = null
    private var listener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("airplay").also {
            it.setReferenceCounted(false)
            it.acquire()
        }

        // Derive a stable deviceid from the Ed25519 public key
        val pk = AirPlayPairing.edPublicKeyBytes
        val deviceId = "%02X:%02X:%02X:%02X:%02X:%02X".format(
            pk[0], pk[1], pk[2], pk[3], pk[4], pk[5])
        val pkHex = pk.joinToString("") { "%02x".format(it) }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Portal"
            serviceType = "_airplay._tcp"
            port = 7000
            setAttribute("deviceid", deviceId)
            setAttribute("features", "0x5A7FFFF7,0x3E")
            setAttribute("flags", "0x0")
            setAttribute("model", "AppleTV6,2")
            setAttribute("srcvers", "550.10")
            setAttribute("pk", pkHex)
            setAttribute("pi", deviceId)
        }

        val reg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, err: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, err: Int) {}
        }
        listener = reg
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, reg)
    }

    fun stop() {
        listener?.let { runCatching { nsdManager?.unregisterService(it) } }
        listener = null
        nsdManager = null
        multicastLock?.release()
        multicastLock = null
    }
}
