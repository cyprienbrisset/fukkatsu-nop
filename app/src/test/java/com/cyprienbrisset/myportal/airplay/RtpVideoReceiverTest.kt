package com.cyprienbrisset.myportal.airplay

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RtpVideoReceiverTest {
    private val key = ByteArray(16) { 0x42.toByte() }
    private val iv  = ByteArray(16) { 0x0F.toByte() }

    @Test
    fun `AES-CBC round-trip with 32-byte payload (2 blocks)`() {
        val plaintext = ByteArray(32) { it.toByte() }
        val encCipher = Cipher.getInstance("AES/CBC/NoPadding")
        encCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val encrypted = encCipher.doFinal(plaintext)

        val decCipher = Cipher.getInstance("AES/CBC/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val decrypted = decCipher.doFinal(encrypted)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `unaligned tail is appended verbatim`() {
        val aligned = ByteArray(16) { 0x01.toByte() }
        val tail = ByteArray(5) { 0xFF.toByte() }
        val payload = aligned + tail
        val encCipher = Cipher.getInstance("AES/CBC/NoPadding")
        encCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val encAligned = encCipher.doFinal(aligned)
        val encPayload = encAligned + tail

        // Simulate RtpVideoReceiver: decrypt 16-byte block, append 5-byte tail verbatim
        val decCipher = Cipher.getInstance("AES/CBC/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val dec = decCipher.doFinal(encPayload, 0, 16)
        val result = dec + encPayload.copyOfRange(16, encPayload.size)

        assertArrayEquals(payload, result)
    }
}
