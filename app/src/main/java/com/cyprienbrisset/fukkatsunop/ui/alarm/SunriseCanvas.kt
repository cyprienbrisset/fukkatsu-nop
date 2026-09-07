package com.cyprienbrisset.fukkatsunop.ui.alarm

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val TWO_PI = (PI * 2.0).toFloat()
private val GR     = 0.6180339887f

// ── Palette ───────────────────────────────────────────────────────────────────
// 7 keyframes : nuit profonde → avant-aube → aube → embrasement → aurore → montée → soleil levé

private data class SkyPalette(val zenith: Color, val mid: Color, val horizon: Color)
private val SKY_KEYS = listOf(
    0.00f to SkyPalette(Color(0xFF060818), Color(0xFF09092A), Color(0xFF0E0D30)),
    0.14f to SkyPalette(Color(0xFF07073C), Color(0xFF13093A), Color(0xFF320C28)),
    0.30f to SkyPalette(Color(0xFF0D0726), Color(0xFF1E081E), Color(0xFF700F0F)),
    0.46f to SkyPalette(Color(0xFF130A14), Color(0xFF2A0918), Color(0xFFBF2A0C)),
    0.62f to SkyPalette(Color(0xFF190B0C), Color(0xFF380E08), Color(0xFFDE4A12)),
    0.78f to SkyPalette(Color(0xFF200E08), Color(0xFF4A1A08), Color(0xFFF27018)),
    1.00f to SkyPalette(Color(0xFF482608), Color(0xFF785015), Color(0xFFFFD038)),
)

// ── Defs particules (calculées une seule fois) ────────────────────────────────

private data class StarDef(val x: Float, val y: Float, val r: Float, val phase: Float)
private data class PetalDef(
    val xBase: Float, val yBase: Float,
    val driftX: Float, val speed: Float,
    val sz: Float, val rotSpeed: Float, val phase: Float, val appear: Float,
)

