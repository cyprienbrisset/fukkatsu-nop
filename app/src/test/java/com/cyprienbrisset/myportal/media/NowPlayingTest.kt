package com.cyprienbrisset.fukkatsunop.media

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTest {
    @Test fun playingStateIsPlaying() {
        assertTrue(isPlaying(PlaybackState.STATE_PLAYING))
    }
    @Test fun otherStatesAreNotPlaying() {
        assertFalse(isPlaying(PlaybackState.STATE_PAUSED))
        assertFalse(isPlaying(PlaybackState.STATE_STOPPED))
        assertFalse(isPlaying(PlaybackState.STATE_NONE))
    }
    @Test fun indexOfActivePicksFirst() {
        assertEquals(0, indexOfActive(listOf(true, false)))
        assertEquals(1, indexOfActive(listOf(false, true)))
        assertEquals(0, indexOfActive(listOf(false, false)))
        assertEquals(-1, indexOfActive(emptyList()))
    }
}
