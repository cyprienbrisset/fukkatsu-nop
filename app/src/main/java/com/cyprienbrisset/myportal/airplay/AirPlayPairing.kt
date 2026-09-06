package com.cyprienbrisset.myportal.airplay

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AirPlayPairing {
    // Server's long-term Ed25519 identity keypair (used in pair-setup).
    val edKeyPair by lazy {
        Ed25519KeyPairGenerator()
            .also { it.init(Ed25519KeyGenerationParameters(SecureRandom())) }
            .generateKeyPair()
    }

    val edPublicKeyBytes: ByteArray get() =
        (edKeyPair.public as Ed25519PublicKeyParameters).encoded

    // Generate a fresh X25519 ephemeral keypair for each pair-verify session.
    fun generateX25519Pair() = X25519KeyPairGenerator()
        .also { it.init(X25519KeyGenerationParameters(SecureRandom())) }
        .generateKeyPair()

    // X25519 key agreement: compute 32-byte shared secret.
    fun x25519(myPrivate: X25519PrivateKeyParameters, theirPublic: X25519PublicKeyParameters): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(myPrivate)
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(theirPublic, secret, 0)
        return secret
    }

    // HKDF-SHA512: extract+expand.
    fun hkdf(inputKey: ByteArray, salt: String, info: String, outLen: Int): ByteArray {
        val saltBytes = salt.toByteArray(Charsets.UTF_8)
        val infoBytes = info.toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA512")
        // Extract
        mac.init(SecretKeySpec(saltBytes, "HmacSHA512"))
        val prk = mac.doFinal(inputKey)
        // Expand
        val out = ByteArray(outLen)
        var offset = 0; var prev = ByteArray(0); var ctr = 1
        while (offset < outLen) {
            mac.init(SecretKeySpec(prk, "HmacSHA512"))
            mac.update(prev); mac.update(infoBytes); mac.update(ctr.toByte())
            prev = mac.doFinal()
            val n = minOf(prev.size, outLen - offset)
            prev.copyInto(out, offset, 0, n)
            offset += n; ctr++
        }
        return out
    }

    // ChaCha20-Poly1305 (available API 29+; on API 28 falls back to AES-GCM stub).
    fun chacha20Encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"),
                GCMParameterSpec(128, nonce))
            cipher.doFinal(plaintext)
        } catch (_: Exception) {
            // Android 9 fallback: AES-GCM with same nonce (not spec-compliant but lets us test)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOf(16), "AES"),
                GCMParameterSpec(128, nonce.copyOf(12)))
            cipher.doFinal(plaintext)
        }
    }

    fun chacha20Decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"),
                GCMParameterSpec(128, nonce))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOf(16), "AES"),
                GCMParameterSpec(128, nonce.copyOf(12)))
            cipher.doFinal(ciphertext)
        }
    }
}