@Composable
fun SunriseCanvas(frac: Float, modifier: Modifier = Modifier) {
    val stars  = remember { buildStars(52) }
    val petals = remember { buildPetals(22) }

    val inf = rememberInfiniteTransition(label = "sunrise")
    // tick : horloge maître 0→1 sur 60 s, en boucle
    val tick by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "tick",
    )
    val pulse by inf.animateFloat(
        0.96f, 1.04f,
        infiniteRepeatable(tween(4_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Canvas(modifier.fillMaxSize()) {
        drawSky(frac)
        drawStars(frac, stars, tick)
        drawHorizonAtmo(frac)
        drawRays(frac, tick)
        drawSunGlow(frac, pulse)
        drawSunDisk(frac, pulse)
        drawHighClouds(frac, tick)
        drawFuji(frac)
        drawRimLight(frac)
        drawMist(frac, tick)
        drawPetals(frac, tick, petals)
        drawVignette()
    }
}

// ── Builders particules ───────────────────────────────────────────────────────

private fun buildStars(n: Int) = List(n) { i ->
    val f = i.toFloat()
    StarDef(
        x     = (f * GR * 3.71f) % 1f,
        y     = (f * GR * 1.97f) % 0.52f,
        r     = 0.0014f + (f * GR * 0.0013f) % 0.0020f,
        phase = (f * GR * 11.3f) % 1f,
    )
}

private fun buildPetals(n: Int) = List(n) { i ->
    val f = i.toFloat()
    PetalDef(
        xBase    = (f * GR) % 1f,
        yBase    = 0.08f + (f * GR * 2.73f) % 0.52f,
        driftX   = ((f * GR * 5.3f) % 0.14f - 0.07f) * 0.05f,
        speed    = 0.09f + (f * GR * 0.021f) % 0.08f,
        sz       = 0.006f + (f * GR * 0.005f) % 0.007f,
        rotSpeed = ((f * GR * 7.1f) % 0.8f - 0.4f) * 360f,
        phase    = (f * GR * 13.7f) % 1f,
        appear   = 0.36f + (f * 0.024f) % 0.30f,
    )
}

// ── Helpers couleurs ──────────────────────────────────────────────────────────

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpC(a: Color, b: Color, t: Float) = Color(
    red   = (a.red   + (b.red   - a.red)   * t).coerceIn(0f, 1f),
    green = (a.green + (b.green - a.green) * t).coerceIn(0f, 1f),
    blue  = (a.blue  + (b.blue  - a.blue)  * t).coerceIn(0f, 1f),
)

private fun skyPaletteAt(frac: Float): SkyPalette {
    val f  = frac.coerceIn(0f, 1f)
    val lo = SKY_KEYS.lastOrNull { it.first <= f } ?: SKY_KEYS.first()
    val hi = SKY_KEYS.firstOrNull { it.first > f  } ?: SKY_KEYS.last()
    if (lo === hi) return lo.second
    val t = (f - lo.first) / (hi.first - lo.first)
    return SkyPalette(
        lerpC(lo.second.zenith,  hi.second.zenith,  t),
        lerpC(lo.second.mid,     hi.second.mid,     t),
        lerpC(lo.second.horizon, hi.second.horizon, t),
    )
}

private fun sunColor(frac: Float) = when {
    frac < 0.24f -> lerpC(Color(0xFF7A1010), Color(0xFFD03808), frac / 0.24f)
    frac < 0.52f -> lerpC(Color(0xFFD03808), Color(0xFFF07020), (frac - 0.24f) / 0.28f)
    frac < 0.78f -> lerpC(Color(0xFFF07020), Color(0xFFFFCC40), (frac - 0.52f) / 0.26f)
    else         -> lerpC(Color(0xFFFFCC40), Color(0xFFFFEC90), (frac - 0.78f) / 0.22f)
}

// Y du centre solaire : part de derrière la montagne, monte au-dessus
private fun sunY(frac: Float, h: Float) = h * (0.73f - frac * 0.56f)

private fun sinf(x: Float)  = sin(x.toDouble()).toFloat()
private fun cosf(x: Float)  = cos(x.toDouble()).toFloat()

// ── Couches de rendu ──────────────────────────────────────────────────────────

private fun DrawScope.drawSky(frac: Float) {
    val p = skyPaletteAt(frac)
    drawRect(
        Brush.verticalGradient(
            colorStops = arrayOf(0f to p.zenith, 0.42f to p.mid, 1f to p.horizon),
            startY = 0f, endY = size.height,
        ),
    )
}

private fun DrawScope.drawStars(frac: Float, stars: List<StarDef>, tick: Float) {
    val a = (1f - frac / 0.30f).coerceIn(0f, 1f)
    if (a < 0.01f) return
    val w = size.width; val h = size.height
    stars.forEach { s ->
        val twinkle = 0.55f + 0.45f * sinf((tick * 8.17f + s.phase) * TWO_PI)
        drawCircle(Color.White.copy(alpha = a * twinkle * 0.88f), w * s.r, Offset(w * s.x, h * s.y))
    }
}

private fun DrawScope.drawHorizonAtmo(frac: Float) {
    if (frac < 0.06f) return
    val sc   = sunColor(frac)
    val a    = smoothstep(frac / 0.55f) * 0.70f
    // Large bande chaude à l'horizon, avant-goût du soleil
    drawRect(
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.52f to Color.Transparent,
                0.70f to sc.copy(alpha = a * 0.09f),
                0.84f to sc.copy(alpha = a * 0.22f),
                1.00f to sc.copy(alpha = a * 0.10f),
            ),
            startY = 0f, endY = size.height,
        ),
    )
}

private fun DrawScope.drawRays(frac: Float, tick: Float) {
    if (frac < 0.10f) return
    val rayA = smoothstep((frac - 0.10f) / 0.28f) * 0.11f
    val w = size.width; val h = size.height
    val cx = w * 0.5f; val cy = sunY(frac, h)
    val sc = sunColor(frac)
    val reach = sqrt(w * w + h * h)
    val baseRot = tick * TWO_PI * 0.04f   // rotation très lente

    for (i in 0 until 16) {
        val angle = baseRot + i.toFloat() / 16f * TWO_PI
        val half  = 0.046f   // demi-angle du rayon (rad)
        val bright = when (i % 4) { 0 -> 1.0f; 2 -> 0.60f; else -> 0.28f }
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + reach * sinf(angle - half), cy - reach * cosf(angle - half))
            lineTo(cx + reach * sinf(angle + half), cy - reach * cosf(angle + half))
            close()
        }
        drawPath(path, sc.copy(alpha = rayA * bright))
    }
}

