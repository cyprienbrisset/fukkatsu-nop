package com.cyprienbrisset.fukkatsunop.airplay

/**
 * Apple Lossless (ALAC) decoder for 16-bit mono/stereo frames, the format an iPhone or
 * a Mac uses to send music over AirPlay. Android has no ALAC decoder of its own.
 *
 * Port of Apple's reference decoder (ALACDecoder.cpp, ag_dec.c, dp_dec.c, matrix_dec.c,
 * Apache-2.0): adaptive Golomb residuals, then the adaptive linear predictor, then
 * stereo un-mixing.
 */
class AlacConfig(
    val frameLength: Int = 352,
    val bitDepth: Int = 16,
    val pb: Int = 40,
    val mb: Int = 10,
    val kb: Int = 14,
    val numChannels: Int = 2,
    val maxRun: Int = 255,
    val sampleRate: Int = 44100,
) {
    companion object {
        /** ALACSpecificConfig, 24 bytes (the CAF "kuki" / MP4 "alac" cookie). */
        fun fromCookie(c: ByteArray): AlacConfig {
            fun be32(o: Int) = ((c[o].toInt() and 0xFF) shl 24) or ((c[o + 1].toInt() and 0xFF) shl 16) or
                ((c[o + 2].toInt() and 0xFF) shl 8) or (c[o + 3].toInt() and 0xFF)
            fun u8(o: Int) = c[o].toInt() and 0xFF
            return AlacConfig(
                frameLength = be32(0), bitDepth = u8(5), pb = u8(6), mb = u8(7), kb = u8(8),
                numChannels = u8(9), maxRun = ((u8(10) shl 8) or u8(11)), sampleRate = be32(20),
            )
        }
    }
}

class AlacDecoder(private val cfg: AlacConfig) {
    private val n = cfg.frameLength
    private val predictor = IntArray(n)
    private val mixU = IntArray(n)
    private val mixV = IntArray(n)
    private val coefsU = IntArray(32)
    private val coefsV = IntArray(32)

    init {
        require(cfg.bitDepth == 16) { "ALAC: only 16-bit audio is supported (got ${cfg.bitDepth})" }
    }

    /**
     * Decodes one frame into interleaved 16-bit PCM ([out] holds frameLength * channels samples).
     * Returns the number of samples per channel, or -1 when the frame does not parse.
     */
    fun decode(frame: ByteArray, len: Int, out: ShortArray): Int {
        val bits = BitReader(frame, len)
        val channels = cfg.numChannels
        var channelIndex = 0
        var numSamples = n
        try {
            while (true) {
                when (bits.read(3)) {
                    ID_SCE, ID_LFE -> {
                        bits.read(4)
                        if (bits.read(12) != 0) return -1
                        val header = bits.read(4)
                        val partial = header shr 3
                        val bytesShifted = (header shr 1) and 3
                        if (bytesShifted == 3) return -1
                        val escape = header and 1
                        val chanBits = cfg.bitDepth - bytesShifted * 8
                        if (partial != 0) numSamples = (bits.read(16) shl 16) or bits.read(16)
                        if (numSamples > n) return -1
                        if (escape == 0) {
                            bits.read(8); bits.read(8) // mixBits, mixRes: unused for mono
                            val (modeU, denShiftU, pbFactorU, numU) = readPredictorHeader(bits, coefsU)
                            if (bytesShifted != 0) bits.skip(bytesShifted * 8 * numSamples)
                            dynDecomp(bits, predictor, numSamples, chanBits, cfg.pb * pbFactorU / 4)
                            runPredictor(modeU, mixU, numSamples, coefsU, numU, chanBits, denShiftU)
                        } else {
                            val shift = 32 - chanBits
                            for (i in 0 until numSamples) mixU[i] = (bits.read(chanBits) shl shift) shr shift
                        }
                        for (i in 0 until numSamples) out[i * channels + channelIndex] = mixU[i].toShort()
                        channelIndex += 1
                    }
                    ID_CPE -> {
                        if (channelIndex + 2 > channels) return numSamples
                        bits.read(4)
                        if (bits.read(12) != 0) return -1
                        val header = bits.read(4)
                        val partial = header shr 3
                        val bytesShifted = (header shr 1) and 3
                        if (bytesShifted == 3) return -1
                        val escape = header and 1
                        val chanBits = cfg.bitDepth - bytesShifted * 8 + 1
                        if (partial != 0) numSamples = (bits.read(16) shl 16) or bits.read(16)
                        if (numSamples > n) return -1
                        var mixBits = 0
                        var mixRes = 0
                        if (escape == 0) {
                            mixBits = bits.read(8)
                            mixRes = bits.read(8).toByte().toInt()
                            val (modeU, denShiftU, pbFactorU, numU) = readPredictorHeader(bits, coefsU)
                            val (modeV, denShiftV, pbFactorV, numV) = readPredictorHeader(bits, coefsV)
                            if (bytesShifted != 0) bits.skip(bytesShifted * 8 * 2 * numSamples)
                            dynDecomp(bits, predictor, numSamples, chanBits, cfg.pb * pbFactorU / 4)
                            runPredictor(modeU, mixU, numSamples, coefsU, numU, chanBits, denShiftU)
                            dynDecomp(bits, predictor, numSamples, chanBits, cfg.pb * pbFactorV / 4)
                            runPredictor(modeV, mixV, numSamples, coefsV, numV, chanBits, denShiftV)
                        } else {
                            val raw = cfg.bitDepth
                            val shift = 32 - raw
                            for (i in 0 until numSamples) {
                                mixU[i] = (bits.read(raw) shl shift) shr shift
                                mixV[i] = (bits.read(raw) shl shift) shr shift
                            }
                        }
                        unmix16(out, channelIndex, channels, numSamples, mixBits, mixRes)
                        channelIndex += 2
                    }
                    ID_DSE -> {
                        bits.read(4)
                        val align = bits.read(1)
                        var count = bits.read(8)
                        if (count == 255) count += bits.read(8)
                        if (align != 0) bits.byteAlign()
                        bits.skip(count * 8)
                    }
                    ID_FIL -> {
                        var count = bits.read(4)
                        if (count == 15) count += bits.read(8) - 1
                        bits.skip(count * 8)
                    }
                    ID_END -> return numSamples
                    else -> return -1 // CCE and PCE are never produced by Apple encoders
                }
                if (channelIndex >= channels) return numSamples
            }
        } catch (_: IndexOutOfBoundsException) {
            return -1
        }
    }

