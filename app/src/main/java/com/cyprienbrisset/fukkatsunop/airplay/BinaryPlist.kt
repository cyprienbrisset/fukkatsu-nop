package com.cyprienbrisset.fukkatsunop.airplay

/**
 * Minimal Apple binary plist (bplist00) encoder/decoder for AirPlay RTSP SETUP negotiation.
 *
 * Decoder: supports Long, String (ASCII + UTF-16), ByteArray, List, Map<String,*>.
 * Encoder: supports Int/Long, String, ByteArray, List<*>, Map<String,*>.
 *
 * Extended counts (low nibble == 0xF) are handled in both directions.
 */
object BinaryPlist {

    // ── Decoder ──────────────────────────────────────────────────────────

    /** Returns Map<String, Any?> where Any? is Long, String, ByteArray, List<Any?>, Map<String, Any?>. */
    fun decode(data: ByteArray): Map<String, Any?> {
        if (data.size < 40) return emptyMap()
        if (!data.copyOfRange(0, 8).contentEquals("bplist00".toByteArray())) return emptyMap()

        val t          = data.copyOfRange(data.size - 32, data.size)
        val offsetSize = t[6].toInt() and 0xFF
        val refSize    = t[7].toInt() and 0xFF
        val numObjects = readLong(t, 8).toInt()
        val topObject  = readLong(t, 16).toInt()
        val offTbl     = readLong(t, 24).toInt()

        if (offsetSize == 0 || refSize == 0 || numObjects <= 0) return emptyMap()

        val offsets = IntArray(numObjects) { i -> readInt(data, offTbl + i * offsetSize, offsetSize) }

        fun parse(objIdx: Int): Any? {
            if (objIdx < 0 || objIdx >= numObjects) return null
            var p      = offsets[objIdx]
            val marker = data[p++].toInt() and 0xFF
            val tag    = marker shr 4
            val nibble = marker and 0xF

            return when (tag) {
                0x0 -> when (marker) { 0x08 -> false; 0x09 -> true; else -> null }

                0x1 -> readLong(data, p, 1 shl nibble)  // int: nibble = log2(byteCount)

                0x4, 0x5, 0x6, 0xA, 0xD -> {
                    // types with count/length; extended when nibble == 0xF
                    val count: Int
                    if (nibble == 0xF) {
                        val im = data[p].toInt() and 0xFF
                        val ib = 1 shl (im and 0xF)
                        count  = readInt(data, p + 1, ib)
                        p     += 1 + ib
                    } else {
                        count = nibble
                    }
                    when (tag) {
                        0x4 -> data.copyOfRange(p, p + count)
                        0x5 -> data.copyOfRange(p, p + count).toString(Charsets.US_ASCII)
                        0x6 -> data.copyOfRange(p, p + count * 2).toString(Charsets.UTF_16BE)
                        0xA -> (0 until count).map { i -> parse(readInt(data, p + i * refSize, refSize)) }
                        0xD -> {
                            val m = mutableMapOf<String, Any?>()
                            for (i in 0 until count) {
                                val k = parse(readInt(data, p + i * refSize, refSize)) as? String ?: continue
                                m[k] = parse(readInt(data, p + (count + i) * refSize, refSize))
                            }
                            m
                        }
                        else -> null
                    }
                }
                else -> null
            }
        }

        @Suppress("UNCHECKED_CAST")
        return parse(topObject) as? Map<String, Any?> ?: emptyMap()
    }

    // ── Encoder ──────────────────────────────────────────────────────────

    fun encode(root: Map<String, *>): ByteArray {
        val enc = Encoder()
        enc.add(root)
        return enc.build()
    }

    // ── Shared helpers ───────────────────────────────────────────────────

    private fun readLong(data: ByteArray, off: Int, size: Int = 8): Long {
        var v = 0L
        for (i in 0 until size) v = (v shl 8) or (data[off + i].toLong() and 0xFF)
        return v
    }

    private fun readInt(data: ByteArray, off: Int, size: Int): Int {
        var v = 0
        for (i in 0 until size) v = (v shl 8) or (data[off + i].toInt() and 0xFF)
        return v
    }

    // ── Encoder internals ─────────────────────────────────────────────────

    private sealed class PObj {
        class PStr(val v: String)               : PObj()
        class PInt(val v: Long)                 : PObj()
        class PDouble(val v: Double)            : PObj()
        class PBool(val v: Boolean)             : PObj()
        class PData(val v: ByteArray)           : PObj()
        class PArray(val refs: List<Int>)       : PObj()
        class PDict(val kR: List<Int>, val vR: List<Int>) : PObj()
    }

    private class Encoder {
        val objects = mutableListOf<PObj>()