private fun DrawScope.drawSunGlow(frac: Float, pulse: Float) {
    if (frac < 0.05f) return
    val w = size.width; val h = size.height
    val cx = w * 0.5f; val cy = sunY(frac, h)
    val sc = sunColor(frac)
    val a  = smoothstep(frac / 0.48f).coerceIn(0f, 0.88f)
    val bigR = w * (0.22f + frac * 0.12f) * pulse

    // Halo radial large et doux
    drawCircle(
        Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to sc.copy(alpha = a * 0.58f),
                0.30f to sc.copy(alpha = a * 0.20f),
                0.65f to sc.copy(alpha = a * 0.05f),
                1.00f to Color.Transparent,
            ),
            center = Offset(cx, cy), radius = bigR,
        ),
        radius = bigR, center = Offset(cx, cy),
    )
}

private fun DrawScope.drawSunDisk(frac: Float, pulse: Float) {
    if (frac < 0.05f) return
    val w = size.width; val h = size.height
    val cx = w * 0.5f; val cy = sunY(frac, h)
    val r  = w * 0.050f * pulse
    val sc = sunColor(frac)

    drawCircle(sc.copy(alpha = 0.28f), r * 1.80f, Offset(cx, cy))   // corona
    drawCircle(sc.copy(alpha = 0.55f), r * 1.30f, Offset(cx, cy))   // halo proche
    drawCircle(sc,                      r,         Offset(cx, cy))   // disque
    drawCircle(                                                       // cœur lumineux
        Color.White.copy(alpha = (frac * 0.44f).coerceIn(0f, 0.40f)),
        r * 0.32f, Offset(cx, cy),
    )
}

private fun DrawScope.drawHighClouds(frac: Float, tick: Float) {
    val cf = (frac - 0.08f) / 0.68f
    val a  = smoothstep(cf.coerceIn(0f, 1f)) * 0.22f
    if (a < 0.01f) return
    val sc = sunColor(frac)
    val w = size.width; val h = size.height
    // Nuages d'altitude qui s'embrasent de rose/orange/or
    listOf(
        Triple(0.17f, 0.44f, 0.040f),
        Triple(0.22f, 0.32f, 0.028f),
        Triple(0.27f, 0.52f, 0.034f),
        Triple(0.13f, 0.28f, 0.022f),
        Triple(0.31f, 0.38f, 0.026f),
    ).forEachIndexed { i, (yf, wf, tf) ->
        val drift = sinf((tick + i * 0.21f) * TWO_PI) * 0.010f
        val bw = w * wf; val bh = h * tf
        val cx = w * (0.5f + drift)
        drawOval(
            sc.copy(alpha = a * (0.45f + i * 0.10f)),
            Offset(cx - bw * 0.5f, h * yf - bh),
            Size(bw, bh * 2f),
        )
    }
}

private fun DrawScope.drawFuji(frac: Float) {
    val w = size.width; val h = size.height
    // La silhouette s'éclaircit très légèrement avec l'aube (éclairage diffus)
    val dark = lerpC(Color(0xFF04060F), Color(0xFF141826), frac)
    val px = w * 0.50f; val py = h * 0.312f

    val body = Path().apply {
        moveTo(-w * 0.05f, h * 1.02f)
        // Pente gauche : deux courbes cubiques (forme concave caractéristique du Fuji)
        cubicTo(w * 0.05f, h * 0.73f, w * 0.17f, h * 0.58f, w * 0.28f, h * 0.458f)
        cubicTo(w * 0.36f, h * 0.388f, w * 0.43f, h * 0.340f, px, py)
        // Pente droite (miroir)
        cubicTo(w * 0.57f, h * 0.340f, w * 0.64f, h * 0.388f, w * 0.72f, h * 0.458f)
        cubicTo(w * 0.83f, h * 0.58f, w * 0.95f, h * 0.73f, w * 1.05f, h * 1.02f)
        close()
    }
    drawPath(body, dark)

    // Calotte neigeuse — teinte bleu-glace, s'éclaircit avec l'aurore
    val snowA = 0.08f + frac * 0.28f
    val snowC = lerpC(Color(0xFF8899BB), Color(0xFFCCDCEE), frac).copy(alpha = snowA)
    val cap = Path().apply {
        moveTo(px - w * 0.072f, py + h * 0.048f)
        cubicTo(px - w * 0.048f, py + h * 0.018f, px - w * 0.013f, py - h * 0.004f, px, py)
        cubicTo(px + w * 0.013f, py - h * 0.004f, px + w * 0.048f, py + h * 0.018f, px + w * 0.072f, py + h * 0.048f)
        close()
    }
    drawPath(cap, snowC)
}

