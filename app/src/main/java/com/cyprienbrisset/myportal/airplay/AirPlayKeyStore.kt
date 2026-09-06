package com.cyprienbrisset.myportal.airplay

import java.security.KeyPair
import java.security.KeyPairGenerator

object AirPlayKeyStore {
    val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    /** Hex-encoded DER SubjectPublicKeyInfo — advertised as `pk` in mDNS TXT record. */
    val publicKeyHex: String by lazy {
        keyPair.public.encoded.joinToString("") { "%02x".format(it) }
    }

    /** Decrypt an RSA-OAEP/SHA1 encrypted blob (the AES session key from ANNOUNCE SDP). */
    fun decryptOaep(encrypted: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keyPair.private)
        return cipher.doFinal(encrypted)
    }
}
