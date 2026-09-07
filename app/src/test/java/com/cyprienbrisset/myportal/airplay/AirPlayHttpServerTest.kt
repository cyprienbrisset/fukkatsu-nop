package com.cyprienbrisset.fukkatsunop.airplay

import org.junit.Assert.*
import org.junit.Test

class AirPlayHttpServerTest {
    @Test
    fun `transport header port extraction`() {
        val header = "RTP/AVP/UDP;unicast;client_port=12345-12346"
        val port = Regex("client_port=(\\d+)").find(header)?.groupValues?.get(1)?.toIntOrNull()
        assertEquals(12345, port)
    }

    @Test
    fun `SDP aesiv base64 decode`() {
        val ivBytes = ByteArray(16) { it.toByte() }
        val b64 = java.util.Base64.getEncoder().encodeToString(ivBytes)
        val decoded = java.util.Base64.getDecoder().decode(b64)
        assertArrayEquals(ivBytes, decoded)
    }
}
