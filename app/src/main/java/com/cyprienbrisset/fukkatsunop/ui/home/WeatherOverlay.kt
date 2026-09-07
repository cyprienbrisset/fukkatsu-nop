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
import androidx.compose.ui.geometry.Size
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

private enum class WeatherEffect { NONE, CLOUDY, RAIN, STORM, SNOW, FOG }

private fun descToEffect(description: String): WeatherEffect = when (description) {
    "Nuageux"          -> WeatherEffect.CLOUDY
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
    val cloudColor    = if (isDark) Color(0xFFDDD8CC) else Color(0xFFF5F2EE)

    when (effect) {
        WeatherEffect.CLOUDY -> CloudCanvas(cloudColor, modifier)
        WeatherEffect.RAIN   -> RainCanvas(particleColor, modifier)
        WeatherEffect.STORM  -> StormCanvas(particleColor, modifier)
        WeatherEffect.SNOW   -> SnowCanvas(particleColor, modifier)
        WeatherEffect.FOG    -> FogCanvas(fogBase, modifier)
        WeatherEffect.NONE   -> Unit
    }
}

// ── Cloudy ───────────────────────────────────────────────────────────────────

private data class CloudDef(
    val yFrac: Float,   // position verticale (0=haut, 1=bas)
    val wFrac: Float,   // largeur relative à l'écran
    val alpha: Float,   // opacité de chaque bosse
    val speed: Float,   // nb de traversées / cycle de 30s
    val phase: Float,   // décalage initial
)

private val CLOUDS = listOf(
    CloudDef(0.08f, 0.28f, 0.72f, 1.00f, 0.00f),
    CloudDef(0.22f, 0.38f, 0.60f, 0.65f, 0.40f),
    CloudDef(0.10f, 0.20f, 0.65f, 1.30f, 0.68f),
    CloudDef(0.32f, 0.32f, 0.55f, 0.78f, 0.20f),
    CloudDef(0.16f, 0.24f, 0.62f, 1.10f, 0.55f),
    CloudDef(0.28f, 0.34f, 0.50f, 0.52f, 0.83f),
)

// Nuage = base plate + 5 bosses qui se chevauchent (radii en w → overlap garanti)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    cx: Float, cy: Float, w: Float, alpha: Float, color: Color,
) {
    val c = color.copy(alpha = alpha)
    val r = w * 0.22f  // rayon bosse = 22% largeur ; spacing = 18% → large overlap

    // Base plate (fond plat du nuage)
    drawOval(c, topLeft = Offset(cx - w * 0.50f, cy - w * 0.08f), size = Size(w, w * 0.28f))
    // 5 bosses en quinconce, toutes en coordonnées *w* pour rester rondes
    drawCircle(c, radius = r * 0.75f, center = Offset(cx - w * 0.30f, cy + w * 0.04f))
    drawCircle(c, radius = r * 0.90f, center = Offset(cx - w * 0.12f, cy - w * 0.18f))
    drawCircle(c, radius = r * 1.00f, center = Offset(cx + w * 0.06f, cy - w * 0.26f))
    drawCircle(c, radius = r * 0.85f, center = Offset(cx + w * 0.24f, cy - w * 0.16f))
    drawCircle(c, radius = r * 0.72f, center = Offset(cx + w * 0.38f, cy + w * 0.03f))
}

@Composable
private fun CloudCanvas(cloudColor: Color, modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "clouds")
    val t by tr.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing)),
        label = "cloud-t",
    )

    Canvas(modifier.fillMaxSize()) {
        CLOUDS.forEach { cloud ->
            val cloudW = size.width * cloud.wFrac
            val xFrac = (t * cloud.speed + cloud.phase) % 1.2f
            val cx = -cloudW + xFrac * (size.width + cloudW * 2f)
            val cy = cloud.yFrac * size.height
            drawCloud(cx, cy, cloudW, cloud.alpha, cloudColor)
        }
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