        fun add(value: Any?): Int = when (value) {
            is String    -> { val i = objects.size; objects.add(PObj.PStr(value)); i }
            is Long      -> { val i = objects.size; objects.add(PObj.PInt(value)); i }
            is Int       -> { val i = objects.size; objects.add(PObj.PInt(value.toLong())); i }
            is Double    -> { val i = objects.size; objects.add(PObj.PDouble(value)); i }
            is Float     -> { val i = objects.size; objects.add(PObj.PDouble(value.toDouble())); i }
            is Boolean   -> { val i = objects.size; objects.add(PObj.PBool(value)); i }
            is ByteArray -> { val i = objects.size; objects.add(PObj.PData(value)); i }
            is Map<*, *> -> {
                val idx = objects.size
                objects.add(PObj.PStr("__placeholder__"))
                val kR = mutableListOf<Int>()
                val vR = mutableListOf<Int>()
                value.entries.forEach { (k, v) -> kR.add(add(k)); vR.add(add(v)) }
                objects[idx] = PObj.PDict(kR, vR)
                idx
            }
            is List<*> -> {
                val idx = objects.size
                objects.add(PObj.PStr("__placeholder__"))
                val refs = mutableListOf<Int>()
                value.forEach { refs.add(add(it)) }
                objects[idx] = PObj.PArray(refs)
                idx
            }
            else -> throw IllegalArgumentException("BinaryPlist.encode: unsupported ${value?.javaClass}")
        }

        fun build(): ByteArray {
            val n       = objects.size
            val refSize = if (n <= 256) 1 else 2

            val encoded = Array(n) { i -> encodeObj(objects[i], refSize) }

            var bytePos = 8L
            val offsets = LongArray(n) { i -> bytePos.also { bytePos += encoded[i].size } }
            val offTbl  = bytePos

            val offSize = when { offTbl <= 0xFF -> 1; offTbl <= 0xFFFF -> 2; else -> 4 }

            val totalObjBytes = encoded.sumOf { it.size }
            val objData = ByteArray(totalObjBytes).also { out ->
                var p = 0; encoded.forEach { b -> b.copyInto(out, p); p += b.size }
            }

            val offsetTable = ByteArray(n * offSize)
            for (i in 0 until n) {
                val o = offsets[i]
                for (j in 0 until offSize) offsetTable[i * offSize + j] = ((o shr ((offSize - 1 - j) * 8)) and 0xFF).toByte()
            }

            val trailer = ByteArray(32)
            trailer[6] = offSize.toByte()
            trailer[7] = refSize.toByte()
            fun wl(arr: ByteArray, start: Int, v: Long) { for (k in 0..7) arr[start + k] = ((v shr (56 - k * 8)) and 0xFF).toByte() }
            wl(trailer, 8, n.toLong())
            wl(trailer, 16, 0L)
            wl(trailer, 24, offTbl)

            return "bplist00".toByteArray() + objData + offsetTable + trailer
        }

        private fun encodeObj(obj: PObj, refSize: Int): ByteArray = when (obj) {
            is PObj.PBool -> byteArrayOf(if (obj.v) 0x09 else 0x08)
            is PObj.PDouble -> {
                val bits = java.lang.Double.doubleToLongBits(obj.v)
                byteArrayOf(0x23) + ByteArray(8) { k -> ((bits shr (56 - k * 8)) and 0xFF).toByte() }
            }
            is PObj.PStr -> {
                val b = obj.v.toByteArray(Charsets.US_ASCII)
                val m = if (b.size <= 14) byteArrayOf((0x50 or b.size).toByte())
                        else byteArrayOf(0x5F) + inlineInt(b.size.toLong())
                m + b
            }
            is PObj.PInt -> {
                val v = obj.v
                when {
                    v in 0L..0xFFL       -> byteArrayOf(0x10, v.toByte())
                    v in 0L..0xFFFFL     -> byteArrayOf(0x11, (v shr 8).toByte(), v.toByte())
                    v in 0L..0xFFFFFFFFL -> byteArrayOf(0x12, (v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
                    else                 -> byteArrayOf(0x13) + ByteArray(8) { k -> ((v shr (56 - k * 8)) and 0xFF).toByte() }
                }
            }
            is PObj.PData -> {
                val b = obj.v
                val m = if (b.size <= 14) byteArrayOf((0x40 or b.size).toByte())
                        else byteArrayOf(0x4F) + inlineInt(b.size.toLong())
                m + b
            }
            is PObj.PArray -> {
                val cnt = obj.refs.size
                val m = if (cnt <= 14) byteArrayOf((0xA0 or cnt).toByte())
                        else byteArrayOf(0xAF.toByte()) + inlineInt(cnt.toLong())
                m + refsBytes(obj.refs, refSize)
            }
            is PObj.PDict -> {
                val cnt = obj.kR.size
                val m = if (cnt <= 14) byteArrayOf((0xD0 or cnt).toByte())
                        else byteArrayOf(0xDF.toByte()) + inlineInt(cnt.toLong())
                m + refsBytes(obj.kR + obj.vR, refSize)
            }
        }

        private fun inlineInt(v: Long): ByteArray = when {
            v <= 0xFF   -> byteArrayOf(0x10, v.toByte())
            v <= 0xFFFF -> byteArrayOf(0x11, (v shr 8).toByte(), v.toByte())
            else        -> byteArrayOf(0x12, (v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        }

        private fun refsBytes(refs: List<Int>, refSize: Int): ByteArray {
            val out = ByteArray(refs.size * refSize)
            refs.forEachIndexed { i, ref ->
                for (j in 0 until refSize) out[i * refSize + j] = ((ref shr ((refSize - 1 - j) * 8)) and 0xFF).toByte()
            }
            return out
        }
    }
}
