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
import androidx.compose.ui.graphics.Path
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
    val particleColor = if (isDark) Kinari.copy(alpha = 0.18f) else Ink.copy(alpha = 0.10f)
    val fogBase       = if (isDark) SumiMuted else InkMuted
    // Nuit : beige clair sur fond sombre. Jour : bleu-gris bien visible sur fond crème Washi.
    val cloudColor    = if (isDark) Color(0xFFE0DBD0) else Color(0xFF8AAAC0)

    val effect = remember(weather?.description) {
        weather?.description?.let { descToEffect(it) } ?: WeatherEffect.NONE
    }

    // Nuages toujours présents — décoration ambiante du launcher.
    CloudCanvas(cloudColor, modifier)

    // Effets météo additionnels par-dessus les nuages.
    when (effect) {
        WeatherEffect.RAIN  -> RainCanvas(particleColor, modifier)
        WeatherEffect.STORM -> StormCanvas(particleColor, modifier)
        WeatherEffect.SNOW  -> SnowCanvas(particleColor, modifier)
        WeatherEffect.FOG   -> FogCanvas(fogBase, modifier)
        else                -> Unit
    }
}

// ── Cloudy ───────────────────────────────────────────────────────────────────

private data class CloudDef(
    val yFrac:    Float,  // position verticale (0=haut, 1=bas)
    val wFrac:    Float,  // largeur relative à l'écran
    val alpha:    Float,  // opacité du corps
    val durationMs: Float, // durée d'une traversée complète (ms)
    val phase:    Float,  // décalage initial dans le cycle [0,1[
)

// durée en ms pour traverser l'écran (de hors-gauche à hors-droite)
private val CLOUDS = listOf(
    CloudDef(0.12f, 0.30f, 0.22f, 30_000f, 0.00f),
    CloudDef(0.26f, 0.40f, 0.20f, 45_000f, 0.40f),
    CloudDef(0.14f, 0.22f, 0.21f, 22_000f, 0.68f),
    CloudDef(0.36f, 0.34f, 0.18f, 38_000f, 0.20f),
    CloudDef(0.20f, 0.26f, 0.20f, 27_000f, 0.55f),
    CloudDef(0.32f, 0.36f, 0.17f, 50_000f, 0.83f),
)

// Espace de référence : 242 × 84. Centre visuel vertical ≈ y 38.
private const val CLOUD_W  = 242f
private const val CLOUD_CY =  38f

// Construit le path directement en coordonnées écran — évite withTransform,
// cassé pour drawPath sur Android 9 (Portal).
private fun cloudPath(cx: Float, cy: Float, w: Float): Path {
    val s  = w / CLOUD_W
    val tx = cx - w * 0.50f
    val ty = cy - CLOUD_CY * s
    fun x(v: Float) = tx + v * s
    fun y(v: Float) = ty + v * s
    return Path().apply {
        moveTo(x(10f),  y(80f))
        cubicTo(x(0f),  y(80f),  x(0f),  y(55f),  x(15f), y(48f))
        cubicTo(x(10f), y(22f),  x(30f), y(12f),  x(50f), y(25f))
        cubicTo(x(65f), y(32f),  x(72f), y(38f),  x(80f), y(35f))
        cubicTo(x(78f), y(5f),   x(118f),y(-4f),  x(140f),y(18f))
        cubicTo(x(155f),y(8f),   x(180f),y(8f),   x(195f),y(28f))
        cubicTo(x(210f),y(18f),  x(230f),y(35f),  x(228f),y(55f))
        cubicTo(x(238f),y(58f),  x(242f),y(72f),  x(232f),y(80f))
        close()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    cx: Float, cy: Float, w: Float, alpha: Float, color: Color,
) {
    drawPath(cloudPath(cx, cy, w * 1.04f), color.copy(alpha = alpha * 0.25f))  // halo
    drawPath(cloudPath(cx, cy, w),         color.copy(alpha = alpha))            // corps
}

@Composable
private fun CloudCanvas(cloudColor: Color, modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "clouds")
    // Une animation par nuage : redémarre toujours hors-écran (xFrac=0 → off-gauche,
    // xFrac=1 → off-droite), donc le wrap est invisible. Phase pour les distribuer d'emblée.
    val t0 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(CLOUDS[0].durationMs.toInt(), easing = LinearEasing)), "c0")
    val t1 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(CLOUDS[1].durationMs.toInt(), easing = LinearEasing)), "c1")
    val t2 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(CLOUDS[2].durationMs.toInt(), easing = LinearEasing)), "c2")
    val t3 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(CLOUDS[3].durationMs.toInt(), easing = LinearEasing)), "c3")
    val t4 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(CLOUDS[4].durationMs.toInt(), easing = LinearEasing)), "c4")
    val t5 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(CLOUDS[5].durationMs.toInt(), easing = LinearEasing)), "c5")
    val ts = listOf(t0, t1, t2, t3, t4, t5)

    Canvas(modifier.fillMaxSize()) {
        CLOUDS.forEachIndexed { i, cloud ->
            val cloudW = size.width * cloud.wFrac
            // % 1.0f : le wrap se produit quand cx passe de hors-droite à hors-gauche → invisible.
            val xFrac  = (ts[i] + cloud.phase) % 1.0f
            val cx     = -cloudW + xFrac * (size.width + cloudW * 2f)
            val cy     = cloud.yFrac * size.height
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
