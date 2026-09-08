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
import androidx.compose.ui.graphics.drawscope.withTransform
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
    val cloudColor    = if (isDark) Color(0xFFDDD8CC) else Color(0xFFF5F2EE)

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

// ── Nuage SVG-style ──────────────────────────────────────────────────────────
// Silhouette cumulus en courbes de Bézier cubiques — CC0, dessinée à la main.
// Espace de référence : 242 × 84 (rapport ~3:1). Centre visuel vertical ≈ y 38.
private val CLOUD_PATH: Path by lazy {
    Path().apply {
        moveTo(10f,  80f)
        cubicTo(  0f, 80f,   0f,  55f,  15f,  48f)  // flanc gauche montant
        cubicTo( 10f, 22f,  30f,  12f,  50f,  25f)  // bosse gauche
        cubicTo( 65f, 32f,  72f,  38f,  80f,  35f)  // creux centre-gauche
        cubicTo( 78f,  5f, 118f,  -4f, 140f,  18f)  // bosse centrale (plus haute)
        cubicTo(155f,  8f, 180f,   8f, 195f,  28f)  // bosse droite
        cubicTo(210f, 18f, 230f,  35f, 228f,  55f)  // pente droite
        cubicTo(238f, 58f, 242f,  72f, 232f,  80f)  // flanc droit descendant
        close()
    }
}
private const val CLOUD_W  = 242f
private const val CLOUD_CY =  38f  // centre vertical dans l'espace du path

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    cx: Float, cy: Float, w: Float, alpha: Float, color: Color,
) {
    val s = w / CLOUD_W
    // Halo doux : même forme, légèrement agrandie et plus transparente
    withTransform({
        translate(cx - w * 0.52f, cy - CLOUD_CY * s * 1.04f)
        scale(s * 1.04f, s * 1.04f)
    }) {
        drawPath(CLOUD_PATH, color.copy(alpha = alpha * 0.25f))
    }
    // Corps principal
    withTransform({
        translate(cx - w * 0.50f, cy - CLOUD_CY * s)
        scale(s, s)
    }) {
        drawPath(CLOUD_PATH, color.copy(alpha = alpha))
    }
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
