package com.cyprienbrisset.myportal.airplay

import android.content.Context
import android.net.wifi.WifiManager
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class MdnsAdvertiser(private val context: Context) {
    private var jmdns: JmDNS? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("airplay").also {
            it.setReferenceCounted(false)
            it.acquire()
        }
        val props = mapOf(
            "deviceid" to "AA:BB:CC:DD:EE:FF",
            "features"  to "0x7F8AD0A7,0x1E",
            "flags"     to "0x4",
            "model"     to "AppleTV3,2",
            "pk"        to AirPlayKeyStore.publicKeyHex,
            "srcvers"   to "220.68",
        )
        jmdns = JmDNS.create()
        val service = ServiceInfo.create("_airplay._tcp.local.", "Portal", 7000, 0, 0, props)
        jmdns?.registerService(service)
    }

    fun stop() {
        runCatching { jmdns?.unregisterAllServices() }
        runCatching { jmdns?.close() }
        jmdns = null
        multicastLock?.release()
        multicastLock = null
    }
}
