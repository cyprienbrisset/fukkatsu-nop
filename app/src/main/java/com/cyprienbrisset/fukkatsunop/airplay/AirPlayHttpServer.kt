package com.cyprienbrisset.fukkatsunop.airplay

import android.util.Base64
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import timber.log.Timber
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

private val BPLIST_MAGIC = "bplist00".toByteArray().toList()


// Kept for AirPlay 1 / RtpVideoReceiver compatibility
data class AirPlaySession(val aesKey: ByteArray, val aesIv: ByteArray, val rtpPort: Int)

class AirPlayHttpServer(
    private val serverName: String,
    private val onConnecting: () -> Unit,
    private val onSession: (isExtended: Boolean) -> Unit,
    private val onDisconnect: () -> Unit,
    private val onCodecConfig: (CodecConfig) -> Unit = {},
    private val onVideoFrame: (frame: ByteArray, keyFrame: Boolean) -> Unit = { _, _ -> },
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
                    .onFailure { Timber.w("Port 7000 busy, retry in 1s: ${it.message}"); Thread.sleep(1_000) }
            }
            if (!bound) { Timber.e("Failed to bind port 7000 — giving up"); return@Thread }
            serverSocket = ss
            Timber.d("AirPlay HTTP server listening on :7000")
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
        Timber.d("Client CONNECTED: ${socket.remoteSocketAddress}")
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
            val mirror = MirrorSetupState()

            while (running && !socket.isClosed) {
                val requestLine = inp.readHttpLine() ?: run {
                    Timber.d("Client DISCONNECTED: ${socket.remoteSocketAddress}")
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
                Timber.d(">>> $method $path | body[${bodyBytes.size}]: ${bodyBytes.take(16).toHex()}")
                headers.forEach { (k, v) -> Timber.d("  hdr $k: $v") }

                when {
                    method == "GET" && (path == "/info" || path == "/server-info") -> {
                        if (bodyBytes.isNotEmpty()) {
                            val ascii = bodyBytes.toString(Charsets.ISO_8859_1).replace(Regex("[\\x00-\\x08\\x0E-\\x1F\\x7F]"), ".")
                            Timber.d("GET /info body[${bodyBytes.size}] hex=${bodyBytes.take(32).toHex()} txt=$ascii")
                        }
                        // The first /info carries a binary plist {qualifier: ["txtAirPlay"]}.
                        val qualifier = runCatching {
                            if (bodyBytes.size > 8 && bodyBytes.take(8) == BPLIST_MAGIC)
                                (BinaryPlist.decode(bodyBytes)["qualifier"] as? List<*>)?.firstOrNull() as? String
                            else null
                        }.getOrNull()
                        val info = AirPlayIdentity.info(serverName, qualifier)
                        Timber.d("GET /info qualifier=$qualifier")
                        out.writeHttp("HTTP/1.1 200 OK", "application/x-apple-binary-plist", BinaryPlist.encode(info))
                    }

                    method == "POST" && path == "/feedback" -> {
                        // Keep-alive sent every 2 s during a session.
                        out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        out.flush()
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
                        Timber.d("pair-setup: client_pk=${bodyBytes.toHex()} server_pk=${serverEdPub.toHex()}")
                        out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", serverEdPub)
                    }

                    method == "POST" && path == "/pair-verify" -> {
                        if (pairVerifySharedSecret == null) {
                            // M1 (68 bytes): [0x01,0x00,0x00,0x00] + client_eph_x25519(32) + client_lt_ed25519(32)
                            val clientX25519 = bodyBytes.copyOfRange(4, 36)
                            val clientLtEd   = if (bodyBytes.size >= 68) bodyBytes.copyOfRange(36, 68) else ByteArray(32)
                            Timber.d("pair-verify M1[${bodyBytes.size}]: client_x25519=${clientX25519.toHex()} client_lt=${clientLtEd.toHex()}")

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
                            Timber.d("pair-verify M2[${response.size}]: server_x25519=${serverX25519Pub.toHex()}")
                            Timber.d("pair-verify M2: shared=${sharedSecret.toHex()} sig=${signature.take(8).toHex()}...")
                            out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", response)
                        } else {
                            // M3 (68 bytes): [0x00,0x00,0x00,0x00] + AES-CTR-encrypted-client-signature(64)
                            // Client encrypted M3 with keystream[64:128] (CTR blocks 4-7, after "consuming" 0-3 for M2)
                            Timber.d("pair-verify M3[${bodyBytes.size}]: ${bodyBytes.toHex()}")
                            val ks = pairVerifyKeystream!!
                            val encrypted = if (bodyBytes.size >= 68) bodyBytes.copyOfRange(4, 68) else ByteArray(64)
                            // Decrypt with keystream[64:128]
                            val clientSig = ByteArray(64) { i -> (encrypted[i].toInt() xor ks[64 + i].toInt()).toByte() }

                            // Verify: client signed client_x25519 || server_x25519 with its long-term Ed25519 key
                            val verifyMsg = pairVerifyClientX25519Pub!! + pairVerifyServerX25519Pub!!
                            val ok = AirPlayPairing.edVerifyWith(verifyMsg, clientSig, pairVerifyClientLtEdPub!!)
                            Timber.d("pair-verify M3: clientSig=${clientSig.take(8).toHex()}... ok=$ok")
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
                        Timber.d("SETUP body[${bodyBytes.size}]=${bodyBytes.take(8).toHex()}")
                        if (bodyBytes.size > 8 && bodyBytes.take(8) == BPLIST_MAGIC) {
                            mirror.ecdhSecret = pairVerifySharedSecret
                            handleAirPlay2Setup(out, cseq, bodyBytes, macAddress, timingLportRef, mirror, onCodecConfig, onVideoFrame) { isExtended ->
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
                        Timber.d("GET_PARAMETER: $reqBody")
                        val respBody = when {
                            reqBody.contains("volume") -> "volume: 0.000000\r\n"
                            else -> ""
                        }
                        out.writeRtspWithBody(cseq, "text/parameters", respBody.toByteArray())
                    }

                    method == "SET_PARAMETER" -> {
                        Timber.d("SET_PARAMETER body[${bodyBytes.size}]")
                        out.writeRtsp(cseq)
                    }

                    method == "RECORD" -> {
                        Timber.d("RECORD")
                        out.writeRtsp(cseq, mapOf(
                            "Server" to "AirTunes/550.10",
                            "Session" to "BABE0001;timeout=90",
                            "Audio-Latency" to "11025",
                            "Audio-Jack-Status" to "connected; type=analog",
                        ))
                    }

                    method == "TEARDOWN" -> {
                        val tdAscii = bodyBytes.toString(Charsets.ISO_8859_1).replace(Regex("[\\x00-\\x08\\x0E-\\x1F\\x7F]"), ".")
                        Timber.d("TEARDOWN body[${bodyBytes.size}] hex=${bodyBytes.toHex()} txt=$tdAscii")
                        out.writeRtsp(cseq)
                        onDisconnect()
                        break
                    }

                    method == "ANNOUNCE" -> {
                        out.writeRtsp(cseq)
                    }

                    method == "POST" && path == "/fp-setup" -> {
                        Timber.d("fp-setup: body[${bodyBytes.size}]=${bodyBytes.take(16).toHex()}")
                        val fpResponse = when (bodyBytes.size) {
                            16 -> {
                                // Phase 1: respond with 142-byte static blob indexed by req[14] (mode 0-3)
                                val mode = bodyBytes.getOrElse(14) { 0 }.toInt() and 0xFF
                                Timber.d("fp-setup phase1 mode=$mode")
                                FairPlay.setupReply(mode)
                            }
                            164 -> {
                                // Phase 2: 12-byte header + last 20 bytes of request echoed.
                                // The request itself is the key message that decrypts the stream key.
                                Timber.d("fp-setup phase2")
                                mirror.fpKeyMsg = bodyBytes.copyOf()
                                FairPlay.handshakeReply(bodyBytes)
                            }
                            else -> {
                                Timber.w("fp-setup: unexpected body size ${bodyBytes.size}")
                                ByteArray(0)
                            }
                        }
                        out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", fpResponse)
                    }

                    else -> {
                        Timber.w("Unhandled: $method $path body[${bodyBytes.size}]=${bodyBytes.toHex()}")
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
// Same sequence as UxPlay (lib/raop_handlers.h):
//  1st SETUP: ekey/eiv + timingPort → decrypt the session key, answer timingPort + eventPort only.
//  2nd SETUP: streams[type 110, streamConnectionID] → derive the video key, answer the data port.
// A client may also send both in one request; each part is handled when present.

/** Per-connection state shared by fp-setup, pair-verify and the SETUP requests. */
class MirrorSetupState {
    @Volatile var fpKeyMsg: ByteArray? = null
    @Volatile var ecdhSecret: ByteArray? = null
    @Volatile var sessionKey: ByteArray? = null
    @Volatile var isExtended: Boolean = false
}

private fun handleAirPlay2Setup(
    out: OutputStream, cseq: String, bodyBytes: ByteArray,
    macAddress: java.net.InetAddress?,
    timingLportRef: java.util.concurrent.atomic.AtomicInteger,
    st: MirrorSetupState,
    onCodecConfig: (CodecConfig) -> Unit,
    onVideoFrame: (ByteArray, Boolean) -> Unit,
    onStreamsReady: (isExtended: Boolean) -> Unit = {},
) {
    runCatching {
        val plist = BinaryPlist.decode(bodyBytes)
        Timber.d("SETUP plist keys: ${plist.keys}")
        val response = mutableMapOf<String, Any>()

        val ekey = plist["ekey"] as? ByteArray
        if (ekey != null) {
            val keyMsg = st.fpKeyMsg
            val fairPlayKey = keyMsg?.let { FairPlay.decrypt(it, ekey) }
            st.sessionKey = fairPlayKey?.let { MirrorKeys.sessionKey(it, st.ecdhSecret) }
            Timber.d(
                "SETUP 1: ekey[${ekey.size}] keyMsg=${keyMsg?.size} fairplay=${fairPlayKey != null} " +
                    "ecdh=${st.ecdhSecret != null} sessionKey=${st.sessionKey != null}",
            )

            val isMirror = plist["isScreenMirroringSession"] as? Boolean ?: true
            st.isExtended = !isMirror
            Timber.d("SETUP 1: isMirror=$isMirror isExtended=${st.isExtended}")

            val macTimingPort = (plist["timingPort"] as? Long)?.toInt() ?: 0
            val timingLport = openRaopTimingClient(macAddress, macTimingPort)
            timingLportRef.set(timingLport)
            response["timingPort"] = timingLport.toLong()
            response["eventPort"] = 0L
        }

        @Suppress("UNCHECKED_CAST")
        val streams = plist["streams"] as? List<Map<String, Any?>>
        if (streams != null) {
            var hasVideo = false
            response["streams"] = streams.map { stream ->
                val type = (stream["type"] as? Long)?.toInt() ?: 0
                when (type) {
                    110 -> {
                        hasVideo = true
                        val connId = stream["streamConnectionID"] as? Long
                        val key = st.sessionKey
                        val cipher = if (key != null && connId != null) MirrorKeys.videoCipher(key, connId) else null
                        Timber.d("SETUP 2: video streamConnectionID=${connId?.let { java.lang.Long.toUnsignedString(it) }} cipher=${cipher != null}")
                        mapOf("type" to 110L, "dataPort" to openVideoTcpServer(cipher, onCodecConfig, onVideoFrame).toLong())
                    }
                    96, 103 -> {
                        val d = openAudioUdpServer(); val c = openAudioUdpServer()
                        mapOf("type" to type.toLong(), "dataPort" to d.toLong(), "controlPort" to c.toLong())
                    }
                    else -> mapOf("type" to type.toLong())
                }
            }
            if (hasVideo) onStreamsReady(st.isExtended)
        }

        val responseBytes = BinaryPlist.encode(response)
        Timber.d("SETUP response keys=${response.keys} bytes=${responseBytes.size}")
        out.writeRtspWithBody(cseq, "application/x-apple-binary-plist", responseBytes,
            mapOf("Server" to "AirTunes/220.68", "Session" to "BABE0001;timeout=90"))
    }.onFailure { e ->
        Timber.e(e, "SETUP error: ${e.message}")
        out.writeRtsp(cseq)
    }
}

// Apple RAOP timing client — we send 32-byte timing packets TO macOS:macTimingPort (UxPlay style)
// macOS is the NTP server; we are the client. timingLport = our local UDP port.
private fun openRaopTimingClient(remote: java.net.InetAddress?, macTimingPort: Int): Int {
    if (remote == null || macTimingPort == 0) return 0
    // A loopback peer only happens on the emulator through `adb forward`: the host is 10.0.2.2.
    val macAddress = if (remote.isLoopbackAddress) java.net.InetAddress.getByName("10.0.2.2") else remote
    return runCatching {
        val sock = java.net.DatagramSocket(null)
        sock.bind(java.net.InetSocketAddress("::", 0))
        val timingLport = sock.localPort
        Timber.d("RAOP timing client: bound port $timingLport, sending to $macAddress:$macTimingPort")
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
                    Timber.d("RAOP NTP: timing exchange ok with ${respPkt.address}")
                }.onFailure { Timber.d("RAOP NTP: no response (${it.message})") }
                Thread.sleep(3000)
            }
        }, "airplay-ntp-client").apply { isDaemon = true }.start()
        timingLport
    }.getOrElse { e -> Timber.e(e, "openRaopTimingClient failed: ${e.message}"); 0 }
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
        Timber.d("Audio UDP server bound on port $port")
        Thread({
            runCatching {
                val buf = java.net.DatagramPacket(ByteArray(2048), 2048)
                while (true) {
                    sock.receive(buf)
                    Timber.d("Audio UDP pkt from ${buf.address}:${buf.port} len=${buf.length}")
                }
            }.onFailure { Timber.d("Audio UDP closed: ${it.message}") }
        }, "airplay-audio-udp").start()
        port
    }.getOrElse { e -> Timber.e(e, "openAudioUdpServer failed: ${e.message}"); 6001 }
}

private fun openVideoTcpServer(
    cipher: javax.crypto.Cipher?,
    onCodecConfig: (CodecConfig) -> Unit,
    onVideoFrame: (ByteArray, Boolean) -> Unit,
): Int {
    return runCatching {
        val ss = java.net.ServerSocket()
        ss.reuseAddress = true
        // IPv6 wildcard → dual-stack, macOS connects via IPv6. 7100 is the usual AirPlay data port.
        runCatching { ss.bind(java.net.InetSocketAddress("::", 7100)) }
            .onFailure { ss.bind(java.net.InetSocketAddress("::", 0)) }
        val port = ss.localPort
        Timber.d("Video TCP server bound on port $port (IPv6 dual-stack via ::)")
        Thread({
            runCatching {
                ss.soTimeout = 15_000
                val client = try {
                    ss.accept()
                } catch (e: java.net.SocketTimeoutException) {
                    Timber.e("Video TCP TIMEOUT — le Mac ne s'est pas connecté (port $port)")
                    ss.close(); return@runCatching
                }
                ss.soTimeout = 0
                Timber.d("Video TCP client CONNECTED from ${client.remoteSocketAddress}")
                client.use { MirrorStreamReader(cipher, onCodecConfig, onVideoFrame).run(it.getInputStream()) }
                Timber.d("Video TCP client closed")
            }.onFailure { Timber.e("Video TCP error: ${it.message}") }
            ss.close()
        }, "airplay-video-tcp").start()
        port
    }.getOrElse { e -> Timber.e(e, "openVideoTcpServer failed: ${e.message}"); 7100 }
}

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
