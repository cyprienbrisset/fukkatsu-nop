package com.cyprienbrisset.fukkatsunop.airplay

/**
 * How the Portal presents itself to Macs, identical to UxPlay (lib/dnssdint.h, global.h).
 * UxPlay advertises an Apple TV 3 with these values and is known to mirror from
 * macOS 12 Monterey up to macOS 27. Features bit 42 (multi-codec) stays off so the
 * Mac sends H.264, the only codec the Portal decoder handles.
 */
object AirPlayIdentity {
    const val MODEL = "AppleTV3,2"
    const val SOURCE_VERSION = "220.68"
    const val FEATURES = 0x5A7FFEE6L
    const val FEATURES_TXT = "0x5A7FFEE6,0x0"
    const val PI = "2e388006-13ba-4041-9a67-25dd4a43d536"
    const val WIDTH = 1920L
    const val HEIGHT = 1080L

    fun pkHex(): String = AirPlayPairing.edPublicKeyBytes.joinToString("") { "%02x".format(it) }

    fun airplayTxt(): List<Pair<String, String>> = listOf(
        "deviceid" to AirPlayPairing.deviceId,
        "features" to FEATURES_TXT,
        "pw" to "false",
        "flags" to "0x4",
        "model" to MODEL,
        "pk" to pkHex(),
        "pi" to PI,
        "srcvers" to SOURCE_VERSION,
        "vv" to "2",
    )

    fun raopTxt(): List<Pair<String, String>> = listOf(
        "ch" to "2", "cn" to "0,1,2,3", "da" to "true", "et" to "0,3,5", "vv" to "2",
        "ft" to FEATURES_TXT, "am" to MODEL, "md" to "0,1,2", "rhd" to "5.6.0.0", "pw" to "false",
        "sr" to "44100", "ss" to "16", "sv" to "false", "tp" to "UDP", "txtvers" to "1",
        "sf" to "0x4", "vs" to SOURCE_VERSION, "vn" to "65537", "pk" to pkHex(),
    )

    /** RAOP service name: hardware address without separators, '@', display name. */
    fun raopName(name: String): String = AirPlayPairing.deviceId.replace(":", "") + "@" + name

    /** DNS TXT wire format: each entry is a length byte followed by "key=value". */
    fun txtBytes(entries: List<Pair<String, String>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        entries.forEach { (k, v) ->
            val b = "$k=$v".toByteArray(Charsets.UTF_8)
            out.write(b.size.coerceAtMost(255)); out.write(b, 0, b.size.coerceAtMost(255))
        }
        return out.toByteArray()
    }

    /** GET /info: only the TXT record when the Mac asks for one, else the full description. */
    fun info(name: String, qualifier: String?): Map<String, Any> {
        when (qualifier) {
            "txtAirPlay" -> return mapOf("txtAirPlay" to txtBytes(airplayTxt()))
            "txtRAOP" -> return mapOf("txtRAOP" to txtBytes(raopTxt()))
        }
        val audioLatency = { type: Long ->
            mapOf("type" to type, "audioType" to "default", "inputLatencyMicros" to 0L, "outputLatencyMicros" to false)
        }
        val audioFormat = { type: Long ->
            mapOf("type" to type, "audioInputFormats" to 0x3fffffcL, "audioOutputFormats" to 0x3fffffcL)
        }
        return mapOf(
            "deviceID" to AirPlayPairing.deviceId,
            "macAddress" to AirPlayPairing.deviceId,
            "pk" to AirPlayPairing.edPublicKeyBytes,
            "features" to FEATURES,
            "name" to name,
            "pi" to PI,
            "vv" to 2L,
            "statusFlags" to 68L,
            "keepAliveLowPower" to true,
            "sourceVersion" to SOURCE_VERSION,
            "keepAliveSendStatsAsBody" to true,
            "model" to MODEL,
            "initialVolume" to 0.0,
            "audioLatencies" to listOf(audioLatency(100L), audioLatency(101L)),
            "audioFormats" to listOf(audioFormat(100L), audioFormat(101L)),
            "displays" to listOf(
                mapOf(
                    "uuid" to "e0ff8a27-6738-3d56-8a16-cc53aacee925",
                    "widthPhysical" to 0L, "heightPhysical" to 0L,
                    "width" to WIDTH, "height" to HEIGHT,
                    "widthPixels" to WIDTH, "heightPixels" to HEIGHT,
                    "rotation" to false,
                    "refreshRate" to 1.0 / 60.0,
                    "maxFPS" to 60L,
                    "overscanned" to false,
                    "features" to 14L,
                ),
            ),
        )
    }
}
