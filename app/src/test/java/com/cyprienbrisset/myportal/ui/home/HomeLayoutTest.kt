package com.cyprienbrisset.fukkatsunop.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {
    @Test fun landscapeWhenWide() {
        assertEquals(HomeLayoutMode.LANDSCAPE, homeLayoutFor(widthDp = 1280, isLandscape = true))
    }
    @Test fun portraitWhenTallAndNarrow() {
        assertEquals(HomeLayoutMode.PORTRAIT, homeLayoutFor(widthDp = 800, isLandscape = false))
    }
    @Test fun narrowLandscapeStillLandscape() {
        assertEquals(HomeLayoutMode.LANDSCAPE, homeLayoutFor(widthDp = 900, isLandscape = true))
    }
    @Test fun tileWidthsDifferPerMode() {
        assertEquals(180, HomeLayoutMode.LANDSCAPE.minTileWidthDp)
        assertEquals(160, HomeLayoutMode.PORTRAIT.minTileWidthDp)
    }
}
