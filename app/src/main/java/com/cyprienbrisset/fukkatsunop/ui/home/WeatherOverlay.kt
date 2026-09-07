package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.fukkatsunop.data.weather.Weather
import com.cyprienbrisset.fukkatsunop.ui.theme.Ink
import com.cyprienbrisset.fukkatsunop.ui.theme.InkMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import kotlin.math.PI
import kotlin.math.sin

private enum class WeatherEffect { NONE, RAIN, STORM, SNOW, FOG }

private fun descToEffect(description: String): WeatherEffect = when (description) {
    "Pluie", "Averses" -> WeatherEffect.RAIN
    "Orage"            -> WeatherEffect.STORM
    "Neige"            -> WeatherEffect.SNOW
    "Brouillard"       -> WeatherEffect.FOG
    else               -> WeatherEffect.NONE
}

@Composable
fun WeatherOverlay(weather: Weather?, isDark: Boolean, modifier: Modifier = Modifier) {
    val effect = remember(weather?.description) {
        weather?.description?.let { descToEffect(it) } ?: WeatherEffect.NONE
    }
    if (effect == WeatherEffect.NONE) return

    val particleColor = if (isDark) Kinari.copy(alpha = 0.18f) else Ink.copy(alpha = 0.10f)
    val fogBase       = if (isDark) SumiMuted else InkMuted

    when (effect) {
        WeatherEffect.RAIN  -> RainCanvas(particleColor, modifier)
        WeatherEffect.STORM -> StormCanvas(particleColor, modifier)
        WeatherEffect.SNOW  -> SnowCanvas(particleColor, modifier)
        WeatherEffect.FOG   -> FogCanvas(fogBase, modifier)
        WeatherEffect.NONE  -> Unit
    }
}

// ── Rain ─────────────────────────────────────────────────────────────────────

@Composable
private fun RainCanvas(color: Color, modifier: Modifier) {
    val n = 42
    val xs     = remember { Array(n) { Math.random().toFloat() } }
    val phases = remember { Array(n) { Math.random().toFloat() } }

    val tr = rememberInfiniteTransition(label = "rain")
    val t by tr.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "rain-t")

    Canvas(modifier.fillMaxSize()) {
        val strokeLen = 13.dp.toPx()
        repeat(n) { i ->
            val x = xs[i] * size.width
            val y = ((t + phases[i]) % 1f) * size.height
            drawLine(color, Offset(x, y), Offset(x - 1.5.dp.toPx(), y + strokeLen),
                strokeWidth = 1.2.dp.toPx())
        }
    }
}

// ── Storm ────────────────────────────────────────────────────────────────────

@Composable
private fun StormCanvas(color: Color, modifier: Modifier) {
    val n = 48
    val xs     = remember { Array(n) { Math.random().toFloat() } }
    val phases = remember { Array(n) { Math.random().toFloat() } }

    val tr = rememberInfiniteTransition(label = "storm")
    val t by tr.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "storm-t")
    val flash by tr.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 5000
            0f at 0
            0f at 4200
            0.07f at 4300
            0f at 4400
            0.04f at 4550
            0f at 5000
        }),
        label = "lightning",
    )

    Canvas(modifier.fillMaxSize()) {
        val strokeLen = 15.dp.toPx()
        repeat(n) { i ->
            val x = xs[i] * size.width
            val y = ((t + phases[i]) % 1f) * size.height
            drawLine(color, Offset(x, y), Offset(x - 2.dp.toPx(), y + strokeLen),
                strokeWidth = 1.4.dp.toPx())
        }
        if (flash > 0f) drawRect(Color.White.copy(alpha = flash))
    }
}

// ── Snow ─────────────────────────────────────────────────────────────────────

@Composable
private fun SnowCanvas(color: Color, modifier: Modifier) {
    val n = 28
    val xs     = remember { Array(n) { Math.random().toFloat() } }
    val phases = remember { Array(n) { Math.random().toFloat() } }
    val drifts = remember { Array(n) { (Math.random() * 0.05 - 0.025).toFloat() } }

    val tr = rememberInfiniteTransition(label = "snow")
    val t by tr.animateFloat(0f, 1f,
        infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "snow-t")

    Canvas(modifier.fillMaxSize()) {
        val r = 2.6.dp.toPx()
        repeat(n) { i ->
            val frac = (t + phases[i]) % 1f
            val drift = sin(frac * 2 * PI + i * 1.3).toFloat() * drifts[i]
            val x = ((xs[i] + drift + 1f) % 1f) * size.width
            val y = frac * size.height
            drawCircle(color, radius = r, center = Offset(x, y))
        }
    }
}

// ── Fog ──────────────────────────────────────────────────────────────────────

@Composable
private fun FogCanvas(baseColor: Color, modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "fog")
    val alpha by tr.animateFloat(
        initialValue = 0.07f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fog-alpha",
    )

    Canvas(modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    baseColor.copy(alpha = alpha * 0.4f),
                    baseColor.copy(alpha = alpha),
                ),
                startY = size.height * 0.42f,
                endY = size.height,
            ),
        )
    }
}
