package com.cyprienbrisset.myportal.airplay

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher

class AirPlayKeyStoreTest {
    @Test
    fun `publicKeyHex is 64+ character hex string`() {
        val hex = AirPlayKeyStore.publicKeyHex
        assertTrue("expected non-empty hex", hex.length >= 64)
        assertTrue("expected hex chars only", hex.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `decryptOaep round-trips a 16-byte AES key`() {
        val aesKey = ByteArray(16) { it.toByte() }
        val encryptCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, AirPlayKeyStore.keyPair.public)
        val encrypted = encryptCipher.doFinal(aesKey)
        val decrypted = AirPlayKeyStore.decryptOaep(encrypted)
        assertArrayEquals(aesKey, decrypted)
    }
}
