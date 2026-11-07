package com.cyprienbrisset.fukkatsunop.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue

// Glass on the Fuji photo: transparent background, translucent surfaces, cream ink.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF4F1EA),
    onPrimary = Color(0xFF16212B),
    secondary = Color(0xFFF4F1EA),
    onSecondary = Color(0xFF16212B),
    background = Color.Transparent,
    onBackground = Color(0xFFF4F1EA),
    surface = Color(0xF21A1F2B),
    onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0x1AFFFFFF),
    onSurfaceVariant = Color(0xBFF4F1EA),
    surfaceContainer = Color(0xF21A1F2B),
    surfaceContainerHigh = Color(0xF2222836),
    surfaceContainerHighest = Color(0xF22A3040),
    outline = Color(0x29FFFFFF),
    outlineVariant = Color(0x29FFFFFF),
)

private val LightScheme = DarkScheme

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
