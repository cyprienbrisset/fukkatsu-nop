package com.cyprienbrisset.myportal.airplay

import com.cyprienbrisset.fukkatsunop.airplay.CodecConfig
import com.cyprienbrisset.fukkatsunop.airplay.MirrorKeys
import com.cyprienbrisset.fukkatsunop.airplay.MirrorStreamReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class MirrorStreamTest {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // Expected values computed independently with Python hashlib (see UxPlay mirror_buffer.c).
    @Test fun sessionKeyIsHashedWithEcdhSecret() {
        val fp = ByteArray(16) { (200 + it).toByte() }
        val ecdh = ByteArray(32) { (100 + it).toByte() }
        assertArrayEquals(hex("0058bf0e809bda0cc36b1e1b93f077b7"), MirrorKeys.sessionKey(fp, ecdh))
        assertArrayEquals(fp, MirrorKeys.sessionKey(fp, null))
    }

    @Test fun streamKeyUsesUnsignedConnectionId() {
        val sk = ByteArray(16) { it.toByte() }
        val (key, iv) = MirrorKeys.streamKey(sk, 0xFEDCBA9876543210uL.toLong())
        assertArrayEquals(hex("0e017541a025d8e13b0f6c7ee2f829e5"), key)
        assertArrayEquals(hex("a9ce26a6f318aec444d68318f5107cc1"), iv)
    }

    @Test fun keystreamContinuesAcrossOddSizedPackets() {
        val sk = Random(1).nextBytes(16)
        val id = 42L
        val plain = Random(2).nextBytes(10_000)
        val (key, iv) = MirrorKeys.streamKey(sk, id)
        val enc = Cipher.getInstance("AES/CTR/NoPadding")
            .apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
            .doFinal(plain)

        val dec = MirrorKeys.videoCipher(sk, id)
        val out = ByteArrayOutputStream()
        var off = 0
        for (size in listOf(1, 15, 17, 6, 1000, 3333, 33)) {
            out.write(dec.update(enc, off, size)); off += size
        }
        out.write(dec.update(enc, off, enc.size - off))
        assertArrayEquals(plain, out.toByteArray())
    }

    private fun avcc(vararg nals: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        nals.forEach { n -> o.write(byteArrayOf((n.size ushr 24).toByte(), (n.size ushr 16).toByte(), (n.size ushr 8).toByte(), n.size.toByte())); o.write(n) }
        return o.toByteArray()
    }

    private fun packet(type: Int, payload: ByteArray): ByteArray {
        val h = ByteArray(128)
        val n = payload.size
        h[0] = n.toByte(); h[1] = (n ushr 8).toByte(); h[2] = (n ushr 16).toByte(); h[3] = (n ushr 24).toByte()
        h[4] = type.toByte()
        return h + payload
    }

    @Test fun readerDecryptsVideoAndParsesCodecConfig() {
        val sk = Random(3).nextBytes(16)
        val id = 7L
        val sps = byteArrayOf(0x67, 0x64, 0x00, 0x28, 0x11)
        val pps = byteArrayOf(0x68, 0x22, 0x33)
        val avcC = byteArrayOf(1, 0x64, 0, 0x28, 0xFF.toByte(), 0xE1.toByte(), 0, sps.size.toByte()) + sps +
            byteArrayOf(1, 0, pps.size.toByte()) + pps

        val idr = byteArrayOf(0x65) + Random(4).nextBytes(500)
        val slice = byteArrayOf(0x41) + Random(5).nextBytes(77)
        val (key, iv) = MirrorKeys.streamKey(sk, id)
        val enc = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        val f1 = enc.update(avcc(idr))
        val f2 = enc.update(avcc(slice))

        val stream = packet(0x01, avcC) + packet(0x00, f1) + packet(0x05, ByteArray(20)) + packet(0x00, f2)
        var config: CodecConfig? = null
        val frames = mutableListOf<Pair<ByteArray, Boolean>>()
        MirrorStreamReader(MirrorKeys.videoCipher(sk, id), { config = it }, { f, k -> frames += f to k })
            .run(ByteArrayInputStream(stream))

        assertNotNull(config)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + sps, config!!.sps)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + pps, config!!.pps)
        assertEquals(2, frames.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + idr, frames[0].first)
        assertTrue(frames[0].second)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + slice, frames[1].first)
        assertFalse(frames[1].second)
    }

    @Test fun leIntAndBadAvcc() {
        assertEquals(0x04030201, MirrorStreamReader.leInt(byteArrayOf(1, 2, 3, 4), 0))
        assertNull(MirrorStreamReader.avccToAnnexB(byteArrayOf(0, 0, 0, 9, 1)))
        assertNull(MirrorStreamReader.parseAvcC(ByteArray(3)))
    }
}
