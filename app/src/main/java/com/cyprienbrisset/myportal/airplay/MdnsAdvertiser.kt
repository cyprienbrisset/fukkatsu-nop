package com.cyprienbrisset.myportal.airplay

import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
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
        // JmDNS.create() without an address binds to loopback on Android.
        // We must pass the actual WiFi interface address so mDNS packets go out on the LAN.
        @Suppress("DEPRECATION")
        val ipInt = wm.connectionInfo?.ipAddress ?: 0
        val wifiAddr: InetAddress? = runCatching {
            InetAddress.getByAddress(byteArrayOf(
                (ipInt and 0xFF).toByte(),
                (ipInt shr 8 and 0xFF).toByte(),
                (ipInt shr 16 and 0xFF).toByte(),
                (ipInt shr 24 and 0xFF).toByte(),
            ))
        }.getOrNull()

        val props = mapOf(
            "deviceid" to "AA:BB:CC:DD:EE:FF",
            "features"  to "0x5A7FFFF7,0x1E",
            "flags"     to "0x4",
            "model"     to "AppleTV3,2",
            "pk"        to AirPlayKeyStore.publicKeyHex,
            "srcvers"   to "220.68",
        )
        jmdns = if (wifiAddr != null) JmDNS.create(wifiAddr) else JmDNS.create()
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
