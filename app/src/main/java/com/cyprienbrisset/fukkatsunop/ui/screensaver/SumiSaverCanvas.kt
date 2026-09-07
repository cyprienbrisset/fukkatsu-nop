package com.cyprienbrisset.fukkatsunop.ui.screensaver

import android.graphics.Matrix
import android.graphics.Paint as APaint
import android.graphics.Path as APath
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Kinari = Color(0xFFECE7DD)
private val SumiBackground = Color(0xFF0D0E12)

// Kanji simples et reconnaissables — formes extraites de la police système
private val KANJI_CHARS = listOf(
    "水", "山", "月", "心", "風", "空", "夢", "禅", "墨", "美",
    "静", "雪", "波", "光", "炎", "竹", "雲", "花", "道", "気",
    "縁", "無", "悟", "愛", "一", "人", "大", "火",
)

private sealed interface SumiElem {
    val birthMs: Long
    val lifeMs: Long

    data class Kanji(
        override val birthMs: Long,
        val fillPath: APath,
        val bounds: RectF,
        val maxAlpha: Float,
        override val lifeMs: Long,
    ) : SumiElem

    data class InkDrop(
        override val birthMs: Long,
        val cx: Float, val cy: Float,
        val maxRadius: Float,
        val maxAlpha: Float,
        override val lifeMs: Long,
    ) : SumiElem
}

@Composable
fun SumiSaverCanvas(modifier: Modifier = Modifier) {
    val elements = remember { mutableStateListOf<SumiElem>() }
    var frameMs by remember { mutableLongStateOf(0L) }
    var lastKanjiMs by remember { mutableLongStateOf(0L) }
    var lastDropMs by remember { mutableLongStateOf(0L) }
    var canvasW by remember { mutableFloatStateOf(1280f) }
    var canvasH by remember { mutableFloatStateOf(800f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            val ms = System.currentTimeMillis()
            frameMs = ms
            if (ms - lastKanjiMs > 14_000L + Random.nextLong(0, 5_000)) {
                elements.add(randomKanji(canvasW, canvasH, ms))
                lastKanjiMs = ms
            }
            if (ms - lastDropMs > 3_500L + Random.nextLong(0, 2_500)) {
                elements.add(randomDrop(canvasW, canvasH, ms))
                lastDropMs = ms
            }
            elements.removeAll { it.birthMs + it.lifeMs < ms }
        }
    }

    Canvas(modifier.fillMaxSize().background(SumiBackground)) {
        canvasW = size.width
        canvasH = size.height
        val ms = frameMs
        elements.filterIsInstance<SumiElem.InkDrop>().forEach { drawDrop(it, frac(ms, it)) }
        elements.filterIsInstance<SumiElem.Kanji>().forEach { drawKanji(it, frac(ms, it)) }
    }
}

private fun frac(ms: Long, e: SumiElem) = ((ms - e.birthMs).toFloat() / e.lifeMs).coerceIn(0f, 1f)

// ── Génération ────────────────────────────────────────────────────────────────

private fun randomKanji(w: Float, h: Float, nowMs: Long): SumiElem.Kanji {
    val char = KANJI_CHARS[Random.nextInt(KANJI_CHARS.size)]
    // Grand : 45–65% de la hauteur de l'écran
    val sizePx = h * (0.45f + Random.nextFloat() * 0.20f)

    val paint = APaint().apply {
        textSize = sizePx
        // Bold Serif → traits épais, aspect calligraphique
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        isAntiAlias = true
    }

    val raw = APath()
    // getTextPath donne la vraie forme vectorielle du glyphe depuis la police
    paint.getTextPath(char, 0, char.length, 0f, 0f, raw)

    val rawBounds = RectF()
    raw.computeBounds(rawBounds, true)

    // Centrer aléatoirement sur l'écran avec marges
    val margin = sizePx * 0.55f
    val cx = margin + Random.nextFloat() * (w - 2f * margin)
    val cy = margin + Random.nextFloat() * (h - 2f * margin)

    val positioned = APath()
    Matrix().also {
        it.setTranslate(cx - rawBounds.centerX(), cy - rawBounds.centerY())
        raw.transform(it, positioned)
    }
    val finalBounds = RectF()
    positioned.computeBounds(finalBounds, true)

    return SumiElem.Kanji(
        birthMs = nowMs,
        fillPath = positioned,
        bounds = finalBounds,
        maxAlpha = 0.92f + Random.nextFloat() * 0.08f,
        lifeMs = 16_000L + Random.nextLong(0, 6_000),
    )
}

