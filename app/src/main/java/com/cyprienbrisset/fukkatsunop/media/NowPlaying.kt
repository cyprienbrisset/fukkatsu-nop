package com.cyprienbrisset.fukkatsunop.media

import android.graphics.Bitmap
import android.media.session.PlaybackState

data class NowPlaying(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val art: Bitmap? = null,
    /** Current position in ms, or -1 if unknown. */
    val positionMs: Long = -1,
    /** Track duration in ms, or -1 if unknown/unbounded (e.g. live streams). */
    val durationMs: Long = -1,
    /** Whether the session advertises seek support. */
    val canSeek: Boolean = false,
    /** Package of the app that owns the media session, for "open now-playing app". */
    val packageName: String? = null,
)

fun isPlaying(state: Int): Boolean = state == PlaybackState.STATE_PLAYING

/** First "active" (playing) index, else 0 if any exist, else -1. */
fun indexOfActive(playing: List<Boolean>): Int {
    if (playing.isEmpty()) return -1
    val i = playing.indexOfFirst { it }
    return if (i >= 0) i else 0
}
