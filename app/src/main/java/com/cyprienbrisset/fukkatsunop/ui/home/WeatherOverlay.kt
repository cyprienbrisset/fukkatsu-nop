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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
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
    if (weather == null) return

    val particleColor = if (isDark) Kinari.copy(alpha = 0.18f) else Ink.copy(alpha = 0.10f)
    val fogBase       = if (isDark) SumiMuted else InkMuted
    // Nuit : beige clair sur fond sombre. Jour : bleu-gris bien visible sur fond crème Washi.
    val cloudColor    = if (isDark) Color(0xFFE0DBD0) else Color(0xFF8AAAC0)

    val effect = remember(weather.description) {
        descToEffect(weather.description)
    }

    CloudCanvas(cloudColor, modifier)

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
    val yFrac:      Float,  // position verticale (0=haut, 1=bas)
    val wFrac:      Float,  // largeur relative à l'écran
    val alpha:      Float,  // opacité du corps
    val durationMs: Float,  // durée d'une traversée complète (ms)
    val phase:      Float,  // décalage initial dans le cycle [0,1[
)

private fun randF(lo: Float, hi: Float) = (lo + Math.random() * (hi - lo)).toFloat()

// Belly aléatoire par nuage (fond arrondi, pas plat).
private fun randomBelly() = randF(6f, 16f)

private val CLOUDS = listOf(
    CloudDef(0.12f, 0.30f, 0.22f, 70_000f, 0.00f),
    CloudDef(0.26f, 0.40f, 0.20f, 110_000f, 0.40f),
    CloudDef(0.14f, 0.22f, 0.21f, 55_000f, 0.68f),
)

// Espace de référence : 242 × 84. Centre visuel vertical ≈ y 38.
private const val CLOUD_W  = 242f
private const val CLOUD_CY =  38f

