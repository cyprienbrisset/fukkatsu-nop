package com.cyprienbrisset.fukkatsunop.airplay

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.modes.SICBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AirPlayPairing {
    val edKeyPair by lazy {
        Ed25519KeyPairGenerator()
            .also { it.init(Ed25519KeyGenerationParameters(SecureRandom())) }
            .generateKeyPair()
    }

    val edPublicKeyBytes: ByteArray get() =
        (edKeyPair.public as Ed25519PublicKeyParameters).encoded

    val deviceId: String get() {
        val pk = edPublicKeyBytes
        return "%02X:%02X:%02X:%02X:%02X:%02X".format(pk[0], pk[1], pk[2], pk[3], pk[4], pk[5])
    }

    // pair_fruit M2: server sends full 64-byte Ed25519 key = seed(32) || pub(32).
    // The first 32 bytes (seed) act as the challenge value that macOS echoes back in M3.
    val edFullKey: ByteArray get() {
        val seed = (edKeyPair.private as Ed25519PrivateKeyParameters).encoded // 32 bytes
        val pub  = (edKeyPair.public  as Ed25519PublicKeyParameters).encoded  // 32 bytes
        return seed + pub // 64 bytes
    }

    val edPrivSeedBytes: ByteArray get() =
        (edKeyPair.private as Ed25519PrivateKeyParameters).encoded // 32 bytes

    fun generateX25519Pair() = X25519KeyPairGenerator()
        .also { it.init(X25519KeyGenerationParameters(SecureRandom())) }
        .generateKeyPair()

    fun x25519(myPrivate: X25519PrivateKeyParameters, theirPublic: X25519PublicKeyParameters): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(myPrivate)
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(theirPublic, secret, 0)
        return secret
    }

    // Sign message with server's long-term Ed25519 private key → 64-byte signature.
    fun edSign(message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, edKeyPair.private as Ed25519PrivateKeyParameters)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    // Verify a signature against our own public key.
    fun edVerify(message: ByteArray, signature: ByteArray): Boolean =
        edVerifyWith(message, signature, edKeyPair.public as Ed25519PublicKeyParameters)

    // Verify a signature against an arbitrary Ed25519 public key (32 bytes).
    fun edVerifyWith(message: ByteArray, signature: ByteArray, pubKeyBytes: ByteArray): Boolean =
        edVerifyWith(message, signature, Ed25519PublicKeyParameters(pubKeyBytes, 0))

    private fun edVerifyWith(message: ByteArray, signature: ByteArray, pub: Ed25519PublicKeyParameters): Boolean {
        val verifier = Ed25519Signer()
        verifier.init(false, pub)
        verifier.update(message, 0, message.size)
        return runCatching { verifier.verifySignature(signature) }.getOrDefault(false)
    }

    fun hkdf(inputKey: ByteArray, salt: String, info: String, outLen: Int): ByteArray {
        val saltBytes = salt.toByteArray(Charsets.UTF_8)
        val infoBytes = info.toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(saltBytes, "HmacSHA512"))
        val prk = mac.doFinal(inputKey)
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

    // SHA-512(parts...) — simple concatenation hash used for AES-CTR key/IV derivation in pair_fruit.
    fun sha512Concat(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-512")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    // AES-128-CTR (SIC mode) — used for pair_fruit M3 encrypt/decrypt.
    fun aesCtr128(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = SICBlockCipher(AESEngine())
        cipher.init(true, ParametersWithIV(KeyParameter(key), iv))
        val out = ByteArray(data.size)
        cipher.processBytes(data, 0, data.size, out, 0)
        return out
    }

    // ChaCha20-Poly1305 via BouncyCastle — works on all Android versions.
    fun chacha20Encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val written = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, written)
        return out
    }

    fun chacha20Decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        val written = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        cipher.doFinal(out, written)
        return out
    }
}
