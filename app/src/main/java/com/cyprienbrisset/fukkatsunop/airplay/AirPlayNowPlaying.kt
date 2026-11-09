package com.cyprienbrisset.fukkatsunop.airplay

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * What an iPhone or a Mac plays over AirPlay, published as a MediaSession so the home
 * "now playing" card shows it like any music app.
 *
 * The sender pushes it with SET_PARAMETER: DMAP tags (title, artist, album), the cover
 * (image/jpeg) and the progress (RTP timestamps). The card buttons go back to the sender
 * through DACP: GET /ctrl-int/1/<command> on its "iTunes_Ctrl_<DACP-ID>" service.
 */
object AirPlayNowPlaying {
    private var session: MediaSession? = null
    private var nsd: NsdManager? = null

    private var title = ""
    private var artist = ""
    private var album = ""
    private var art: android.graphics.Bitmap? = null
    private var durationMs = -1L
    private var positionMs = 0L
    private var positionAt = 0L
    private var playing = false

    private var dacpId: String? = null
    private var activeRemote: String? = null
    private var senderAddress: InetAddress? = null
    @Volatile private var dacpEndpoint: Pair<InetAddress, Int>? = null

    fun init(context: Context) {
        if (session != null) return
        nsd = context.getSystemService(NsdManager::class.java)
        session = MediaSession(context, "AirPlay").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = command("play")
                override fun onPause() = command("pause")
                override fun onSkipToNext() = command("nextitem")
                override fun onSkipToPrevious() = command("previtem")
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
    }

    /** DMAP-tagged metadata of the current track. */
    @Synchronized fun setMetadata(dmap: ByteArray) {
        val tags = Dmap.strings(dmap)
        title = tags["minm"].orEmpty()
        artist = tags["asar"] ?: tags["asaa"].orEmpty()
        album = tags["asal"].orEmpty()
        Timber.d("AirPlay now playing: $title — $artist")
        publish()
    }

    /** Cover of the current track; an empty body means the track has none. */
    @Synchronized fun setArtwork(image: ByteArray) {
        art = if (image.isEmpty()) null else runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 512) sample *= 2
            BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
        publish()
    }

    /** "progress: start/current/end" in RTP timestamps at [sampleRate]. */
    @Synchronized fun setProgress(text: String, sampleRate: Int = 44100) {
        val p = parseProgress(text, sampleRate) ?: return
        positionMs = p.first; durationMs = p.second; positionAt = SystemClock.elapsedRealtime()
        publish()
    }

    @Synchronized fun setPlaying(value: Boolean) {
        if (value == playing) return
        positionMs = currentPosition(); positionAt = SystemClock.elapsedRealtime()
        playing = value
        publish()
    }

    /** DACP-ID and Active-Remote come with the RTSP requests of the sender. */
    @Synchronized fun setRemote(id: String?, remote: String?, address: InetAddress?) {
        if (id == null || remote == null) return
        if (id != dacpId) dacpEndpoint = null
        dacpId = id; activeRemote = remote; senderAddress = address
    }

    @Synchronized fun clear() {
        title = ""; artist = ""; album = ""; art = null; durationMs = -1; positionMs = 0; playing = false
        session?.isActive = false
    }

    private fun currentPosition(): Long =
        if (playing) positionMs + (SystemClock.elapsedRealtime() - positionAt) else positionMs

    private fun publish() {
        val s = session ?: return
        if (title.isBlank() && artist.isBlank()) return
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs, if (playing) 1f else 0f, positionAt,
                )
                .build(),
        )
        s.isActive = true
    }

    private fun command(cmd: String) {
        val remote = activeRemote ?: return
        Thread({
            val ep = dacpEndpoint ?: resolveDacp() ?: run { Timber.w("AirPlay: no DACP service, '$cmd' not sent"); return@Thread }
            runCatching {
                val c = URL("http", ep.first.hostAddress, ep.second, "/ctrl-int/1/$cmd").openConnection() as HttpURLConnection
                c.setRequestProperty("Active-Remote", remote)
                c.connectTimeout = 3000; c.readTimeout = 3000
                Timber.d("AirPlay DACP $cmd → ${c.responseCode}")
                c.disconnect()
            }.onFailure { Timber.w("AirPlay DACP $cmd failed: ${it.message}"); dacpEndpoint = null }
        }, "airplay-dacp").start()
        // Answer at once on screen; the sender confirms with its next FLUSH / audio.
        when (cmd) { "play" -> setPlaying(true); "pause" -> setPlaying(false) }
    }

    /** Finds "iTunes_Ctrl_<DACP-ID>" with mDNS (blocking, up to 4 s). */
    private fun resolveDacp(): Pair<InetAddress, Int>? {
        val id = dacpId ?: return null
        val mgr = nsd ?: return null
        val wanted = "iTunes_Ctrl_$id"
        val found = java.util.concurrent.LinkedBlockingQueue<Pair<InetAddress, Int>>(1)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceName.equals(wanted, ignoreCase = true)) return
                mgr.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(r: NsdServiceInfo) { found.offer((senderAddress ?: r.host) to r.port) }
                    override fun onResolveFailed(r: NsdServiceInfo, code: Int) = Unit
                })
            }
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        return try {
            mgr.discoverServices("_dacp._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            found.poll(4, java.util.concurrent.TimeUnit.SECONDS)?.also { dacpEndpoint = it }
        } finally {
            runCatching { mgr.stopServiceDiscovery(listener) }
        }
    }

    /** Position and duration in ms from "start/current/end", or null. */
    fun parseProgress(text: String, sampleRate: Int = 44100): Pair<Long, Long>? {
        val m = Regex("progress:\\s*(\\d+)/(\\d+)/(\\d+)").find(text) ?: return null
        val (start, cur, end) = m.destructured.toList().map { it.toLong() }
        // RTP timestamps are 32-bit and may wrap between start and end.
        fun span(a: Long, b: Long) = (b - a) and 0xFFFFFFFFL
        return span(start, cur) * 1000 / sampleRate to span(start, end) * 1000 / sampleRate
    }
}

/** DMAP: 4-byte tag, 4-byte big-endian length, data; containers nest the same records. */
object Dmap {
    private val containers = setOf("mlit", "mlcl", "mdcl", "cmst", "cmgt", "apso", "adbs", "aply", "msrv")
    private val stringTags = setOf("minm", "asar", "asaa", "asal", "asgn", "ascp", "ascm")

    fun strings(data: ByteArray): Map<String, String> {
        val out = mutableMapOf<String, String>()
        walk(data, 0, data.size, out)
        return out
    }

    private fun walk(d: ByteArray, from: Int, to: Int, out: MutableMap<String, String>) {
        var p = from
        while (p + 8 <= to) {
            val tag = String(d, p, 4, Charsets.US_ASCII)
            val len = ((d[p + 4].toInt() and 0xFF) shl 24) or ((d[p + 5].toInt() and 0xFF) shl 16) or
                ((d[p + 6].toInt() and 0xFF) shl 8) or (d[p + 7].toInt() and 0xFF)
            val start = p + 8
            if (len < 0 || start + len > to) return
            when (tag) {
                in containers -> walk(d, start, start + len, out)
                in stringTags -> out.putIfAbsent(tag, String(d, start, len, Charsets.UTF_8))
            }
            p = start + len
        }
    }
}
