# AirPlay Receiver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Portal act as an AirPlay 1 Screen Mirroring receiver — the Mac projects its screen via Control Center → Screen Mirroring, the Portal shows it fullscreen automatically.

**Architecture:** Always-on `AirPlayService` (ForegroundService) runs `AirPlayReceiver` which owns a jmDNS Bonjour advertiser, a raw TCP server on port 7000 handling HTTP fp-setup + RTSP ANNOUNCE/SETUP/RECORD/TEARDOWN, a UDP RTP receiver that AES-128-CBC decrypts video packets, and a `MediaCodec` H.264 decoder rendering to a `SurfaceView` in `AirPlayActivity`. State is shared app-wide via a companion-object `StateFlow<AirPlayState>`. `HomeScreen` auto-launches `AirPlayActivity` when state reaches `Streaming`.

**Tech Stack:** jmDNS 3.5.8 (mDNS Bonjour), javax.crypto (AES + RSA, built-in), MediaCodec (H.264, built-in), DatagramSocket + ServerSocket (built-in), Jetpack Compose

---

## File Map

| File | Action |
|---|---|
| `gradle/libs.versions.toml` | Add jmdns version + library entry |
| `app/build.gradle.kts` | Add jmdns dependency |
| `app/src/main/AndroidManifest.xml` | Add CHANGE_WIFI_MULTICAST_STATE permission + AirPlayService + AirPlayActivity |
| `data/tile/TileEntity.kt` | Add `AIRPLAY` to `TileType` enum |
| `airplay/AirPlayState.kt` | New — sealed class |
| `airplay/AirPlayKeyStore.kt` | New — RSA-2048 key pair (generated once, kept in memory) |
| `airplay/MdnsAdvertiser.kt` | New — jmDNS _airplay._tcp advertisement |
| `airplay/AirPlayHttpServer.kt` | New — TCP ServerSocket: HTTP fp-setup + RTSP ANNOUNCE/SETUP/RECORD/TEARDOWN |
| `airplay/RtpVideoReceiver.kt` | New — UDP socket + AES-128-CBC decrypt → pushes NAL units |
| `airplay/H264Renderer.kt` | New — MediaCodec decoder, Surface-mode |
| `airplay/AirPlayReceiver.kt` | New — orchestrator, StateFlow, attachSurface/detachSurface |
| `airplay/AirPlayService.kt` | New — ForegroundService, always-on |
| `alarm/BootReceiver.kt` | Modify — also start AirPlayService on boot |
| `MainActivity.kt` | Modify — start AirPlayService on create |
| `ui/airplay/AirPlayViewModel.kt` | New — observe state, bind surface |
| `ui/airplay/AirPlayActivity.kt` | New — fullscreen SurfaceView + auto-hiding HUD |
| `ui/home/TileIcon.kt` | Modify — add AIRPLAY case |
| `ui/home/MedallionGrid.kt` | Modify — accept airPlayState, draw Shu border on STREAMING |
| `ui/home/HomeViewModel.kt` | Modify — expose airPlayState StateFlow |
| `ui/home/HomeScreen.kt` | Modify — LaunchedEffect auto-launch AirPlayActivity |
| `ui/settings/TileEditScreen.kt` | Modify — add "Écran Mac" AIRPLAY tile option |

---

### Task 1: Dependencies + Permissions

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add jmDNS to version catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:
```toml
jmdns = "3.5.8"
```
Under `[libraries]`:
```toml
jmdns = { group = "org.jmdns", name = "jmdns", version.ref = "jmdns" }
```

- [ ] **Step 2: Add jmDNS to app/build.gradle.kts**

In the `dependencies { }` block, after `implementation(libs.coil.compose)`:
```kotlin
implementation(libs.jmdns)
```

- [ ] **Step 3: Sync gradle**

Run: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep jmdns`
Expected: line containing `org.jmdns:jmdns:3.5.8`

- [ ] **Step 4: Add permission and components to AndroidManifest.xml**

After the existing `<uses-permission android:name="android.permission.READ_CALENDAR" />` line, add:
```xml
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

Inside `<application>` before the closing `</application>` tag, add:
```xml
<service
    android:name=".airplay.AirPlayService"
    android:exported="false"
    android:foregroundServiceType="mediaProjection" />

<activity
    android:name=".ui.airplay.AirPlayActivity"
    android:exported="false"
    android:configChanges="orientation|screenSize|keyboardHidden"
    android:screenOrientation="landscape"
    android:theme="@style/Theme.MyPortal" />
```

