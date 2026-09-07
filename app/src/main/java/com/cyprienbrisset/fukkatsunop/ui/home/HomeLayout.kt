package com.cyprienbrisset.fukkatsunop.ui.home

enum class HomeLayoutMode(val minTileWidthDp: Int) {
    LANDSCAPE(180),
    PORTRAIT(160),
}

/** Orientation is the primary signal; width is a tiebreaker for square-ish screens. */
fun homeLayoutFor(widthDp: Int, isLandscape: Boolean): HomeLayoutMode =
    if (isLandscape || widthDp >= 900) HomeLayoutMode.LANDSCAPE else HomeLayoutMode.PORTRAIT