    private data class PredictorHeader(val mode: Int, val denShift: Int, val pbFactor: Int, val num: Int)

    private fun readPredictorHeader(bits: BitReader, coefs: IntArray): PredictorHeader {
        val a = bits.read(8)
        val b = bits.read(8)
        val num = b and 0x1F
        for (i in 0 until num) coefs[i] = bits.read(16).toShort().toInt()
        return PredictorHeader(a shr 4, a and 0xF, b shr 5, num)
    }

    private fun runPredictor(mode: Int, out: IntArray, num: Int, coefs: IntArray, numActive: Int, chanBits: Int, denShift: Int) {
        if (mode == 0) {
            unpcBlock(predictor, out, num, coefs, numActive, chanBits, denShift)
        } else {
            unpcBlock(predictor, predictor, num, null, 31, chanBits, 0)
            unpcBlock(predictor, out, num, coefs, numActive, chanBits, denShift)
        }
    }

    private fun unmix16(out: ShortArray, offset: Int, stride: Int, num: Int, mixBits: Int, mixRes: Int) {
        var o = offset
        if (mixRes != 0) {
            for (j in 0 until num) {
                val l = mixU[j] + mixV[j] - ((mixRes * mixV[j]) shr mixBits)
                out[o] = l.toShort(); out[o + 1] = (l - mixV[j]).toShort(); o += stride
            }
        } else {
            for (j in 0 until num) { out[o] = mixU[j].toShort(); out[o + 1] = mixV[j].toShort(); o += stride }
        }
    }

    /** Adaptive Golomb residual decoding (ag_dec.c dyn_decomp). */
    private fun dynDecomp(bits: BitReader, pc: IntArray, numSamples: Int, maxSize: Int, pb: Int) {
        val wb = (1 shl cfg.kb) - 1
        var mb = cfg.mb.toLong()
        var zmode = 0
        var c = 0
        while (c < numSamples) {
            var m = (mb ushr QBSHIFT).toInt()
            var k = lg3a(m).coerceAtMost(cfg.kb)
            m = (1 shl k) - 1
            val nv = dynGet32(bits, m, k, maxSize)
            val ndecode = nv + zmode
            val multiplier = (-(ndecode and 1)) or 1
            pc[c++] = ((ndecode + 1) ushr 1) * multiplier
            mb = (pb.toLong() * (nv + zmode) + mb - ((pb.toLong() * mb) ushr QBSHIFT)) and 0xFFFFFFFFL
            if ((nv.toLong() and 0xFFFFFFFFL) > N_MAX_MEAN_CLAMP) mb = N_MEAN_CLAMP_VAL
            zmode = 0
            if (((mb shl MMULSHIFT) and 0xFFFFFFFFL) < QB && c < numSamples) {
                zmode = 1
                k = Integer.numberOfLeadingZeros(mb.toInt()) - BITOFF + ((mb.toInt() + MOFF) shr MDENSHIFT)
                val mz = ((1 shl k) - 1) and wb
                val run = dynGet(bits, mz, k)
                if (c + run > numSamples) throw IndexOutOfBoundsException("ALAC zero run")
                repeat(run) { pc[c++] = 0 }
                if (run >= 65535) zmode = 0
                mb = 0
            }
        }
    }

    private fun dynGet(bits: BitReader, m: Int, k: Int): Int {
        val word = bits.peek32()
        val pre = Integer.numberOfLeadingZeros(word.inv())
        return if (pre >= MAX_PREFIX_16) {
            bits.skip(MAX_PREFIX_16)
            bits.read(MAX_DATATYPE_BITS_16)
        } else {
            val v = (word shl (pre + 1)) ushr (32 - k)
            bits.skip(pre + 1 + k)
            var result = pre * m + v - 1
            if (v < 2) { result -= v - 1; bits.skip(-1) }
            result
        }
    }

