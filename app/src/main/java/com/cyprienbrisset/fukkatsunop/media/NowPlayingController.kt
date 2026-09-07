package com.cyprienbrisset.fukkatsunop.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.cyprienbrisset.fukkatsunop.system.MediaListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NowPlayingController(private val context: Context) {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state

    private val component = ComponentName(context, MediaListenerService::class.java)
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private var controller: MediaController? = null

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { refreshFromController() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { refreshFromController() }
        override fun onSessionDestroyed() { refresh() }
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /**
     * If WRITE_SECURE_SETTINGS is granted (via provision script), self-activate the notification
     * listener without requiring the user to navigate to Settings → Notification Access.
     * Safe to call repeatedly — no-ops if already enabled.
     */
    fun ensureListenerEnabled() {
        val serviceComponent = ComponentName(context, MediaListenerService::class.java)
        if (hasAccess()) {
            // Already in the allowed list — request a rebind in case the service isn't live yet.
            // This happens when the setting was written via ADB but the system didn't auto-bind.
            runCatching { NotificationListenerService.requestRebind(serviceComponent) }
            return
        }
        // Add to enabled_notification_listeners via WRITE_SECURE_SETTINGS (granted by provision script).
        val serviceId = "${context.packageName}/.system.MediaListenerService"
        val current = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        }.getOrNull() ?: ""
        if (!current.split(":").contains(serviceId)) {
            val updated = if (current.isEmpty() || current == "null") serviceId else "$current:$serviceId"
            runCatching {
                Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", updated)
            }
        }
        runCatching { NotificationListenerService.requestRebind(serviceComponent) }
    }

    fun refresh() {
        ensureListenerEnabled()
        if (!hasAccess()) { detach(); controller = null; _state.value = null; return }
        val sessions = try { msm.getActiveSessions(component) } catch (e: SecurityException) { emptyList() }
        val playingFlags = sessions.map { isPlaying(it.playbackState?.state ?: PlaybackState.STATE_NONE) }
        val idx = indexOfActive(playingFlags)
        val next = if (idx >= 0) sessions.getOrNull(idx) else null
        if (next?.sessionToken != controller?.sessionToken) {
            detach()
            controller = next
            controller?.registerCallback(cb)
        }
        refreshFromController()
    }

    private fun refreshFromController() {
        val c = controller
        if (c == null) { _state.value = null; return }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val ps = c.playbackState
        val playing = isPlaying(ps?.state ?: PlaybackState.STATE_NONE)
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        // Extrapolate the live position from the last reported snapshot.
        val position = if (ps != null && ps.position >= 0) {
            val elapsed = if (playing) {
                ((android.os.SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
            } else 0L
            (ps.position + elapsed).let { if (duration > 0) it.coerceIn(0, duration) else it.coerceAtLeast(0) }
        } else -1L
        val canSeek = ps != null && (ps.actions and PlaybackState.ACTION_SEEK_TO) != 0L
        _state.value = if (title.isBlank() && artist.isBlank()) null
            else NowPlaying(title, artist, playing, art, position, duration, canSeek, c.packageName)
    }

    fun seekTo(positionMs: Long) { controller?.transportControls?.seekTo(positionMs) }

    fun toggle() {
        val c = controller ?: return
        if (isPlaying(c.playbackState?.state ?: PlaybackState.STATE_NONE)) c.transportControls.pause()
        else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }

    private fun detach() { controller?.unregisterCallback(cb) }
    fun dispose() { detach(); controller = null }
}
