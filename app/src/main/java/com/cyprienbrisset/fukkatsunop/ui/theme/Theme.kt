package com.cyprienbrisset.fukkatsunop.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue

private val DarkScheme = darkColorScheme(
    primary = AccentShu,
    onPrimary = OnShu,
    background = Sumi,
    onBackground = Kinari,
    surface = SumiSurface,
    onSurface = Kinari,
    surfaceVariant = SumiSurface,
    onSurfaceVariant = SumiMuted,
    outline = SumiLine,
    outlineVariant = SumiLine,
)

private val LightScheme = lightColorScheme(
    primary = AccentShu,
    onPrimary = OnShu,
    background = Washi,
    onBackground = Ink,
    surface = WashiCard,
    onSurface = Ink,
    surfaceVariant = WashiCard,
    onSurfaceVariant = InkMuted,
    outline = WashiLine,
    outlineVariant = WashiLine,
)

/** 7h–20h = mode jour (Washi), sinon mode nuit (Sumi). */
fun isDaytime(hour: Int) = hour in 7..19

@Composable
fun MyPortalTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    SideEffect { applyColorPalette(darkTheme) }

    val target = if (darkTheme) DarkScheme else LightScheme

    val background       by animateColorAsState(target.background,        tween(600), label = "bg")
    val surface          by animateColorAsState(target.surface,            tween(600), label = "surf")
    val onBackground     by animateColorAsState(target.onBackground,       tween(600), label = "onBg")
    val onSurface        by animateColorAsState(target.onSurface,          tween(600), label = "onSurf")
    val surfaceVariant   by animateColorAsState(target.surfaceVariant,     tween(600), label = "surfVar")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant,   tween(600), label = "onSurfVar")
    val outline          by animateColorAsState(target.outline,            tween(600), label = "outline")

    val animatedScheme = target.copy(
        background       = background,
        surface          = surface,
        onBackground     = onBackground,
        onSurface        = onSurface,
        surfaceVariant   = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline          = outline,
        outlineVariant   = outline,
    )

    MaterialTheme(
        colorScheme = animatedScheme,
        typography  = PortalTypography,
        content     = content,
    )
}
