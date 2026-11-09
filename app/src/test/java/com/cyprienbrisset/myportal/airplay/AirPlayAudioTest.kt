package com.cyprienbrisset.myportal.airplay

import com.cyprienbrisset.fukkatsunop.airplay.AlacConfig
import com.cyprienbrisset.fukkatsunop.airplay.AlacDecoder
import com.cyprienbrisset.fukkatsunop.airplay.AudioCrypto
import com.cyprienbrisset.fukkatsunop.airplay.AudioStreamSpec
import com.cyprienbrisset.fukkatsunop.airplay.RtpReorder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class AirPlayAudioTest {

    private fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream("airplay/$name")!!.readBytes()

    /**
     * alac_frames.bin: Apple's encoder output (afconvert) of a known 16-bit stereo signal —
     * silence, tones, white noise, full-scale sine. ALAC is lossless: the decoder must give
     * back alac_pcm.raw exactly.
     */
    @Test fun alacDecodesAppleEncoderOutputBitExact() {
        val bin = ByteBuffer.wrap(resource("alac_frames.bin"))
        val cookie = ByteArray(24).also { bin.get(it) }
        val cfg = AlacConfig.fromCookie(cookie)
        assertEquals(16, cfg.bitDepth)
        assertEquals(2, cfg.numChannels)
        val dec = AlacDecoder(cfg)
        val pcm = ShortArray(cfg.frameLength * 2)
        val out = ByteBuffer.allocate(4 * cfg.frameLength * 8).order(ByteOrder.LITTLE_ENDIAN)
        repeat(bin.int) {
            val frame = ByteArray(bin.int).also { bin.get(it) }
            val n = dec.decode(frame, frame.size, pcm)
            assertTrue("frame $it does not decode", n > 0)
            for (i in 0 until n * 2) out.putShort(pcm[i])
        }
        val expected = resource("alac_pcm.raw")
        assertEquals(expected.size, out.position())
        assertArrayEquals(expected, out.array().copyOf(out.position()))
    }

    @Test fun payloadIsCbcDecryptedPerPacketWithClearTail() {
        val key = Random(1).nextBytes(16)
        val iv = Random(2).nextBytes(16)
        val plain = Random(3).nextBytes(16 * 5 + 7)
        val enc = Cipher.getInstance("AES/CBC/NoPadding")
            .apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
            .doFinal(plain, 0, 80) + plain.copyOfRange(80, plain.size)
        val packet = ByteArray(12) + enc
        assertArrayEquals(plain, AudioCrypto.decrypt(key, iv, packet, 12, enc.size))
        // Same key and IV for every packet: decrypting twice gives the same result.
        assertArrayEquals(plain, AudioCrypto.decrypt(key, iv, packet, 12, enc.size))
    }

    @Test fun reorderPutsPacketsBackInSequence() {
        val r = RtpReorder(depth = 4)
        val out = mutableListOf<Int>()
        fun push(seq: Int) = r.push(seq, byteArrayOf(seq.toByte())) { out += it[0].toInt() and 0xFF }
        listOf(10, 12, 11, 13, 13, 9).forEach(::push)
        assertEquals(listOf(10, 11, 12, 13), out)
    }

    @Test fun reorderSkipsALostPacketAndWrapsAround() {
        val r = RtpReorder(depth = 3)
        val out = mutableListOf<Int>()
        fun push(seq: Int) = r.push(seq, byteArrayOf((seq and 0xFF).toByte())) { out += it[0].toInt() and 0xFF }
        push(0xFFFE)
        // 0xFFFF is lost
        push(0x0000); push(0x0001)
        assertEquals(listOf(0xFE), out)
        push(0x0002) // three waiting: give up on 0xFFFF
        assertEquals(listOf(0xFE, 0x00, 0x01, 0x02), out)
    }

    @Test fun streamSpecReadsCodecFromSetup() {
        assertEquals(AudioStreamSpec(2, 352), AudioStreamSpec.from(mapOf("type" to 96L, "ct" to 2L, "spf" to 352L)))
        assertEquals(AudioStreamSpec(8, 480), AudioStreamSpec.from(mapOf("type" to 96L, "ct" to 8L)))
        assertEquals("AAC-ELD", AudioStreamSpec(8, 480).name)
    }
}
