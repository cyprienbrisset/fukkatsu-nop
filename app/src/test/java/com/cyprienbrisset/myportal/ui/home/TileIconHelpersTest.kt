package com.cyprienbrisset.fukkatsunop.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileIconHelpersTest {
    @Test fun monogramLetterIsFirstNonBlankUpper() {
        assertEquals("N", monogramLetter("netflix"))
        assertEquals("J", monogramLetter("  jellyfin"))
        assertEquals("?", monogramLetter("   "))
    }

    @Test fun monogramColorIsDeterministicAndStable() {
        assertEquals(monogramColor("Netflix"), monogramColor("Netflix"))
        assertTrue(monogramColor("Netflix") != monogramColor("Jellyfin"))
    }

    @Test fun faviconUrlExtractsHostAndSize() {
        assertEquals(
            "https://www.google.com/s2/favicons?sz=128&domain=jellyfin.local",
            faviconUrl("http://jellyfin.local:8096/web/index.html"),
        )
        assertEquals(
            "https://www.google.com/s2/favicons?sz=128&domain=youtube.com",
            faviconUrl("https://youtube.com"),
        )
    }
}