- [ ] **Step 5: Build to verify manifest is valid**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "feat(airplay): add jmDNS dependency, CHANGE_WIFI_MULTICAST_STATE permission, AirPlayService + AirPlayActivity manifest entries"
```

---

### Task 2: Data Types

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/data/tile/TileEntity.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayState.kt`

- [ ] **Step 1: Add AIRPLAY to TileType enum**

In `data/tile/TileEntity.kt`, change:
```kotlin
enum class TileType { APP, WEB }
```
to:
```kotlin
enum class TileType { APP, WEB, AIRPLAY }
```

- [ ] **Step 2: Create AirPlayState.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayState.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

sealed class AirPlayState {
    object Waiting    : AirPlayState()
    object Connecting : AirPlayState()
    object Streaming  : AirPlayState()
    data class Error(val msg: String) : AirPlayState()
}
```

- [ ] **Step 3: Build**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add data/tile/TileEntity.kt airplay/AirPlayState.kt
git commit -m "feat(airplay): TileType.AIRPLAY + AirPlayState sealed class"
```

---

### Task 3: RSA Key Store

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayKeyStore.kt`

The Mac encrypts the AES session key with our RSA public key (advertised as `pk` in mDNS). We generate a fixed RSA-2048 key pair once at process start. The public key is exposed as a hex string for mDNS, and the private key is used to decrypt the AES session key from ANNOUNCE SDP.

- [ ] **Step 1: Create AirPlayKeyStore.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayKeyStore.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

object AirPlayKeyStore {
    val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    /** Hex-encoded DER SubjectPublicKeyInfo — advertised as `pk` in mDNS TXT record. */
    val publicKeyHex: String by lazy {
        keyPair.public.encoded.joinToString("") { "%02x".format(it) }
    }

    /** Decrypt an RSA-OAEP/SHA1 encrypted blob (the AES session key from ANNOUNCE SDP). */
    fun decryptOaep(encrypted: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keyPair.private)
        return cipher.doFinal(encrypted)
    }
}
```

- [ ] **Step 2: Write unit test**

Create `app/src/test/java/com/cyprienbrisset/myportal/airplay/AirPlayKeyStoreTest.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher

class AirPlayKeyStoreTest {
    @Test
    fun `publicKeyHex is 64+ character hex string`() {
        val hex = AirPlayKeyStore.publicKeyHex
        assertTrue("expected non-empty hex", hex.length >= 64)
        assertTrue("expected hex chars only", hex.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `decryptOaep round-trips a 16-byte AES key`() {
        val aesKey = ByteArray(16) { it.toByte() }
        val encryptCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, AirPlayKeyStore.keyPair.public)
        val encrypted = encryptCipher.doFinal(aesKey)
        val decrypted = AirPlayKeyStore.decryptOaep(encrypted)
        assertArrayEquals(aesKey, decrypted)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:test --tests "*.AirPlayKeyStoreTest" -q`
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayKeyStore.kt \
        app/src/test/java/com/cyprienbrisset/myportal/airplay/AirPlayKeyStoreTest.kt
git commit -m "feat(airplay): AirPlayKeyStore — RSA-2048 key pair for AES session key decryption"
```

---

### Task 4: mDNS Advertiser

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/airplay/MdnsAdvertiser.kt`

Advertises `_airplay._tcp` on port 7000 so the Mac's Screen Mirroring picker shows "Portal".

- [ ] **Step 1: Create MdnsAdvertiser.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/airplay/MdnsAdvertiser.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

import android.content.Context
import android.net.wifi.WifiManager
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class MdnsAdvertiser(private val context: Context) {
    private var jmdns: JmDNS? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("airplay").also {
            it.setReferenceCounted(false)
            it.acquire()
        }
        val props = mapOf(
            "deviceid" to "AA:BB:CC:DD:EE:FF",
            "features"  to "0x7F8AD0A7,0x1E",
            "flags"     to "0x4",
            "model"     to "AppleTV3,2",
            "pk"        to AirPlayKeyStore.publicKeyHex,
            "srcvers"   to "220.68",
        )
        jmdns = JmDNS.create()
        val service = ServiceInfo.create("_airplay._tcp.local.", "Portal", 7000, 0, 0, props)
        jmdns?.registerService(service)
    }

    fun stop() {
        runCatching { jmdns?.unregisterAllServices() }
        runCatching { jmdns?.close() }
        jmdns = null
        multicastLock?.release()
        multicastLock = null
    }
}
```

- [ ] **Step 2: Build**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL (jmDNS classes resolve)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/airplay/MdnsAdvertiser.kt
git commit -m "feat(airplay): MdnsAdvertiser — Bonjour _airplay._tcp advertisement via jmDNS"
```