private fun DrawScope.drawRimLight(frac: Float) {
    if (frac < 0.20f) return
    val a  = smoothstep((frac - 0.20f) / 0.40f) * 0.78f
    val rc = lerpC(sunColor(frac), Color.White, 0.18f)
    val w = size.width; val h = size.height
    val px = w * 0.50f; val py = h * 0.312f

    // Arêtes supérieures du Fuji (même chemin que le corps, portion haute)
    val rim = Path().apply {
        moveTo(w * 0.28f, h * 0.458f)
        cubicTo(w * 0.36f, h * 0.388f, w * 0.43f, h * 0.340f, px, py)
        cubicTo(w * 0.57f, h * 0.340f, w * 0.64f, h * 0.388f, w * 0.72f, h * 0.458f)
    }
    // 3 passes : glow large → intermédiaire → filet précis
    drawPath(rim, rc.copy(alpha = a * 0.07f), style = Stroke(width = w * 0.038f))
    drawPath(rim, rc.copy(alpha = a * 0.18f), style = Stroke(width = w * 0.014f))
    drawPath(rim, rc.copy(alpha = a * 0.60f), style = Stroke(width = w * 0.0035f))
}

private fun DrawScope.drawMist(frac: Float, tick: Float) {
    val baseA = (0.018f + (1f - frac) * 0.092f).coerceIn(0f, 0.11f)
    val w = size.width; val h = size.height
    listOf(
        Triple(0.558f, 0.84f, 0.024f),
        Triple(0.612f, 1.00f, 0.030f),
        Triple(0.665f, 1.14f, 0.021f),
    ).forEachIndexed { i, (yf, wf, tf) ->
        val dy = sinf((tick + i * 0.27f) * TWO_PI) * h * 0.013f
        val bw = w * wf; val bh = h * tf
        drawOval(
            Color(0xFFF2EDE2).copy(alpha = baseA * (0.50f + i * 0.22f)),
            Offset((w - bw) * 0.5f, h * yf + dy - bh),
            Size(bw, bh * 2f),
        )
    }
}

private fun DrawScope.drawPetals(frac: Float, tick: Float, petals: List<PetalDef>) {
    val globalA = smoothstep((frac - 0.30f) / 0.22f).coerceIn(0f, 1f)
    if (globalA < 0.01f) return
    val w = size.width; val h = size.height

    petals.forEach { p ->
        val appear = smoothstep(((frac - p.appear) / 0.13f).coerceIn(0f, 1f))
        if (appear < 0.01f) return@forEach
        val t  = (tick + p.phase) % 1f
        val px = w * ((p.xBase + p.driftX * t * 12f + 1f) % 1f)
        // Les pétales montent doucement — portés par les courants thermiques du soleil
        val py = h * (p.yBase + (0.5f - t) * p.speed)
        val sz = w * p.sz
        val a  = (globalA * appear * 0.72f).coerceIn(0f, 0.68f)
        val col = Color(1.0f, 0.72f, 0.83f, a)

        withTransform({ rotate(p.rotSpeed * t, Offset(px, py)) }) {
            // Forme pétale : deux ellipses décalées = silhouette de sakura
            drawOval(col.copy(alpha = a * 0.42f), Offset(px - sz * 1.5f, py - sz * 0.75f), Size(sz * 3.0f, sz * 1.5f))
            drawOval(col,                          Offset(px - sz,         py - sz * 0.50f), Size(sz * 2.0f, sz))
        }
    }
}

private fun DrawScope.drawVignette() {
    val w = size.width; val h = size.height
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.58f to Color.Transparent,
                1.00f to Color(0xCC000000),
            ),
            center = Offset(w * 0.5f, h * 0.5f),
            radius = w * 0.74f,
        ),
    )
}
