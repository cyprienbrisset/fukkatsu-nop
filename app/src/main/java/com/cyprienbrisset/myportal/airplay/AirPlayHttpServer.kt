package com.cyprienbrisset.myportal.airplay

import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

data class AirPlaySession(
    val aesKey: ByteArray,   // 16-byte AES-128 key (RSA-decrypted)
    val aesIv: ByteArray,    // 16-byte AES-IV (raw from SDP)
    val rtpPort: Int,        // UDP port we allocated for video RTP
)

class AirPlayHttpServer(
    private val onConnecting: () -> Unit,
    private val onSession: (AirPlaySession) -> Unit,
    private val onDisconnect: () -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    private val FP_MAGIC = byteArrayOf(0x46, 0x50, 0x4c, 0x59.toByte()) // "FPLY"

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
        socket.use {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            var aesKey: ByteArray? = null
            var aesIv: ByteArray? = null
            var cseq = "0"
            var session = "1"
            var rtpPort = 0

            while (running && !socket.isClosed) {
                val requestLine = reader.readLine() ?: break
                val headers = mutableMapOf<String, String>()
                var line: String
                while (true) {
                    line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        headers[line.substring(0, colonIdx).trim()] =
                            line.substring(colonIdx + 1).trim()
                    }
                }
                cseq = headers["CSeq"] ?: cseq
                val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    reader.read(buf, 0, contentLength)
                    String(buf)
                } else ""

                val method = requestLine.substringBefore(' ')
                val path = requestLine.substringAfter(' ').substringBefore(' ')

                when {
                    // macOS probes the device with GET /info before showing it in Screen Mirroring.
                    // Without a valid response here the device never appears in the picker.
                    method == "GET" && (path == "/info" || path == "/server-info") -> {
                        val plist = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>deviceid</key><string>AA:BB:CC:DD:EE:FF</string>
<key>features</key><integer>1517580279</integer>
<key>model</key><string>AppleTV3,2</string>
<key>pk</key><string>${AirPlayKeyStore.publicKeyHex}</string>
<key>srcvers</key><string>220.68</string>
<key>vv</key><integer>2</integer>
</dict></plist>"""
                        val body = plist.toByteArray(Charsets.UTF_8)
                        writer.print("HTTP/1.1 200 OK\r\n")
                        writer.print("Content-Type: text/x-apple-plist+xml\r\n")
                        writer.print("Content-Length: ${body.size}\r\n")
                        writer.print("\r\n")
                        writer.flush()
                        socket.getOutputStream().write(body)
                        socket.getOutputStream().flush()
                    }
                    method == "POST" && path == "/fp-setup" -> {
                        val fpAck = ByteArray(32).also { buf ->
                            FP_MAGIC.copyInto(buf)
                            buf[4] = 0x03
                            buf[5] = 0x01
                            buf[6] = 0x02
                        }
                        writer.print("HTTP/1.1 200 OK\r\n")
                        writer.print("Content-Type: application/octet-stream\r\n")
                        writer.print("Content-Length: ${fpAck.size}\r\n")
                        writer.print("\r\n")
                        writer.flush()
                        socket.getOutputStream().write(fpAck)
                        socket.getOutputStream().flush()
                    }
                    method == "OPTIONS" -> {
                        writer.print("RTSP/1.0 200 OK\r\n")
                        writer.print("CSeq: $cseq\r\n")
                        writer.print("Public: ANNOUNCE, SETUP, RECORD, TEARDOWN, OPTIONS\r\n")
                        writer.print("\r\n")
                        writer.flush()
                        onConnecting()
                    }
                    method == "ANNOUNCE" -> {
                        body.lines().forEach { sdpLine ->
                            when {
                                sdpLine.startsWith("a=aeskey:") -> {
                                    val b64 = sdpLine.substringAfter("a=aeskey:").trim()
                                    val encrypted = Base64.decode(b64, Base64.DEFAULT)
                                    aesKey = runCatching { AirPlayKeyStore.decryptOaep(encrypted) }.getOrNull()
                                }
                                sdpLine.startsWith("a=aesiv:") -> {
                                    val b64 = sdpLine.substringAfter("a=aesiv:").trim()
                                    aesIv = Base64.decode(b64, Base64.DEFAULT)
                                }
                            }
                        }
                        writer.print("RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n\r\n")
                        writer.flush()
                    }
                    method == "SETUP" -> {
                        val udpSocket = java.net.DatagramSocket(0)
                        rtpPort = udpSocket.localPort
                        udpSocket.close()
                        val clientPort = headers["Transport"]
                            ?.let { Regex("client_port=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
                        writer.print("RTSP/1.0 200 OK\r\n")
                        writer.print("CSeq: $cseq\r\n")
                        writer.print("Session: $session\r\n")
                        writer.print("Transport: RTP/AVP/UDP;unicast;client_port=$clientPort;server_port=$rtpPort\r\n")
                        writer.print("\r\n")
                        writer.flush()
                    }
                    method == "RECORD" -> {
                        val key = aesKey
                        val iv = aesIv
                        if (key != null && iv != null && rtpPort > 0) {
                            onSession(AirPlaySession(key, iv, rtpPort))
                        }
                        writer.print("RTSP/1.0 200 OK\r\nCSeq: $cseq\r\nSession: $session\r\n\r\n")
                        writer.flush()
                    }
                    method == "TEARDOWN" -> {
                        writer.print("RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n\r\n")
                        writer.flush()
                        onDisconnect()
                        break
                    }
                }
            }
        }
    }
}
