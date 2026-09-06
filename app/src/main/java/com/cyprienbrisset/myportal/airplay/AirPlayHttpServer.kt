package com.cyprienbrisset.myportal.airplay

import android.util.Base64
import android.util.Log
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "AirPlayServer"

data class AirPlaySession(
    val aesKey: ByteArray,
    val aesIv: ByteArray,
    val rtpPort: Int,
)

class AirPlayHttpServer(
    private val onConnecting: () -> Unit,
    private val onSession: (AirPlaySession) -> Unit,
    private val onDisconnect: () -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    fun start() {
        running = true
        Thread({
            serverSocket = ServerSocket(7000)
            while (running) {
                runCatching {
                    val client = serverSocket?.accept() ?: return@runCatching
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
        Log.d(TAG, "Client connected: ${socket.remoteSocketAddress}")
        socket.use {
            val inp = socket.getInputStream()
            val out = socket.getOutputStream()

            // Per-session pair-verify state
            var pairVerifyX25519Priv: X25519PrivateKeyParameters? = null
            var pairVerifySessionKey: ByteArray? = null

            var aesKey: ByteArray? = null
            var aesIv: ByteArray? = null
            var cseq = "0"
            var rtpPort = 0

            while (running && !socket.isClosed) {
                val requestLine = inp.readHttpLine() ?: break
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

                when {
                    method == "GET" && (path == "/info" || path == "/server-info") -> {
                        val plist = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>deviceid</key><string>AA:BB:CC:DD:EE:FF</string>
<key>features</key><integer>1517580279</integer>
<key>model</key><string>AppleTV3,2</string>
<key>srcvers</key><string>220.68</string>
<key>vv</key><integer>2</integer>
</dict></plist>""".toByteArray(Charsets.UTF_8)
                        out.writeHttp("HTTP/1.1 200 OK", "text/x-apple-plist+xml", plist)
                    }

                    method == "POST" && path == "/pair-setup" -> {
                        // macOS sends 32 bytes = client Ed25519 ephemeral public key.
                        // We respond with our server Ed25519 public key (32 bytes).
                        val serverEdPub = AirPlayPairing.edPublicKeyBytes
                        Log.d(TAG, "pair-setup: client_pk=${bodyBytes.toHex()} server_pk=${serverEdPub.toHex()}")
                        out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", serverEdPub)
                    }

                    method == "POST" && path == "/pair-verify" -> {
                        if (pairVerifyX25519Priv == null) {
                            // Step M1: client sends [4-byte flags?] + 32-byte X25519 pubkey, OR just 32 bytes.
                            // Try: if body >= 36, skip first 4 bytes; else take first 32 bytes.
                            val clientX25519Bytes = if (bodyBytes.size >= 36)
                                bodyBytes.copyOfRange(4, 36)
                            else
                                bodyBytes.copyOfRange(0, minOf(32, bodyBytes.size))

                            Log.d(TAG, "pair-verify M1: client_x25519=${clientX25519Bytes.toHex()}")

                            val serverX25519Pair = AirPlayPairing.generateX25519Pair()
                            pairVerifyX25519Priv = serverX25519Pair.private as X25519PrivateKeyParameters
                            val serverX25519Pub  = (serverX25519Pair.public as org.bouncycastle.crypto.params.X25519PublicKeyParameters).encoded

                            val sharedSecret = AirPlayPairing.x25519(
                                pairVerifyX25519Priv!!,
                                X25519PublicKeyParameters(clientX25519Bytes, 0),
                            )
                            pairVerifySessionKey = AirPlayPairing.hkdf(
                                sharedSecret,
                                "Pair-Verify-Encrypt-Salt",
                                "Pair-Verify-Encrypt-Info",
                                32,
                            )

                            // Build M2 response: server X25519 pubkey (32) + encrypted server info.
                            // Server info = server Ed25519 pubkey (32) + client Ed25519 pubkey (32).
                            val serverEdPub   = AirPlayPairing.edPublicKeyBytes
                            val plainServerInfo = serverX25519Pub + serverEdPub
                            val nonce = "PV-Msg02".toByteArray(Charsets.UTF_8).copyOf(12)
                            val encryptedInfo = runCatching {
                                AirPlayPairing.chacha20Encrypt(pairVerifySessionKey!!, nonce, plainServerInfo)
                            }.getOrElse { e ->
                                Log.e(TAG, "pair-verify M2 encrypt failed: $e")
                                ByteArray(48) // stub 48 zeros
                            }

                            val response = serverX25519Pub + encryptedInfo
                            Log.d(TAG, "pair-verify M2: response[${response.size}]=${response.take(16).toHex()}")
                            out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", response)
                        } else {
                            // Step M3: client sends encrypted client info. We respond OK.
                            Log.d(TAG, "pair-verify M3: body[${bodyBytes.size}]=${bodyBytes.take(16).toHex()}")
                            out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", ByteArray(0))
                            onConnecting()
                        }
                    }

                    method == "OPTIONS" -> {
                        out.writeRtsp(cseq, mapOf(
                            "Public" to "ANNOUNCE, SETUP, RECORD, TEARDOWN, OPTIONS",
                        ))
                    }

                    method == "ANNOUNCE" -> {
                        val body = bodyBytes.toString(Charsets.UTF_8)
                        body.lines().forEach { line ->
                            when {
                                line.startsWith("a=aeskey:") -> {
                                    val b64 = line.substringAfter("a=aeskey:").trim()
                                    val encrypted = Base64.decode(b64, Base64.DEFAULT)
                                    aesKey = runCatching { AirPlayKeyStore.decryptOaep(encrypted) }.getOrNull()
                                        ?: runCatching {
                                            // AirPlay 2: key might be encrypted with session key
                                            pairVerifySessionKey?.let { sk ->
                                                val nonce = "PS-Msg06".toByteArray(Charsets.UTF_8).copyOf(12)
                                                AirPlayPairing.chacha20Decrypt(sk, nonce, encrypted)
                                            }
                                        }.getOrNull()
                                }
                                line.startsWith("a=aesiv:") -> {
                                    val b64 = line.substringAfter("a=aesiv:").trim()
                                    aesIv = Base64.decode(b64, Base64.DEFAULT)
                                }
                            }
                        }
                        out.writeRtsp(cseq)
                    }

                    method == "SETUP" -> {
                        val udpSocket = java.net.DatagramSocket(0)
                        rtpPort = udpSocket.localPort
                        udpSocket.close()
                        val clientPort = headers["Transport"]
                            ?.let { Regex("client_port=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
                        out.writeRtsp(cseq, mapOf(
                            "Session" to "1",
                            "Transport" to "RTP/AVP/UDP;unicast;client_port=$clientPort;server_port=$rtpPort",
                        ))
                    }

                    method == "RECORD" -> {
                        val key = aesKey; val iv = aesIv
                        if (key != null && iv != null && rtpPort > 0) {
                            onSession(AirPlaySession(key, iv, rtpPort))
                        }
                        out.writeRtsp(cseq, mapOf("Session" to "1"))
                    }

                    method == "TEARDOWN" -> {
                        out.writeRtsp(cseq)
                        onDisconnect()
                        break
                    }

                    method == "POST" && path == "/fp-setup" -> {
                        val fpAck = ByteArray(32).also { buf ->
                            byteArrayOf(0x46, 0x50, 0x4c, 0x59.toByte()).copyInto(buf)
                            buf[4] = 0x03; buf[5] = 0x01; buf[6] = 0x02
                        }
                        out.writeHttp("HTTP/1.1 200 OK", "application/octet-stream", fpAck)
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

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
private fun List<Byte>.toHex() = joinToString("") { "%02x".format(it) }
