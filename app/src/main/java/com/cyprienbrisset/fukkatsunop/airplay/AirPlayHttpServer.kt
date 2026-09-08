package com.cyprienbrisset.fukkatsunop.airplay

import android.util.Base64
import android.util.Log
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

private val BPLIST_MAGIC = "bplist00".toByteArray().toList()

private const val TAG = "AirPlayServer"

private const val W = 1920
private const val H = 1080

// Kept for AirPlay 1 / RtpVideoReceiver compatibility
data class AirPlaySession(val aesKey: ByteArray, val aesIv: ByteArray, val rtpPort: Int)

class AirPlayHttpServer(
    private val onConnecting: () -> Unit,
    private val onSession: (isExtended: Boolean) -> Unit,
    private val onDisconnect: () -> Unit,
    private val onVideoNal: (ByteArray) -> Unit = {},
    private val onReconfigureCsd: (sps: ByteArray, pps: ByteArray) -> Unit = { _, _ -> },
) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    fun start() {
        running = true
        Thread({
            // SO_REUSEADDR pour survivre aux TIME_WAIT après restart
            val ss = java.net.ServerSocket()
            ss.reuseAddress = true
            var bound = false
            repeat(8) {
                if (bound) return@repeat
                runCatching { ss.bind(java.net.InetSocketAddress(7000)) }.onSuccess { bound = true }
                    .onFailure { Log.w(TAG, "Port 7000 busy, retry in 1s: ${it.message}"); Thread.sleep(1_000) }
            }
            if (!bound) { Log.e(TAG, "Failed to bind port 7000 — giving up"); return@Thread }
            serverSocket = ss
            Log.d(TAG, "AirPlay HTTP server listening on :7000")
            while (running) {
                runCatching {
                    val client = ss.accept()
                    Thread({ handleClient(client) }, "airplay-client").start()
                }
            }
        }, "airplay-tcp").start()
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
    }

    private fun handleClient(socket: Socket) {
        Log.d(TAG, "Client CONNECTED: ${socket.remoteSocketAddress}")
        socket.use {
            val inp = socket.getInputStream()
            val out = socket.getOutputStream()
            val macAddress = (socket.remoteSocketAddress as? java.net.InetSocketAddress)?.address
            val timingLportRef = java.util.concurrent.atomic.AtomicInteger(0)

            // Per-session pair-verify state (pair_fruit / RAOP binary protocol)
            var pairVerifySharedSecret: ByteArray? = null
            var pairVerifyKeystream: ByteArray? = null        // 128 bytes: [0:64]=M2, [64:128]=M3
            var pairVerifyClientX25519Pub: ByteArray? = null  // from M1[4:36]
            var pairVerifyClientLtEdPub: ByteArray? = null    // from M1[36:68]
            var pairVerifyServerX25519Pub: ByteArray? = null  // generated at M1

            var cseq = "0"
            val isExtendedRef = java.util.concurrent.atomic.AtomicBoolean(false)

            while (running && !socket.isClosed) {
                val requestLine = inp.readHttpLine() ?: run {
                    Log.d(TAG, "Client DISCONNECTED: ${socket.remoteSocketAddress}")
                    break
                }
                if (requestLine.isEmpty()) continue
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = inp.readHttpLine() ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                }
                cseq = headers["CSeq"] ?: cseq
                val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
                val bodyBytes = if (contentLength > 0) inp.readExact(contentLength) else ByteArray(0)

                val method = requestLine.substringBefore(' ')
                val path   = requestLine.substringAfter(' ').substringBefore(' ')
                Log.d(TAG, ">>> $method $path | body[${bodyBytes.size}]: ${bodyBytes.take(16).toHex()}")
                headers.forEach { (k, v) -> Log.d(TAG, "  hdr $k: $v") }

                when {
                    method == "GET" && (path == "/info" || path == "/server-info") -> {
                        if (bodyBytes.isNotEmpty()) {
                            val ascii = bodyBytes.toString(Charsets.ISO_8859_1).replace(Regex("[\\x00-\\x08\\x0E-\\x1F\\x7F]"), ".")
                            Log.d(TAG, "GET /info body[${bodyBytes.size}] hex=${bodyBytes.take(32).toHex()} txt=$ascii")
                        }
                        val pkB64 = Base64.encodeToString(AirPlayPairing.edPublicKeyBytes, Base64.NO_WRAP)
                        val plist = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>deviceid</key><string>${AirPlayPairing.deviceId}</string>
<key>features</key><string>0x5A7FFFF7,0x3E</string>
<key>model</key><string>AppleTV6,2</string>
<key>srcvers</key><string>550.10</string>
<key>vv</key><integer>2</integer>
<key>pk</key><data>$pkB64</data>
<key>pi</key><string>${AirPlayPairing.deviceId}</string>
<key>protovers</key><string>1.1</string>
<key>statusFlags</key><integer>4</integer>
<key>displays</key><array><dict>
<key>UUID</key><string>e5f7a68d-7b0f-4305-a332-e3cfd84b0a8e</string>
<key>features</key><integer>14</integer>
<key>width</key><integer>$W</integer>
<key>height</key><integer>$H</integer>
<key>widthPixels</key><integer>$W</integer>
<key>heightPixels</key><integer>$H</integer>
<key>widthPhysical</key><integer>0</integer>
<key>heightPhysical</key><integer>0</integer>
<key>rotation</key><false/>
<key>refreshRate</key><real>0.016666666666666666</real>
<key>maxFPS</key><integer>60</integer>
<key>overscanned</key><false/>
</dict></array>
</dict></plist>""".toByteArray(Charsets.UTF_8)
                        Log.d(TAG, "GET /info")
                        out.writeHttp("HTTP/1.1 200 OK", "text/x-apple-plist+xml", plist)
                    }

                    method == "POST" && path == "/pair-setup" -> {
                        // macOS sends 32 bytes = client Ed25519 long-term public key.
                        // We respond avec notre clé Ed25519 publique (32 bytes).
                        pairVerifySharedSecret = null
                        pairVerifyKeystream = null
                        pairVerifyClientX25519Pub = null
                        pairVerifyClientLtEdPub = null
                        pairVerifyServerX25519Pub = null
                        val serverEdPub = AirPlayPairing.edPublicKeyBytes
                        Log.d(TAG, "pair-setup: client_pk=${bodyBytes.toHex()} server_pk=${serverEdPub.toHex()}")
                        out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", serverEdPub)
                    }

                    method == "POST" && path == "/pair-verify" -> {
                        if (pairVerifySharedSecret == null) {
                            // M1 (68 bytes): [0x01,0x00,0x00,0x00] + client_eph_x25519(32) + client_lt_ed25519(32)
                            val clientX25519 = bodyBytes.copyOfRange(4, 36)
                            val clientLtEd   = if (bodyBytes.size >= 68) bodyBytes.copyOfRange(36, 68) else ByteArray(32)
                            Log.d(TAG, "pair-verify M1[${bodyBytes.size}]: client_x25519=${clientX25519.toHex()} client_lt=${clientLtEd.toHex()}")

                            val serverX25519Pair = AirPlayPairing.generateX25519Pair()
                            val serverX25519Priv = serverX25519Pair.private as X25519PrivateKeyParameters
                            val serverX25519Pub  = (serverX25519Pair.public as X25519PublicKeyParameters).encoded

                            val sharedSecret = AirPlayPairing.x25519(serverX25519Priv, X25519PublicKeyParameters(clientX25519, 0))
                            pairVerifySharedSecret = sharedSecret
                            pairVerifyClientX25519Pub = clientX25519
                            pairVerifyClientLtEdPub   = clientLtEd
                            pairVerifyServerX25519Pub = serverX25519Pub

                            // Derive AES-128-CTR key/IV: SHA512(label || shared_secret)[0:16]
                            val aesKey = AirPlayPairing.sha512Concat("Pair-Verify-AES-Key".toByteArray(), sharedSecret).copyOf(16)
                            val aesIv  = AirPlayPairing.sha512Concat("Pair-Verify-AES-IV".toByteArray(),  sharedSecret).copyOf(16)

                            // Pre-compute 128-byte keystream: [0:64] = M2, [64:128] = M3 (UxPlay "fake round").
                            // Client uses blocks 0-3 to decrypt M2, then blocks 4-7 to encrypt M3.
                            val keystream = AirPlayPairing.aesCtr128(aesKey, aesIv, ByteArray(128))
                            pairVerifyKeystream = keystream

                            // Sign server_x25519 || client_x25519 with our long-term Ed25519 key
                            val signMsg   = serverX25519Pub + clientX25519
                            val signature = AirPlayPairing.edSign(signMsg) // 64 bytes
                            // Encrypt with keystream[0:64] (CTR blocks 0-3)
                            val encSig = ByteArray(64) { i -> (signature[i].toInt() xor keystream[i].toInt()).toByte() }

                            // M2 (96 bytes): server_eph_x25519(32) + AES-CTR-encrypted-Ed25519-signature(64)
                            val response = serverX25519Pub + encSig
                            Log.d(TAG, "pair-verify M2[${response.size}]: server_x25519=${serverX25519Pub.toHex()}")
                            Log.d(TAG, "pair-verify M2: shared=${sharedSecret.toHex()} sig=${signature.take(8).toHex()}...")
                            out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", response)
                        } else {
                            // M3 (68 bytes): [0x00,0x00,0x00,0x00] + AES-CTR-encrypted-client-signature(64)
                            // Client encrypted M3 with keystream[64:128] (CTR blocks 4-7, after "consuming" 0-3 for M2)
                            Log.d(TAG, "pair-verify M3[${bodyBytes.size}]: ${bodyBytes.toHex()}")
                            val ks = pairVerifyKeystream!!
                            val encrypted = if (bodyBytes.size >= 68) bodyBytes.copyOfRange(4, 68) else ByteArray(64)
                            // Decrypt with keystream[64:128]
                            val clientSig = ByteArray(64) { i -> (encrypted[i].toInt() xor ks[64 + i].toInt()).toByte() }

                            // Verify: client signed client_x25519 || server_x25519 with its long-term Ed25519 key
                            val verifyMsg = pairVerifyClientX25519Pub!! + pairVerifyServerX25519Pub!!
                            val ok = AirPlayPairing.edVerifyWith(verifyMsg, clientSig, pairVerifyClientLtEdPub!!)
                            Log.d(TAG, "pair-verify M3: clientSig=${clientSig.take(8).toHex()}... ok=$ok")
                            // M4: client ignores content
                            out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", ByteArray(0))
                            onConnecting()
                        }
                    }

                    method == "OPTIONS" -> {
                        out.writeRtsp(cseq, mapOf(
                            "Public" to "ANNOUNCE, SETUP, RECORD, TEARDOWN, OPTIONS, GET_PARAMETER, SET_PARAMETER",
                        ))
                    }

                    method == "SETUP" -> {
                        Log.d(TAG, "SETUP body[${bodyBytes.size}]=${bodyBytes.take(8).toHex()}")
                        if (bodyBytes.size > 8 && bodyBytes.take(8) == BPLIST_MAGIC) {
                            handleAirPlay2Setup(out, cseq, bodyBytes, macAddress, timingLportRef, isExtendedRef, onVideoNal, onReconfigureCsd) { isExtended ->
                                onSession(isExtended)
                            }
                        } else {
                            // AirPlay 1 legacy
                            val clientPort = headers["Transport"]
                                ?.let { Regex("client_port=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
                            out.writeRtsp(cseq, mapOf("Session" to "1",
                                "Transport" to "RTP/AVP/UDP;unicast;client_port=$clientPort;server_port=6001"))
                        }
                    }

                    method == "GET_PARAMETER" -> {
                        val reqBody = bodyBytes.toString(Charsets.UTF_8).trim()
                        Log.d(TAG, "GET_PARAMETER: $reqBody")
                        val respBody = when {
                            reqBody.contains("volume") -> "volume: 0.000000\r\n"
                            else -> ""
                        }
                        out.writeRtspWithBody(cseq, "text/parameters", respBody.toByteArray())
                    }

                    method == "SET_PARAMETER" -> {
                        Log.d(TAG, "SET_PARAMETER body[${bodyBytes.size}]")
                        out.writeRtsp(cseq)
                    }

                    method == "RECORD" -> {
                        Log.d(TAG, "RECORD")
                        out.writeRtsp(cseq, mapOf(
                            "Server" to "AirTunes/550.10",
                            "Session" to "BABE0001;timeout=90",
                            "Audio-Latency" to "11025",
                            "Audio-Jack-Status" to "connected; type=analog",
                        ))
                    }

                    method == "TEARDOWN" -> {
                        val tdAscii = bodyBytes.toString(Charsets.ISO_8859_1).replace(Regex("[\\x00-\\x08\\x0E-\\x1F\\x7F]"), ".")
                        Log.d(TAG, "TEARDOWN body[${bodyBytes.size}] hex=${bodyBytes.toHex()} txt=$tdAscii")
                        out.writeRtsp(cseq)
                        onDisconnect()
                        break
                    }

                    method == "ANNOUNCE" -> {
                        out.writeRtsp(cseq)
                    }

                    method == "POST" && path == "/fp-setup" -> {
                        Log.d(TAG, "fp-setup: body[${bodyBytes.size}]=${bodyBytes.take(16).toHex()}")
                        val fpResponse = when (bodyBytes.size) {
                            16 -> {
                                // Phase 1: respond with 142-byte static blob indexed by req[14] (mode 0-3)
                                val mode = bodyBytes.getOrElse(14) { 0 }.toInt() and 0xFF
                                Log.d(TAG, "fp-setup phase1 mode=$mode")
                                FairPlay.setupReply(mode)
                            }
                            164 -> {
                                // Phase 2: 12-byte header + last 20 bytes of request echoed
                                Log.d(TAG, "fp-setup phase2")
                                FairPlay.handshakeReply(bodyBytes)
                            }
                            else -> {
                                Log.w(TAG, "fp-setup: unexpected body size ${bodyBytes.size}")
                                ByteArray(0)
                            }
                        }
                        out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", fpResponse)
                    }

                    else -> {
                        Log.w(TAG, "Unhandled: $method $path body[${bodyBytes.size}]=${bodyBytes.toHex()}")
                        out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        out.flush()
                    }
                }
            }
        }
    }
}

// ---- AirPlay 2 RTSP SETUP (binary plist) ----
//
// combinedGetInfoWithControlSetup=true (macOS 27) = ONE SETUP containing streams.
// Previous hypothesis of two-SETUP was wrong: macOS never sends a second SETUP.
// The real prior failure was video TCP bound IPv4-only; macOS connects via IPv6.
// Both branches handled: if "streams" already in plist → second SETUP fallback.

private fun handleAirPlay2Setup(
    out: OutputStream, cseq: String, bodyBytes: ByteArray,
    macAddress: java.net.InetAddress?,
    timingLportRef: java.util.concurrent.atomic.AtomicInteger,
    isExtendedRef: java.util.concurrent.atomic.AtomicBoolean,
    onVideoNal: (ByteArray) -> Unit,
    onReconfigureCsd: (ByteArray, ByteArray) -> Unit,
    onStreamsReady: (isExtended: Boolean) -> Unit = {},
) {
    runCatching {
        val plist = BinaryPlist.decode(bodyBytes)
        Log.d(TAG, "SETUP plist keys: ${plist.keys}")

        if ("streams" in plist) {
            // ── Fallback: unexpected second SETUP with streams ─────────────────
            @Suppress("UNCHECKED_CAST")
            val streams = plist["streams"] as? List<Map<String, Any?>> ?: emptyList()
            Log.d(TAG, "SETUP #2 (fallback) streams[${streams.size}]")
            val responseStreams = streams.mapIndexed { i, stream ->
                val type = (stream["type"] as? Long)?.toInt() ?: 0
                val connId = stream["streamConnectionID"]
                Log.d(TAG, "  stream[$i] type=$type connId=$connId")
                when (type) {
                    110 -> mapOf("type" to type.toLong(), "dataPort" to openVideoTcpServer(onVideoNal, onReconfigureCsd).toLong())
                    96, 103 -> {
                        val d = openAudioUdpServer(); val c = openAudioUdpServer()
                        mapOf("type" to type.toLong(), "dataPort" to d.toLong(), "controlPort" to c.toLong())
                    }
                    else -> mapOf("type" to type.toLong())
                }
            }
            val rb = BinaryPlist.encode(mapOf("streams" to responseStreams))
            out.writeRtspWithBody(cseq, "application/x-apple-binary-plist", rb)
            onStreamsReady(isExtendedRef.get())

        } else {
            // ── SETUP (combinedGetInfoWithControlSetup) ───────────────────────
            // macOS sends ONE SETUP; expects timingPort + streams in one response.
            listOf("combinedGetInfoWithControlSetup", "timingProtocol", "timingPort", "et",
                   "eiv", "ekey", "isScreenMirroringSession", "osVersion", "sourceVersion",
                   "macAddress", "sessionUUID").forEach { k ->
                val v = plist[k]
                val s = when (v) { is ByteArray -> "data[${v.size}]"; is List<*> -> "list"; is Map<*,*> -> "dict"; else -> v?.toString() ?: "null" }
                Log.d(TAG, "  SETUP[$k]=$s")
            }

            val isMirror = plist["isScreenMirroringSession"] as? Boolean ?: true
            isExtendedRef.set(!isMirror)
            Log.d(TAG, "SETUP: isMirror=$isMirror isExtended=${!isMirror}")

            val macTimingPort = (plist["timingPort"] as? Long)?.toInt() ?: 0
            val timingLport = openRaopTimingClient(macAddress, macTimingPort)
            timingLportRef.set(timingLport)

            // Allocate video port only. Video TCP must bind IPv6 (macOS connects via IPv6).
            val videoPort = openVideoTcpServer(onVideoNal, onReconfigureCsd)

            val responseMap = mutableMapOf<String, Any>(
                "timingPort" to timingLport.toLong(),
                "eventPort" to 0L,
                "statusFlags" to 4L,
                "streams" to listOf(
                    mapOf("type" to 110L, "dataPort" to videoPort.toLong()),
                ),
            )
            if (!isMirror) {
                responseMap["displays"] = listOf(mapOf(
                    "uuid" to "e5f7a68d-7b0f-4305-a332-e3cfd84b0a8e",
                    "widthPhysical" to 0L, "heightPhysical" to 0L,
                    "width" to W.toLong(), "height" to H.toLong(),
                    "widthPixels" to W.toLong(), "heightPixels" to H.toLong(),
                    "rotation" to false, "refreshRate" to (1.0 / 60.0),
                    "maxFPS" to 60L, "overscanned" to false, "features" to 14L,
                ))
            }
            val responseBytes = BinaryPlist.encode(responseMap)
            Log.d(TAG, "SETUP: timingLport=$timingLport videoTcp=$videoPort bytes=${responseBytes.size}")
            Log.d(TAG, "SETUP response hex: ${responseBytes.toHex()}")
            out.writeRtspWithBody(cseq, "application/x-apple-binary-plist", responseBytes,
                mapOf("Server" to "AirTunes/550.10", "Session" to "BABE0001;timeout=90"))
            onStreamsReady(isExtendedRef.get())
        }
    }.onFailure { e ->
        Log.e(TAG, "SETUP error: ${e.message}", e)
        out.writeRtsp(cseq)
    }
}

// Apple RAOP timing client — we send 32-byte timing packets TO macOS:macTimingPort (UxPlay style)
// macOS is the NTP server; we are the client. timingLport = our local UDP port.
private fun openRaopTimingClient(macAddress: java.net.InetAddress?, macTimingPort: Int): Int {
    if (macAddress == null || macTimingPort == 0) return 0
    return runCatching {
        val sock = java.net.DatagramSocket(null)
        sock.bind(java.net.InetSocketAddress("::", 0))
        val timingLport = sock.localPort
        Log.d(TAG, "RAOP timing client: bound port $timingLport, sending to $macAddress:$macTimingPort")
        Thread({
            // Apple RAOP timing request: 32 bytes
            // [0] 0x80 [1] 0xd2 [2-3] 0x0007 [4-7] 0 [8-15] client_ref_time [16-23] recv_ntp [24-31] send_ntp
            val req = ByteArray(32).apply {
                this[0] = 0x80.toByte()
                this[1] = 0xd2.toByte()
                this[3] = 0x07
            }
            var prevRecvMs = 0L
            var clientRefRaw = ByteArray(8)  // raw bytes from response[24:32]
            sock.soTimeout = 300
            while (true) {
                // Fill send timestamp at offset 24
                ntpPutTs(req, 24, System.currentTimeMillis())
                if (prevRecvMs != 0L) {
                    // client_ref_time at offset 8 (from last response[24:32])
                    clientRefRaw.copyInto(req, 8)
                    // recv_time at offset 16 (when we received last response)
                    ntpPutTs(req, 16, prevRecvMs)
                }
                runCatching {
                    sock.send(java.net.DatagramPacket(req.copyOf(), 32, macAddress, macTimingPort))
                    val respBuf = ByteArray(32)
                    val respPkt = java.net.DatagramPacket(respBuf, 32)
                    sock.receive(respPkt)
                    prevRecvMs = System.currentTimeMillis()
                    respBuf.copyInto(clientRefRaw, 0, 24, 32)
                    Log.d(TAG, "RAOP NTP: timing exchange ok with ${respPkt.address}")
                }.onFailure { Log.d(TAG, "RAOP NTP: no response (${it.message})") }
                Thread.sleep(3000)
            }
        }, "airplay-ntp-client").apply { isDaemon = true }.start()
        timingLport
    }.getOrElse { e -> Log.e(TAG, "openRaopTimingClient failed: ${e.message}"); 0 }
}

private fun ntpPutTs(buf: ByteArray, off: Int, ms: Long) {
    // Convertit ms (Unix epoch) en timestamp NTP 64-bit (epoch 1900)
    val NTP_OFFSET = 2208988800L
    val secs = ms / 1000 + NTP_OFFSET
    val frac = (ms % 1000) * 0x100000000L / 1000
    val ntp  = (secs shl 32) or (frac and 0xFFFFFFFFL)
    for (i in 0..7) buf[off + i] = ((ntp ushr (56 - i * 8)) and 0xFF).toByte()
}

private fun openAudioUdpServer(): Int {
    return runCatching {
        // Bind sur :: pour accepter aussi bien IPv4-mapped que IPv6 natif
        val sock = java.net.DatagramSocket(null)
        sock.bind(java.net.InetSocketAddress("::", 0))
        val port = sock.localPort
        Log.d(TAG, "Audio UDP server bound on port $port")
        Thread({
            runCatching {
                val buf = java.net.DatagramPacket(ByteArray(2048), 2048)
                while (true) {
                    sock.receive(buf)
                    Log.d(TAG, "Audio UDP pkt from ${buf.address}:${buf.port} len=${buf.length}")
                }
            }.onFailure { Log.d(TAG, "Audio UDP closed: ${it.message}") }
        }, "airplay-audio-udp").start()
        port
    }.getOrElse { e -> Log.e(TAG, "openAudioUdpServer failed: ${e.message}"); 6001 }
}

private fun openVideoTcpServer(
    onVideoNal: (ByteArray) -> Unit,
    onReconfigureCsd: (ByteArray, ByteArray) -> Unit,
): Int {
    return runCatching {
        val ss = java.net.ServerSocket()
        ss.reuseAddress = true
        ss.bind(java.net.InetSocketAddress("::", 0))  // IPv6 wildcard → dual-stack, macOS connects via IPv6
        val port = ss.localPort
        Log.d(TAG, "Video TCP server bound on port $port (IPv6 dual-stack via ::)")
        Thread({
            runCatching {
                ss.soTimeout = 10_000
                val client = try {
                    ss.accept()
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e(TAG, "Video TCP TIMEOUT — macOS n'a pas connecté après 10s (port $port)")
                    ss.close(); return@runCatching
                }
                ss.soTimeout = 0
                Log.d(TAG, "Video TCP client CONNECTED from ${client.remoteSocketAddress}")
                val inp = client.getInputStream()
                val hdr = ByteArray(128)
                var pendingSps: ByteArray? = null
                var pendingPps: ByteArray? = null
                while (true) {
                    var off = 0
                    while (off < 128) {
                        val r = inp.read(hdr, off, 128 - off)
                        if (r < 0) { off = -1; break }
                        off += r
                    }
                    if (off < 128) break
                    val payloadSize = hdr.int32BE(0)
                    val pktType = hdr[4].toInt() and 0xFF
                    val payload = if (payloadSize > 0) inp.readExact(payloadSize) else ByteArray(0)
                    when (pktType) {
                        0x00 -> { // codec data: avcC extradata → SPS + PPS
                            val nals = extractAvccExtradata(payload)
                            val sps = nals.firstOrNull { it.size > 4 && (it[4].toInt() and 0x1F) == 7 }
                            val pps = nals.firstOrNull { it.size > 4 && (it[4].toInt() and 0x1F) == 8 }
                            if (sps != null && pps != null) {
                                pendingSps = sps; pendingPps = pps
                                onReconfigureCsd(sps, pps)
                            }
                            nals.forEach { onVideoNal(it) }
                        }
                        0x01 -> { // video frame: AVCC format
                            if (pendingSps != null && pendingPps != null) {
                                onVideoNal(pendingSps!!)
                                onVideoNal(pendingPps!!)
                                pendingSps = null; pendingPps = null
                            }
                            avccFrameToAnnexB(payload) { nal -> onVideoNal(nal) }
                        }
                        0x05 -> {} // nop / keepalive
                        else -> Log.d(TAG, "Video pkt type=0x${"%02x".format(pktType)} size=$payloadSize")
                    }
                }
                client.close()
            }.onFailure { Log.e(TAG, "Video TCP error: ${it.message}") }
            ss.close()
        }, "airplay-video-tcp").start()
        port
    }.getOrElse { e -> Log.e(TAG, "openVideoTcpServer failed: ${e.message}"); 7100 }
}

// Parse avcC extradata box → list of Annex-B NAL units (start code + NALU bytes)
private fun extractAvccExtradata(data: ByteArray): List<ByteArray> {
    val result = mutableListOf<ByteArray>()
    if (data.size < 7) return result
    var off = 5 // skip configVersion, profile, compat, level, NAL length-1
    val spsCount = data[off++].toInt() and 0x1F
    repeat(spsCount) {
        if (off + 2 > data.size) return result
        val len = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
        off += 2
        if (off + len > data.size) return result
        result += annexBWrap(data, off, len)
        off += len
    }
    if (off >= data.size) return result
    val ppsCount = data[off++].toInt() and 0xFF
    repeat(ppsCount) {
        if (off + 2 > data.size) return result
        val len = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
        off += 2
        if (off + len > data.size) return result
        result += annexBWrap(data, off, len)
        off += len
    }
    return result
}

// Convert AVCC video frame payload (length-prefixed NALUs) to Annex-B NAL units
private inline fun avccFrameToAnnexB(data: ByteArray, emit: (ByteArray) -> Unit) {
    var off = 0
    while (off + 4 <= data.size) {
        val len = data.int32BE(off); off += 4
        if (len <= 0 || off + len > data.size) break
        emit(annexBWrap(data, off, len))
        off += len
    }
}

private fun annexBWrap(src: ByteArray, srcOff: Int, len: Int): ByteArray {
    val nal = ByteArray(4 + len)
    nal[0] = 0; nal[1] = 0; nal[2] = 0; nal[3] = 1
    src.copyInto(nal, 4, srcOff, srcOff + len)
    return nal
}

private fun ByteArray.int32BE(off: Int): Int =
    ((this[off].toInt() and 0xFF) shl 24) or
    ((this[off + 1].toInt() and 0xFF) shl 16) or
    ((this[off + 2].toInt() and 0xFF) shl 8) or
    (this[off + 3].toInt() and 0xFF)

// ---- Helpers ----

private fun InputStream.readHttpLine(): String? {
    val sb = StringBuilder()
    var prev = -1
    while (true) {
        val b = read()
        if (b == -1) return if (sb.isEmpty()) null else sb.toString()
        if (b == '\n'.code && prev == '\r'.code) return sb.dropLast(1).toString()
        sb.append(b.toChar())
        prev = b
    }
}

private fun InputStream.readExact(n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
        val r = read(buf, off, n - off)
        if (r < 0) break
        off += r
    }
    return buf
}

private fun OutputStream.writeHttp(status: String, contentType: String, body: ByteArray) {
    write("$status\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
    write(body)
    flush()
}

private fun OutputStream.writeRtsp(cseq: String, extra: Map<String, String> = emptyMap()) {
    val sb = StringBuilder("RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n")
    extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
    sb.append("\r\n")
    write(sb.toString().toByteArray())
    flush()
}

private fun OutputStream.writeRtspWithBody(cseq: String, contentType: String, body: ByteArray, extraHeaders: Map<String, String> = emptyMap()) {
    val sb = StringBuilder("RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n")
    extraHeaders.forEach { (k, v) -> sb.append("$k: $v\r\n") }
    sb.append("Content-Type: $contentType\r\nContent-Length: ${body.size}\r\n\r\n")
    write(sb.toString().toByteArray())
    write(body)
    flush()
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
private fun List<Byte>.toHex() = joinToString("") { "%02x".format(it) }
