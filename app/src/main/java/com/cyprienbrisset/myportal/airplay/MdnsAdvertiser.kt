package com.cyprienbrisset.myportal.airplay

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

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Portal"
            serviceType = "_airplay._tcp"
            port = 7000
            setAttribute("deviceid", "AA:BB:CC:DD:EE:FF")
            setAttribute("features", "0x5A7FFFF7,0x1E")
            setAttribute("flags", "0x4")
            setAttribute("model", "AppleTV3,2")
            setAttribute("srcvers", "220.68")
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