// 6 templates structurellement distincts (nb de bosses, proportions, asymétrie différents).
// belly : fond arrondi aléatoire par nuage.  Pas de withTransform (cassé Android 9).
private fun cloudPath(cx: Float, cy: Float, w: Float, template: Int, belly: Float): Path {
    val s  = w / CLOUD_W
    val tx = cx - w * 0.50f
    val ty = cy - CLOUD_CY * s
    fun x(v: Float) = tx + v * s
    fun y(v: Float) = ty + v * s
    val b  = 76f                // y baseline côtés
    val bm = b + belly          // y fond au centre (ventre)
    return Path().apply {
        when (template) {
            // ── 0 : 2 bosses symétriques, bas et large ────────────────────────
            0 -> {
                moveTo(x(10f), y(b))
                cubicTo(x(0f),  y(b),      x(0f),  y(b-20f), x(16f), y(b-28f))
                cubicTo(x(8f),  y(b-50f),  x(45f), y(b-58f), x(72f), y(b-40f))
                cubicTo(x(88f), y(b-30f),  x(105f),y(b-28f), x(121f),y(b-32f))
                cubicTo(x(136f),y(b-28f),  x(152f),y(b-30f), x(168f),y(b-40f))
                cubicTo(x(195f),y(b-58f),  x(230f),y(b-48f), x(234f),y(b-24f))
                cubicTo(x(240f),y(b-10f),  x(240f),y(b),     x(230f),y(b))
            }
            // ── 1 : 1 grosse bosse centrale, compact ─────────────────────────
            1 -> {
                moveTo(x(18f), y(b))
                cubicTo(x(4f),  y(b),      x(0f),  y(b-32f), x(20f), y(b-52f))
                cubicTo(x(30f), y(b-80f),  x(80f), y(b-90f), x(121f),y(b-88f))
                cubicTo(x(162f),y(b-90f),  x(210f),y(b-78f), x(222f),y(b-50f))
                cubicTo(x(240f),y(b-30f),  x(240f),y(b),     x(224f),y(b))
            }
            // ── 2 : 3 bosses classique cumulus ────────────────────────────────
            2 -> {
                moveTo(x(10f), y(b))
                cubicTo(x(0f),  y(b),      x(0f),  y(b-26f), x(15f), y(b-36f))
                cubicTo(x(8f),  y(b-58f),  x(42f), y(b-66f), x(65f), y(b-48f))
                cubicTo(x(80f), y(b-38f),  x(92f), y(b-36f), x(100f),y(b-42f))
                cubicTo(x(98f), y(b-72f),  x(132f),y(b-82f), x(155f),y(b-62f))
                cubicTo(x(168f),y(b-52f),  x(185f),y(b-50f), x(200f),y(b-58f))
                cubicTo(x(212f),y(b-44f),  x(236f),y(b-22f), x(232f),y(b))
            }
            // ── 3 : 4 bosses, nuage chargé ────────────────────────────────────
            3 -> {
                moveTo(x(6f),  y(b))
                cubicTo(x(0f),  y(b),      x(0f),  y(b-18f), x(12f), y(b-26f))
                cubicTo(x(4f),  y(b-46f),  x(35f), y(b-54f), x(55f), y(b-42f))
                cubicTo(x(68f), y(b-34f),  x(76f), y(b-32f), x(84f), y(b-38f))
                cubicTo(x(82f), y(b-58f),  x(108f),y(b-66f), x(128f),y(b-54f))
                cubicTo(x(140f),y(b-46f),  x(150f),y(b-44f), x(162f),y(b-50f))
                cubicTo(x(162f),y(b-66f),  x(190f),y(b-72f), x(208f),y(b-56f))
                cubicTo(x(220f),y(b-44f),  x(234f),y(b-20f), x(236f),y(b))
            }
            // ── 4 : effilé à droite (queue de comète) ────────────────────────
            4 -> {
                moveTo(x(5f),  y(b))
                cubicTo(x(0f),  y(b),      x(0f),  y(b-38f), x(18f), y(b-52f))
                cubicTo(x(6f),  y(b-76f),  x(52f), y(b-86f), x(88f), y(b-62f))
                cubicTo(x(102f),y(b-52f),  x(118f),y(b-46f), x(136f),y(b-54f))
                cubicTo(x(142f),y(b-40f),  x(168f),y(b-32f), x(192f),y(b-26f))
                cubicTo(x(212f),y(b-18f),  x(234f),y(b-8f),  x(234f),y(b))
            }
            // ── 5 : masse à gauche, queue vers droite ─────────────────────────
            else -> {
                moveTo(x(8f),  y(b))
                cubicTo(x(0f),  y(b),      x(0f),  y(b-42f), x(20f), y(b-58f))
                cubicTo(x(6f),  y(b-84f),  x(56f), y(b-92f), x(92f), y(b-68f))
                cubicTo(x(110f),y(b-54f),  x(120f),y(b-50f), x(132f),y(b-58f))
                cubicTo(x(132f),y(b-36f),  x(158f),y(b-26f), x(185f),y(b-22f))
                cubicTo(x(208f),y(b-16f),  x(236f),y(b-8f),  x(236f),y(b))
            }
        }
        // ── Fond arrondi commun (droite → gauche) ─────────────────────────────
        cubicTo(x(215f), y(bm+2f), x(121f), y(bm+5f), x(28f), y(bm+2f))
        cubicTo(x(10f),  y(bm),    x(2f),   y(b+2f),  x(8f),  y(b))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    cx: Float, cy: Float, w: Float, alpha: Float, color: Color, template: Int, belly: Float,
) {
    drawPath(cloudPath(cx, cy, w * 1.04f, template, belly), color.copy(alpha = alpha * 0.25f))
    drawPath(cloudPath(cx, cy, w,          template, belly), color.copy(alpha = alpha))
}

@Composable
private fun CloudCanvas(cloudColor: Color, modifier: Modifier) {
    val bellies  = remember { CLOUDS.map { randomBelly() } }
    val startMs  = remember { System.currentTimeMillis() }
    var tick     by remember { mutableLongStateOf(0L) }

    // Les nuages bougent très lentement (22–45 s par traversée) : 8 fps suffit.
    // Évite de maintenir le Choreographer à 60 fps en permanence.
    LaunchedEffect(Unit) {
        while (true) {
            delay(333L)   // 3 fps — nuages imperceptibles à plus de 3 fps
            tick = System.currentTimeMillis() - startMs
        }
    }

    Canvas(modifier.fillMaxSize()) {
        CLOUDS.forEachIndexed { i, cloud ->
            val cloudW = size.width * cloud.wFrac
            val xFrac  = ((tick.toFloat() / cloud.durationMs) + cloud.phase) % 1.0f
            val cx     = -cloudW + xFrac * (size.width + cloudW * 2f)
            val cy     = cloud.yFrac * size.height
            drawCloud(cx, cy, cloudW, cloud.alpha, cloudColor, i, bellies[i])
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