    private fun dynGet32(bits: BitReader, m: Int, k: Int, maxBits: Int): Int {
        val word = bits.peek32()
        var result = Integer.numberOfLeadingZeros(word.inv())
        if (result >= MAX_PREFIX_32) {
            bits.skip(MAX_PREFIX_32)
            return bits.read(maxBits)
        }
        bits.skip(result + 1)
        if (k != 1) {
            val v = (word shl (result + 1)) ushr (32 - k)
            bits.skip(k - 1)
            result *= m
            if (v >= 2) { result += v - 1; bits.skip(1) }
        }
        return result
    }

    companion object {
        private const val ID_SCE = 0
        private const val ID_CPE = 1
        private const val ID_LFE = 3
        private const val ID_DSE = 4
        private const val ID_FIL = 6
        private const val ID_END = 7

        private const val QBSHIFT = 9
        private const val QB = 1L shl QBSHIFT
        private const val MMULSHIFT = 2
        private const val MDENSHIFT = QBSHIFT - MMULSHIFT - 1
        private const val MOFF = 1 shl (MDENSHIFT - 2)
        private const val BITOFF = 24
        private const val MAX_PREFIX_16 = 9
        private const val MAX_PREFIX_32 = 9
        private const val MAX_DATATYPE_BITS_16 = 16
        private const val N_MAX_MEAN_CLAMP = 0xFFFFL
        private const val N_MEAN_CLAMP_VAL = 0xFFFFL

        private fun lg3a(x: Int) = 31 - Integer.numberOfLeadingZeros(x + 3)
        private fun signOf(i: Int) = if (i > 0) 1 else if (i < 0) -1 else 0

        /** Adaptive linear predictor (dp_dec.c unpc_block); [coefs] adapts in place. */
        fun unpcBlock(pc1: IntArray, out: IntArray, num: Int, coefs: IntArray?, numActive: Int, chanBits: Int, denShift: Int) {
            val chanShift = 32 - chanBits
            val denHalf = if (denShift == 0) 0 else 1 shl (denShift - 1)
            out[0] = pc1[0]
            if (numActive == 0) {
                if (num > 1 && pc1 !== out) System.arraycopy(pc1, 1, out, 1, num - 1)
                return
            }
            if (numActive == 31) {
                var prev = out[0]
                for (j in 1 until num) {
                    val del = pc1[j] + prev
                    prev = (del shl chanShift) shr chanShift
                    out[j] = prev
                }
                return
            }
            coefs!!
            for (j in 1..numActive.coerceAtMost(num - 1)) {
                val del = pc1[j] + out[j - 1]
                out[j] = (del shl chanShift) shr chanShift
            }
            val lim = numActive + 1
            for (j in lim until num) {
                var sum1 = 0
                val p = j - 1
                val top = out[j - lim]
                for (k in 0 until numActive) sum1 += coefs[k] * (out[p - k] - top)
                var del = pc1[j]
                var del0 = del
                val sg = signOf(del)
                del += top + ((sum1 + denHalf) shr denShift)
                out[j] = (del shl chanShift) shr chanShift
                if (sg > 0) {
                    for (k in numActive - 1 downTo 0) {
                        val dd = top - out[p - k]
                        val sgn = signOf(dd)
                        coefs[k] = (coefs[k] - sgn).toShort().toInt()
                        del0 -= (numActive - k) * ((sgn * dd) shr denShift)
                        if (del0 <= 0) break
                    }
                } else if (sg < 0) {
                    for (k in numActive - 1 downTo 0) {
                        val dd = top - out[p - k]
                        val sgn = signOf(dd)
                        coefs[k] = (coefs[k] + sgn).toShort().toInt()
                        del0 -= (numActive - k) * ((-sgn * dd) shr denShift)
                        if (del0 >= 0) break
                    }
                }
            }
        }
    }
}

/** Big-endian bit reader; reads past the end return zeros, like the reference decoder's padding. */
private class BitReader(private val data: ByteArray, private val len: Int) {
    var pos = 0

    private fun byteAt(i: Int): Int {
        if (i > len + 8) throw IndexOutOfBoundsException("ALAC frame overrun")
        return if (i < len) data[i].toInt() and 0xFF else 0
    }

    /** 32 bits starting at the current position (MSB first). */
    fun peek32(): Int {
        val b = pos ushr 3
        val v = (byteAt(b).toLong() shl 32) or (byteAt(b + 1).toLong() shl 24) or (byteAt(b + 2).toLong() shl 16) or
            (byteAt(b + 3).toLong() shl 8) or byteAt(b + 4).toLong()
        return (v ushr (8 - (pos and 7))).toInt()
    }

    fun read(nBits: Int): Int {
        if (nBits == 0) return 0
        val v = peek32() ushr (32 - nBits)
        pos += nBits
        return v
    }

    fun skip(nBits: Int) { pos += nBits }

    fun byteAlign() { pos = (pos + 7) and 7.inv() }
}