private fun randomDrop(w: Float, h: Float, ms: Long) = SumiElem.InkDrop(
    birthMs = ms,
    cx = Random.nextFloat() * w, cy = Random.nextFloat() * h,
    maxRadius = w * (0.004f + Random.nextFloat() * 0.016f),
    maxAlpha = 0.10f + Random.nextFloat() * 0.14f,
    lifeMs = 6_000L + Random.nextLong(0, 4_000),
)

// ── Rendu ─────────────────────────────────────────────────────────────────────

private fun envAlpha(frac: Float, fadeIn: Float = 0.04f, fadeOut: Float = 0.84f): Float = when {
    frac < fadeIn  -> frac / fadeIn
    frac < fadeOut -> 1f
    else           -> 1f - (frac - fadeOut) / (1f - fadeOut)
}

private fun DrawScope.drawKanji(elem: SumiElem.Kanji, frac: Float) {
    // 60% du temps = phase de dessin, 40% = maintenu puis fondu
    val drawFrac = (frac / 0.60f).coerceIn(0f, 1f)
    val alphaVal = envAlpha(frac, fadeIn = 0.02f, fadeOut = 0.84f)
    val inkColor = Kinari.copy(alpha = (alphaVal * elem.maxAlpha).coerceIn(0f, 1f))

    val b = elem.bounds
    val charH = b.bottom - b.top
    val charW = b.right - b.left

    // Frontière du pinceau qui descend
    val revealY = b.top + drawFrac * charH
    // Zone de douceur au bas du trait (effet d'encre qui s'arrête)
    val softH = charH * 0.07f

    // 1. Clipper + dessiner le remplissage du kanji jusqu'à revealY + marge
    clipRect(
        left   = b.left  - charW * 0.05f,
        top    = b.top   - charH * 0.05f,
        right  = b.right + charW * 0.05f,
        bottom = revealY + softH,
    ) {
        drawPath(path = elem.fillPath.asComposePath(), color = inkColor)
    }

    // 2. Dégradé qui cache le bord inférieur du clip → bord doux comme de l'encre qui finit
    if (drawFrac < 1f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, SumiBackground),
                startY = revealY - softH * 0.5f,
                endY   = revealY + softH,
            ),
            topLeft = Offset(b.left - charW * 0.08f, revealY - softH * 0.5f),
            size    = Size(charW * 1.16f, softH * 1.5f),
        )
    }

    // 3. Pointe du pinceau : forme ovale horizontale large + halo d'encre fraîche
    if (drawFrac in 0.01f..0.97f) {
        val tipCX = b.left + charW * 0.5f
        val tipCY = revealY
        val tipW  = charW * 0.38f   // pinceau large
        val tipH  = charH * 0.045f  // pinceau plat

        // Corps de la pointe (large, plat, chargé d'encre)
        drawOval(
            color   = inkColor,
            topLeft = Offset(tipCX - tipW * 0.5f, tipCY - tipH * 0.5f),
            size    = Size(tipW, tipH),
        )
        // Halo humide autour
        drawOval(
            color   = inkColor.copy(alpha = inkColor.alpha * 0.18f),
            topLeft = Offset(tipCX - tipW * 0.7f, tipCY - tipH * 1.8f),
            size    = Size(tipW * 1.4f, tipH * 3.6f),
        )
        // Micro-éclat sur le côté gauche du pinceau
        drawOval(
            color   = inkColor.copy(alpha = inkColor.alpha * 0.10f),
            topLeft = Offset(tipCX - tipW * 0.62f, tipCY - tipH * 0.3f),
            size    = Size(tipW * 0.22f, tipH * 0.6f),
        )
    }
}

private fun DrawScope.drawDrop(elem: SumiElem.InkDrop, frac: Float) {
    val r = elem.maxRadius * if (frac < 0.20f) frac / 0.20f else 1f
    val a = (envAlpha(frac, 0.10f, 0.65f) * elem.maxAlpha).coerceIn(0f, 1f)
    val c = Offset(elem.cx, elem.cy)
    drawCircle(Kinari.copy(alpha = a), radius = r * 0.5f, center = c)
    drawCircle(Kinari.copy(alpha = a * 0.20f), radius = r, center = c)
}
