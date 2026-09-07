package com.cyprienbrisset.fukkatsunop.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// ── Accent — invariant across modes ─────────────────────────────────────────
val Shu   = Color(0xFFC1272D)
val OnShu = Color(0xFFF6EEE0)

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

// ── Raw palette values ───────────────────────────────────────────────────────
private val SumiRaw        = Color(0xFF0D0E12)
private val Ink2Raw        = Color(0xFF15171C)
private val SumiSurfaceRaw = Color(0xFF191C23)
private val SumiLineRaw    = Color(0xFF242832)
private val KinariRaw      = Color(0xFFECE7DD)
private val SumiMutedRaw   = Color(0xFF9A9488)

// Light palette (day / Washi)
val Washi        = Color(0xFFF2EDE3)
val WashiSurface = Color(0xFFE6E1D6)
val WashiLine    = Color(0xFFCCC7BC)
val Ink          = Color(0xFF14161C)
val InkMuted     = Color(0xFF6E6B61)

// ── Theme-reactive palette ───────────────────────────────────────────────────
// Backed by Compose State so any read during composition is tracked and
// triggers recomposition when the theme changes — no composable changes needed.
private val _sumi        = mutableStateOf(SumiRaw)
private val _ink2        = mutableStateOf(Ink2Raw)
private val _sumiSurface = mutableStateOf(SumiSurfaceRaw)
private val _sumiLine    = mutableStateOf(SumiLineRaw)
private val _kinari      = mutableStateOf(KinariRaw)
private val _sumiMuted   = mutableStateOf(SumiMutedRaw)

val Sumi:        Color get() = _sumi.value
val Ink2:        Color get() = _ink2.value
val SumiSurface: Color get() = _sumiSurface.value
val SumiLine:    Color get() = _sumiLine.value
val Kinari:      Color get() = _kinari.value
val SumiMuted:   Color get() = _sumiMuted.value

fun applyColorPalette(dark: Boolean) {
    _sumi.value        = if (dark) SumiRaw        else Washi
    _ink2.value        = if (dark) Ink2Raw        else WashiSurface
    _sumiSurface.value = if (dark) SumiSurfaceRaw else WashiSurface
    _sumiLine.value    = if (dark) SumiLineRaw    else WashiLine
    _kinari.value      = if (dark) KinariRaw      else Ink
    _sumiMuted.value   = if (dark) SumiMutedRaw   else InkMuted
}