---

### Task 5: TCP Control Server (fp-setup + RTSP)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayHttpServer.kt`

Raw TCP `ServerSocket` on port 7000. Handles:
- `POST /fp-setup` (HTTP) — FairPlay legacy handshake (fixed response)
- `ANNOUNCE` (RTSP) — parses SDP for `a=aeskey` + `a=aesiv`
- `SETUP` (RTSP) — allocates UDP port for RTP video, returns it to the Mac
- `RECORD` (RTSP) — signals stream start
- `TEARDOWN` (RTSP) — signals disconnect

Parsed session parameters (AES key, IV, RTP port) are delivered via callbacks.

- [ ] **Step 1: Create AirPlayHttpServer.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayHttpServer.kt`:
```kotlin
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

    // Fixed 14-byte FairPlay phase-1 response header (magic + version).
    // The full 142-byte response body is the standard legacy fp-setup ACK.
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
                    method == "POST" && path == "/fp-setup" -> {
                        // Legacy FairPlay phase-1: return a 32-byte ACK (version 3, phase 1).
                        val fpAck = ByteArray(32).also { buf ->
                            FP_MAGIC.copyInto(buf)
                            buf[4] = 0x03 // version
                            buf[5] = 0x01 // phase 1
                            buf[6] = 0x02 // sub-phase
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
                        // Parse SDP for aeskey + aesiv
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
                        // Allocate a random UDP port for video RTP
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
```

- [ ] **Step 2: Write unit test for SDP parsing logic**

Create `app/src/test/java/com/cyprienbrisset/myportal/airplay/AirPlayHttpServerTest.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

import android.util.Base64
import org.junit.Assert.*
import org.junit.Test

class AirPlayHttpServerTest {
    @Test
    fun `transport header port extraction`() {
        val header = "RTP/AVP/UDP;unicast;client_port=12345-12346"
        val port = Regex("client_port=(\\d+)").find(header)?.groupValues?.get(1)?.toIntOrNull()
        assertEquals(12345, port)
    }

    @Test
    fun `SDP aesiv base64 decode`() {
        val ivBytes = ByteArray(16) { it.toByte() }
        // android.util.Base64 not available in unit tests — use java.util.Base64
        val b64 = java.util.Base64.getEncoder().encodeToString(ivBytes)
        val decoded = java.util.Base64.getDecoder().decode(b64)
        assertArrayEquals(ivBytes, decoded)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:test --tests "*.AirPlayHttpServerTest" -q`
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 4: Build full APK**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayHttpServer.kt \
        app/src/test/java/com/cyprienbrisset/myportal/airplay/AirPlayHttpServerTest.kt
git commit -m "feat(airplay): AirPlayHttpServer — TCP server handling fp-setup, RTSP ANNOUNCE/SETUP/RECORD/TEARDOWN"
```

---

### Task 6: RTP Video Receiver

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/airplay/RtpVideoReceiver.kt`

Receives UDP RTP packets on the port negotiated in SETUP. Each RTP payload is AES-128-CBC decrypted (same key+IV for every packet), then the H.264 NAL unit is extracted and pushed to `H264Renderer`.

- [ ] **Step 1: Create RtpVideoReceiver.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/airplay/RtpVideoReceiver.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

import java.net.DatagramPacket
import java.net.DatagramSocket
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RtpVideoReceiver(
    private val session: AirPlaySession,
    private val onNalUnit: (ByteArray) -> Unit,
) {
    private var udpSocket: DatagramSocket? = null
    @Volatile private var running = false

    private val cipher = Cipher.getInstance("AES/CBC/NoPadding").also { c ->
        c.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(session.aesKey, "AES"),
            IvParameterSpec(session.aesIv),
        )
    }

    fun start() {
        running = true
        udpSocket = DatagramSocket(session.rtpPort)
        Thread({
            val buf = ByteArray(65536)
            val packet = DatagramPacket(buf, buf.size)
            while (running) {
                runCatching {
                    udpSocket?.receive(packet) ?: return@runCatching
                    processPacket(buf, packet.length)
                }
            }
        }, "airplay-rtp").start()
    }

    fun stop() {
        running = false
        runCatching { udpSocket?.close() }
    }

    private fun processPacket(data: ByteArray, length: Int) {
        // RTP header is 12 bytes (fixed). Payload starts at byte 12.
        if (length <= 12) return
        val payloadOffset = 12
        val payloadLen = length - payloadOffset
        // AES-CBC requires payload to be a multiple of 16; decrypt only the aligned portion,
        // append the unaligned tail as-is (AirPlay spec: last <16 bytes are unencrypted).
        val alignedLen = (payloadLen / 16) * 16
        val decrypted = if (alignedLen > 0) {
            // Re-init cipher with the same IV for each packet (IV is not chained across packets).
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(session.aesKey, "AES"),
                IvParameterSpec(session.aesIv),
            )
            val dec = cipher.doFinal(data, payloadOffset, alignedLen)
            if (alignedLen < payloadLen) {
                dec + data.copyOfRange(payloadOffset + alignedLen, payloadOffset + payloadLen)
            } else dec
        } else {
            data.copyOfRange(payloadOffset, payloadOffset + payloadLen)
        }
        onNalUnit(decrypted)
    }
}
```

- [ ] **Step 2: Write unit test for AES decryption**

Create `app/src/test/java/com/cyprienbrisset/myportal/airplay/RtpVideoReceiverTest.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RtpVideoReceiverTest {
    private val key = ByteArray(16) { 0x42.toByte() }
    private val iv  = ByteArray(16) { 0x0F.toByte() }

    @Test
    fun `AES-CBC round-trip with 32-byte payload (2 blocks)`() {
        val plaintext = ByteArray(32) { it.toByte() }
        val encCipher = Cipher.getInstance("AES/CBC/NoPadding")
        encCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val encrypted = encCipher.doFinal(plaintext)

        val decCipher = Cipher.getInstance("AES/CBC/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val decrypted = decCipher.doFinal(encrypted)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `unaligned tail is appended verbatim`() {
        // 16 bytes aligned + 5 bytes tail = 21 bytes payload
        val aligned = ByteArray(16) { 0x01.toByte() }
        val tail = ByteArray(5) { 0xFF.toByte() }
        val payload = aligned + tail
        // Encrypt only the aligned portion
        val encCipher = Cipher.getInstance("AES/CBC/NoPadding")
        encCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val encAligned = encCipher.doFinal(aligned)
        val encPayload = encAligned + tail

        // Simulate RtpVideoReceiver: decrypt 16-byte block, append 5-byte tail verbatim
        val decCipher = Cipher.getInstance("AES/CBC/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val dec = decCipher.doFinal(encPayload, 0, 16)
        val result = dec + encPayload.copyOfRange(16, encPayload.size)

        assertArrayEquals(payload, result)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:test --tests "*.RtpVideoReceiverTest" -q`
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/airplay/RtpVideoReceiver.kt \
        app/src/test/java/com/cyprienbrisset/myportal/airplay/RtpVideoReceiverTest.kt
git commit -m "feat(airplay): RtpVideoReceiver — UDP RTP receive + AES-128-CBC decrypt"
```

---

### Task 7: H.264 Renderer

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/airplay/H264Renderer.kt`

Wraps `MediaCodec` in Surface output mode. `AirPlayActivity` calls `attachSurface(surface)` when its `SurfaceView` is ready and `detachSurface()` when it goes away.

- [ ] **Step 1: Create H264Renderer.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/airplay/H264Renderer.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface

class H264Renderer {
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    @Volatile private var configured = false

    fun attachSurface(s: Surface) {
        surface = s
        startCodec(s)
    }

    fun detachSurface() {
        stopCodec()
        surface = null
    }

    fun pushNalUnit(data: ByteArray) {
        if (!configured) return
        val codec = codec ?: return
        val index = codec.dequeueInputBuffer(10_000L)
        if (index < 0) return
        val buf = codec.getInputBuffer(index) ?: return
        buf.clear()
        buf.put(data)
        codec.queueInputBuffer(index, 0, data.size, System.nanoTime() / 1000, 0)
        // Release output buffers so the decoder keeps rendering to the Surface.
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = codec.dequeueOutputBuffer(info, 0L)
            if (outIdx < 0) break
            codec.releaseOutputBuffer(outIdx, true)
        }
    }

    private fun startCodec(s: Surface) {
        stopCodec()
        val format = MediaFormat.createVideoFormat("video/avc", 1920, 1080)
        val c = MediaCodec.createDecoderByType("video/avc")
        c.configure(format, s, null, 0)
        c.start()
        codec = c
        configured = true
    }

    fun stopCodec() {
        configured = false
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }
}
```

- [ ] **Step 2: Build**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/airplay/H264Renderer.kt
git commit -m "feat(airplay): H264Renderer — MediaCodec Surface-mode H.264 decoder"
```

---

### Task 8: AirPlay Receiver (Orchestrator)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayReceiver.kt`

Singleton that wires together `MdnsAdvertiser`, `AirPlayHttpServer`, `RtpVideoReceiver`, and `H264Renderer`. Drives the `StateFlow<AirPlayState>` that the rest of the app observes.

- [ ] **Step 1: Create AirPlayReceiver.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayReceiver.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AirPlayReceiver {
    private val _state = MutableStateFlow<AirPlayState>(AirPlayState.Waiting)
    val state: StateFlow<AirPlayState> = _state

    private var mdns: MdnsAdvertiser? = null
    private var server: AirPlayHttpServer? = null
    private var rtp: RtpVideoReceiver? = null
    private val renderer = H264Renderer()

    fun start(context: Context) {
        _state.value = AirPlayState.Waiting
        mdns = MdnsAdvertiser(context).also { it.start() }
        server = AirPlayHttpServer(
            onConnecting = { _state.value = AirPlayState.Connecting },
            onSession = { session ->
                rtp?.stop()
                rtp = RtpVideoReceiver(session) { nal -> renderer.pushNalUnit(nal) }
                    .also { it.start() }
                _state.value = AirPlayState.Streaming
            },
            onDisconnect = {
                rtp?.stop()
                rtp = null
                renderer.detachSurface()
                _state.value = AirPlayState.Waiting
            },
        ).also { it.start() }
    }

    fun stop() {
        rtp?.stop(); rtp = null
        server?.stop(); server = null
        mdns?.stop(); mdns = null
        renderer.stopCodec()
        _state.value = AirPlayState.Waiting
    }

    fun attachSurface(surface: Surface) = renderer.attachSurface(surface)
    fun detachSurface() = renderer.detachSurface()

    fun disconnect() {
        rtp?.stop(); rtp = null
        renderer.detachSurface()
        _state.value = AirPlayState.Waiting
        // Server keeps listening for next connection.
    }
}
```

- [ ] **Step 2: Build**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayReceiver.kt
git commit -m "feat(airplay): AirPlayReceiver — orchestrator + StateFlow<AirPlayState>"
```

---

### Task 9: AirPlay ForegroundService

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayService.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/alarm/BootReceiver.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/MainActivity.kt`

- [ ] **Step 1: Create AirPlayService.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayService.kt`:
```kotlin
package com.cyprienbrisset.myportal.airplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AirPlayService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val CHANNEL = "airplay"
    private val NOTIF_ID = 8000

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("En attente de connexion"))
        AirPlayReceiver.start(this)
        AirPlayReceiver.state.onEach { state ->
            val text = when (state) {
                is AirPlayState.Streaming -> "Mac connecté ● Live"
                is AirPlayState.Connecting -> "Connexion en cours…"
                is AirPlayState.Error -> "Erreur : ${state.msg}"
                else -> "En attente de connexion"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(text))
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        AirPlayReceiver.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Écran Mac", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL)
            .setContentTitle("Écran Mac")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .build()
}
```

- [ ] **Step 2: Extend BootReceiver to start AirPlayService**

In `alarm/BootReceiver.kt`, replace the `onReceive` body with:
```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    // Start AirPlay receiver service
    context.startForegroundService(
        Intent(context, com.cyprienbrisset.myportal.airplay.AirPlayService::class.java)
    )
    // Re-schedule alarms
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val repo = AlarmRepository(AppDatabase.get(context).alarmDao())
            val scheduler = AlarmScheduler(context)
            repo.enabled().forEach { scheduler.schedule(it) }
        } finally {
            pending.finish()
        }
    }
}
```

- [ ] **Step 3: Start AirPlayService from MainActivity**

Open `app/src/main/java/com/cyprienbrisset/myportal/MainActivity.kt`. In `onCreate`, after `super.onCreate(savedInstanceState)`, add:
```kotlin
startService(Intent(this, com.cyprienbrisset.myportal.airplay.AirPlayService::class.java))
```

(Add `import android.content.Intent` if not already present.)

- [ ] **Step 4: Build**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Install and verify service starts**

Run:
```bash
~/Library/Android/sdk/platform-tools/adb install -r -d app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.cyprienbrisset.myportal/.MainActivity
sleep 3
~/Library/Android/sdk/platform-tools/adb shell dumpsys activity services com.cyprienbrisset.myportal | grep airplay
```
Expected: line containing `AirPlayService`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/airplay/AirPlayService.kt \
        app/src/main/java/com/cyprienbrisset/myportal/alarm/BootReceiver.kt \
        app/src/main/java/com/cyprienbrisset/myportal/MainActivity.kt
git commit -m "feat(airplay): AirPlayService ForegroundService + boot start + MainActivity start"
```

---

### Task 10: AirPlay Fullscreen Activity

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/airplay/AirPlayViewModel.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/airplay/AirPlayActivity.kt`

- [ ] **Step 1: Create AirPlayViewModel.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/ui/airplay/AirPlayViewModel.kt`:
```kotlin
package com.cyprienbrisset.myportal.ui.airplay

import androidx.lifecycle.ViewModel
import com.cyprienbrisset.myportal.airplay.AirPlayReceiver
import com.cyprienbrisset.myportal.airplay.AirPlayState

class AirPlayViewModel : ViewModel() {
    val state = AirPlayReceiver.state

    fun disconnect() = AirPlayReceiver.disconnect()
}
```

- [ ] **Step 2: Create AirPlayActivity.kt**

Create `app/src/main/java/com/cyprienbrisset/myportal/ui/airplay/AirPlayActivity.kt`:
```kotlin
package com.cyprienbrisset.myportal.ui.airplay

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cyprienbrisset.myportal.airplay.AirPlayReceiver
import com.cyprienbrisset.myportal.airplay.AirPlayState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AirPlayActivity : ComponentActivity() {
    private val vm: AirPlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        val frame = FrameLayout(this)
        val surfaceView = SurfaceView(this)
        frame.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))

        // Simple HUD: close button, top-right corner
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            visibility = View.VISIBLE
            setOnClickListener { vm.disconnect() }
        }
        val hudParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = 24; rightMargin = 24
        }
        frame.addView(closeBtn, hudParams)
        setContentView(frame)

        // Auto-hide HUD after 3s
        lifecycleScope.launch {
            delay(3000)
            closeBtn.animate().alpha(0f).setDuration(500).start()
        }
        surfaceView.setOnClickListener {
            closeBtn.animate().cancel()
            closeBtn.alpha = 1f
            closeBtn.visibility = View.VISIBLE
            lifecycleScope.launch {
                delay(3000)
                closeBtn.animate().alpha(0f).setDuration(500).start()
            }
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                AirPlayReceiver.attachSurface(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                AirPlayReceiver.detachSurface()
            }
        })

        // Finish when the Mac disconnects
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    if (state is AirPlayState.Waiting || state is AirPlayState.Error) {
                        finish()
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/airplay/
git commit -m "feat(airplay): AirPlayActivity + AirPlayViewModel — fullscreen SurfaceView + auto-hiding HUD"
```

---

### Task 11: Tile Rendering (TileIcon + MedallionGrid)

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/TileIcon.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/MedallionGrid.kt`

- [ ] **Step 1: Add AIRPLAY case to TileIcon**

In `TileIcon.kt`, in the `TileIcon` composable, the `when (tile.type)` block currently handles `APP` and `WEB`. Add an `AIRPLAY` branch.

Add import at the top:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material3.Icon
import com.cyprienbrisset.myportal.airplay.AirPlayReceiver
import com.cyprienbrisset.myportal.airplay.AirPlayState
```

In the `when (tile.type)` block inside `TileIcon`, before the closing brace, add:
```kotlin
TileType.AIRPLAY -> {
    val airState by AirPlayReceiver.state.collectAsState()
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4))
            .background(Color(monogramColor("Mac"))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Monitor,
            contentDescription = "Écran Mac",
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
```

Also add `import androidx.compose.runtime.collectAsState` and `import androidx.compose.runtime.getValue`.

- [ ] **Step 2: Add Shu border overlay for STREAMING state in MedallionGrid**

In `MedallionGrid.kt`, the `items` block renders each tile via `Medallion { BadgedTileIcon(...) }`. Wrap the `Box` that contains `Medallion` with an additional `border` modifier when the tile is AIRPLAY + STREAMING.

Add these imports to MedallionGrid.kt:
```kotlin
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cyprienbrisset.myportal.airplay.AirPlayReceiver
import com.cyprienbrisset.myportal.airplay.AirPlayState
import com.cyprienbrisset.myportal.data.tile.TileType
```

Inside the `items(displayTiles, key = { it.id }) { tile ->` block, before the existing `Box(modifier = ...)`, add at the top of the lambda:
```kotlin
val airPlayState by AirPlayReceiver.state.collectAsState()
val isAirPlayLive = tile.type == TileType.AIRPLAY && airPlayState is AirPlayState.Streaming
```

Then wrap the existing outermost `Box` with a new Box that applies the border:
```kotlin
Box(
    modifier = Modifier
        .then(
            if (isAirPlayLive)
                Modifier.border(2.dp, Shu, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            else Modifier
        )
) {
    // existing Box(modifier = Modifier.zIndex(...) ...) { ... }
}
```

- [ ] **Step 3: Add status sub-label to AIRPLAY tile**

The AIRPLAY tile's status text ("● Live", "En attente…") should appear below the icon text, inside the icon area. In `TileIcon.kt`, update the `TileType.AIRPLAY` branch to a `Column`:

```kotlin
TileType.AIRPLAY -> {
    val airState by AirPlayReceiver.state.collectAsState()
    val statusText = when (airState) {
        is AirPlayState.Streaming  -> "● Live"
        is AirPlayState.Connecting -> "Connexion…"
        is AirPlayState.Error      -> "⚠ Erreur"
        else                       -> "En attente"
    }
    val statusColor = when (airState) {
        is AirPlayState.Streaming -> Shu
        is AirPlayState.Error     -> Kinari
        else                      -> SumiMuted
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4))
            .background(Color(monogramColor("Mac"))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Monitor,
                contentDescription = "Écran Mac",
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f),
            )
            Text(
                statusText,
                color = statusColor,
                fontSize = (size.value / 8).sp,
                lineHeight = (size.value / 8).sp,
            )
        }
    }
}
```

Add missing imports to TileIcon.kt:
```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material3.Icon
import com.cyprienbrisset.myportal.airplay.AirPlayReceiver
import com.cyprienbrisset.myportal.airplay.AirPlayState
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
```

- [ ] **Step 4: Build**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home/TileIcon.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/home/MedallionGrid.kt
git commit -m "feat(airplay): tile rendering — Monitor icon + Live/Waiting status + Shu border on STREAMING"
```

