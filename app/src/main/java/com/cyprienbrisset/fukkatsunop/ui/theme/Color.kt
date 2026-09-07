package com.cyprienbrisset.fukkatsunop.ui.theme

import androidx.compose.ui.graphics.Color

// Dark palette (night)
val Sumi = Color(0xFF0D0E12)
val Ink2 = Color(0xFF15171C)
val SumiSurface = Color(0xFF191C23)
val SumiLine = Color(0xFF242832)
val Kinari = Color(0xFFECE7DD)
val SumiMuted = Color(0xFF9A9488)

// Accent — invariant across modes
val Shu = Color(0xFFC1272D)
val OnShu = Color(0xFFF6EEE0)

// Seasonal accents
val Sakura = Color(0xFFE8A0AF)
val Momiji = Color(0xFFC85A14)

val AccentShu: Color get() {
    val month = java.time.LocalDate.now().monthValue
    return when (month) {
        3, 4, 5   -> Sakura
        9, 10, 11 -> Momiji
        else      -> Shu
    }
}

// Light palette (day / Washi)
val Washi = Color(0xFFF2EDE3)
val WashiSurface = Color(0xFFE6E1D6)
val WashiLine = Color(0xFFCCC7BC)
val Ink = Color(0xFF14161C)
val InkMuted = Color(0xFF6E6B61)
