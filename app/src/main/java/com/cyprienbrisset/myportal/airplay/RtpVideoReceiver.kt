package com.cyprienbrisset.myportal.airplay

import java.net.DatagramPacket
import java.net.DatagramSocket
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RtpVideoReceiver(
    private val session: AirPlaySession,
    private val onNalUnit: (ByteArray) -> Unit,
) {
    private var udpSocket: DatagramSocket? = null
    @Volatile private var running = false

    fun start() {
        running = true
        udpSocket = DatagramSocket(session.rtpPort)
        Thread({
            val buf = ByteArray(65536)
            val packet = DatagramPacket(buf, buf.size)
            while (running) {
                runCatching {
                    udpSocket?.receive(packet) ?: return@runCatching
                    processPacket(buf, packet.length)
                }
            }
        }, "airplay-rtp").start()
    }

    fun stop() {
        running = false
        runCatching { udpSocket?.close() }
    }

    private fun processPacket(data: ByteArray, length: Int) {
        // RTP header is 12 bytes (fixed). Payload starts at byte 12.
        if (length <= 12) return
        val payloadOffset = 12
        val payloadLen = length - payloadOffset
        // AES-CBC requires payload to be a multiple of 16; decrypt only the aligned portion,
        // append the unaligned tail as-is (AirPlay spec: last <16 bytes are unencrypted).
        val alignedLen = (payloadLen / 16) * 16
        val decrypted = if (alignedLen > 0) {
            // Re-init cipher with the same IV for each packet (IV is not chained across packets).
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(session.aesKey, "AES"),
                IvParameterSpec(session.aesIv),
            )
            val dec = cipher.doFinal(data, payloadOffset, alignedLen)
            if (alignedLen < payloadLen) {
                dec + data.copyOfRange(payloadOffset + alignedLen, payloadOffset + payloadLen)
            } else dec
        } else {
            data.copyOfRange(payloadOffset, payloadOffset + payloadLen)
        }
        onNalUnit(decrypted)
    }
}
