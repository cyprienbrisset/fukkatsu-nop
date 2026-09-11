package com.cyprienbrisset.fukkatsunop.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// ── Accent — invariant across modes ─────────────────────────────────────────
val Shu    = Color(0xFFC1272D)
val OnShu  = Color(0xFFF6EEE0)

val Sakura = Color(0xFFE8A0AF)
val Momiji = Color(0xFFC85A14)
val Indigo = Color(0xFF5B6EE8)
val Matcha = Color(0xFF4D8A5F)
val Nuit   = Color(0xFF8557CE)
val Or     = Color(0xFFC89020)

private val _accentOverrideState = mutableStateOf("AUTO")

fun applyAccentOverride(override: String) { _accentOverrideState.value = override }

val AccentShu: Color get() {
    return when (_accentOverrideState.value) {
        "SAKURA" -> Sakura
        "MOMIJI" -> Momiji
        "INDIGO" -> Indigo
        "MATCHA" -> Matcha
        "NUIT"   -> Nuit
        "OR"     -> Or
        "SHU"    -> Shu
        else     -> {
            val month = java.time.LocalDate.now().monthValue
            when (month) {
                3, 4, 5   -> Sakura
                9, 10, 11 -> Momiji
                else      -> Shu
            }
        }
    }
}

// ── Background tone ──────────────────────────────────────────────────────────
private val _bgToneState = mutableStateOf("DEFAULT")
fun applyBgTone(tone: String) { _bgToneState.value = tone }
val BgTone: Color get() = when (_bgToneState.value) {
    "INDIGO" -> Color(0xFF080C1A)
    "MATCHA" -> Color(0xFF060E08)
    "NUIT"   -> Color(0xFF0D0714)
    "OR"     -> Color(0xFF130F06)
    "SAKURA" -> Color(0xFF150810)
    "MOMIJI" -> Color(0xFF150A05)
    "SHU"    -> Color(0xFF160608)
    else     -> Color(0xFF0D0E12)
}

// ── Icon shape ────────────────────────────────────────────────────────────────
private val _iconShapeState = mutableStateOf(false)
fun applyIconShape(enabled: Boolean) { _iconShapeState.value = enabled }
val IconShapeEnabled: Boolean get() = _iconShapeState.value

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
val InkMuted     = Color(0xFF4A4640)

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

// Light-mode card and sub-surface colors — distinctly lighter than the Washi page bg
// so cards float visibly on the parchment background (same logic as white cards on gray in MD3)
val WashiCard    = Color(0xFFFAF8F4)  // near-white card surface
private val WashiSubCard = Color(0xFFE0DBD0)  // darker slot (artwork placeholder, etc.)

fun applyColorPalette(dark: Boolean) {
    _sumi.value        = if (dark) SumiRaw        else Washi
    _ink2.value        = if (dark) Ink2Raw        else WashiSubCard
    _sumiSurface.value = if (dark) SumiSurfaceRaw else WashiCard
    _sumiLine.value    = if (dark) SumiLineRaw    else WashiLine
    _kinari.value      = if (dark) KinariRaw      else Ink
    _sumiMuted.value   = if (dark) SumiMutedRaw   else InkMuted
}