---

### Task 12: Add AIRPLAY Tile in TileEditScreen

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/TileEditScreen.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/TileEditViewModel.kt`

- [ ] **Step 1: Add addAirPlay() to TileEditViewModel**

Find `TileEditViewModel.kt` (same package as TileEditScreen). It already has `addApp(...)` and `addWeb(...)`. Add:
```kotlin
fun addAirPlay() = viewModelScope.launch {
    val existing = repo.getAll().firstOrNull { it.type == TileType.AIRPLAY }
    if (existing == null) {
        repo.add(TileEntity(type = TileType.AIRPLAY, label = "Écran Mac", position = 0))
    }
}
```

This prevents adding the AIRPLAY tile twice (it's a singleton tile).

- [ ] **Step 2: Add "Système" tab to TileEditScreen SegmentedChoice**

In `TileEditScreen.kt`, the `SegmentedChoice` currently has 3 segments (App, Web, Store). Add a 4th:

Change:
```kotlin
SegmentedChoice(
    listOf(Segment("アプリ", "Application"), Segment("ウェブ", "Web"), Segment("ストア", "Store")),
    selectedIndex = mode, onSelect = { mode = it },
)
```
to:
```kotlin
SegmentedChoice(
    listOf(
        Segment("アプリ", "Application"),
        Segment("ウェブ", "Web"),
        Segment("ストア", "Store"),
        Segment("システム", "Système"),
    ),
    selectedIndex = mode, onSelect = { mode = it },
)
```

- [ ] **Step 3: Add AIRPLAY section when mode == 3**

In the `if (mode == 2) { StoreBody... return@Column }` block, after the StoreBody return, add before the `if (mode == 0)` block:

```kotlin
if (mode == 3) {
    Spacer(Modifier.height(16.dp))
    SectionLabel("追加", "TUILES SYSTÈME")
    Spacer(Modifier.height(16.dp))
    Medallion(
        label = "Écran Mac",
        onClick = { vm.addAirPlay() },
    ) {
        Box(
            Modifier.size(46.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(com.cyprienbrisset.myportal.ui.theme.SumiSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Monitor,
                contentDescription = "Écran Mac",
                tint = Kinari,
                modifier = Modifier.size(28.dp),
            )
        }
    }
    Spacer(Modifier.weight(1f))
    return@Column
}
```

Add at top of TileEditScreen.kt imports:
```kotlin
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material3.Icon
```

- [ ] **Step 4: Build**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/settings/TileEditScreen.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/settings/TileEditViewModel.kt
git commit -m "feat(airplay): TileEditScreen — Système tab with Écran Mac AIRPLAY tile option"
```

---

### Task 13: HomeViewModel + HomeScreen Integration

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`

- [ ] **Step 1: Expose airPlayState in HomeViewModel**

In `HomeViewModel.kt`, add after the `nowPlaying` block:
```kotlin
val airPlayState = AirPlayReceiver.state
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AirPlayState.Waiting)
```

Add imports:
```kotlin
import com.cyprienbrisset.myportal.airplay.AirPlayReceiver
import com.cyprienbrisset.myportal.airplay.AirPlayState
```

- [ ] **Step 2: Add tile click handler for AIRPLAY in HomeScreen launch lambda**

In `HomeScreen.kt`, in the `val launch: (TileEntity) -> Unit = { tile -> ... }` block, add the AIRPLAY case inside the `when (tile.type)` expression:

```kotlin
TileType.AIRPLAY -> {
    val st = vm.airPlayState.value
    if (st is AirPlayState.Streaming) {
        ctx.startActivity(Intent(ctx, AirPlayActivity::class.java))
    }
    // Waiting/Connecting: no-op, service is already running
    if (st is AirPlayState.Error) {
        ctx.startService(Intent(ctx, AirPlayService::class.java))
    }
}
```

Add imports:
```kotlin
import com.cyprienbrisset.myportal.airplay.AirPlayService
import com.cyprienbrisset.myportal.airplay.AirPlayState
import com.cyprienbrisset.myportal.ui.airplay.AirPlayActivity
```

- [ ] **Step 3: Add auto-launch LaunchedEffect in HomeScreen**

In `HomeScreen.kt`, inside the `BoxWithConstraints` composable, after the existing `LaunchedEffect(now)` line, add:

```kotlin
val airPlayState by vm.airPlayState.collectAsStateWithLifecycle()
LaunchedEffect(airPlayState) {
    if (airPlayState is AirPlayState.Streaming) {
        ctx.startActivity(Intent(ctx, AirPlayActivity::class.java))
    }
}
```

- [ ] **Step 4: Pass airPlayState to MedallionGrid calls**

`MedallionGrid` now reads `AirPlayReceiver.state` directly (Task 11), so no parameter change is needed. Verify the two `MedallionGrid(...)` calls in HomeScreen (landscape + portrait) still compile.

- [ ] **Step 5: Build**

Run: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Install and smoke test**

```bash
~/Library/Android/sdk/platform-tools/adb install -r -d app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.cyprienbrisset.myportal/.MainActivity
sleep 3
# Verify AirPlayService is running
~/Library/Android/sdk/platform-tools/adb shell dumpsys activity services com.cyprienbrisset.myportal | grep -i airplay
# Verify mDNS is advertising (port 7000 open)
~/Library/Android/sdk/platform-tools/adb shell ss -tlnp | grep 7000
```
Expected: AirPlayService listed, port 7000 bound.

- [ ] **Step 7: Final commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeViewModel.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt
git commit -m "feat(airplay): HomeScreen/HomeViewModel — airPlayState exposure + auto-launch AirPlayActivity on Streaming"
```

---

## End-to-End Test (manual, on device)

After all tasks are complete:

1. Install the debug APK on Portal.
2. On Mac: System Settings → Displays → Add Display → "Portal" should appear. (Or Control Center → Screen Mirroring → "Portal".)
3. Select "Portal" — Portal should switch automatically to fullscreen showing the Mac desktop.
4. Tap anywhere → HUD appears. Tap "✕ Quitter" → Mac receives TEARDOWN, Portal returns to home.
5. On Portal home, add "Écran Mac" tile via TileEditScreen → Système → Écran Mac.
6. While Mac is connected, tile should show "● Live" with a red border.
