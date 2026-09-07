package com.cyprienbrisset.fukkatsunop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = Shu,
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
    primary = Shu,
    onPrimary = OnShu,
    background = Washi,
    onBackground = Ink,
    surface = WashiSurface,
    onSurface = Ink,
    surfaceVariant = WashiSurface,
    onSurfaceVariant = InkMuted,
    outline = WashiLine,
    outlineVariant = WashiLine,
)

/** 7h–20h = mode jour (Washi), sinon mode nuit (Sumi). */
fun isDaytime(hour: Int) = hour in 7..19

@Composable
fun MyPortalTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = PortalTypography,
        content = content,
    )
}
