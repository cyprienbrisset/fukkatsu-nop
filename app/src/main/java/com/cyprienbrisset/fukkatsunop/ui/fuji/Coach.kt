package com.cyprienbrisset.fukkatsunop.ui.fuji

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic

// ── Coach marks: a one-time card per screen explaining its touch gestures ────

enum class Gesture { TAP, LONG_PRESS, PULL_DOWN, PULL_UP, SLIDE_LEFT, SLIDE_RIGHT, DRAG, SLIDER }

/** One card: what to do, and where on the miniature screen the finger shows it (0..1). */
data class CoachStep(
    val title: String,
    val text: String,
    val gesture: Gesture,
    val at: Offset = Offset(0.5f, 0.55f),
)

/** Which coach cards were already dismissed, so each shows only once. */
object Coach {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("coach", Context.MODE_PRIVATE)
    fun seen(ctx: Context, id: String) = prefs(ctx).getBoolean(id, false)
    fun markSeen(ctx: Context, id: String) = prefs(ctx).edit().putBoolean(id, true).apply()
    /** "Revoir les astuces" in the settings: every card shows again. */
    fun resetAll(ctx: Context) = prefs(ctx).edit().clear().apply()
}

/**
 * Full-screen card shown the first time [active] is true for this [id]. Swallows touches
 * until dismissed, so a gesture tried by mistake doesn't act on the screen behind.
 */
@Composable
fun CoachOverlay(id: String, steps: List<CoachStep>, active: Boolean = true) {
    val ctx = LocalContext.current
    var pending by remember(id) { mutableStateOf(!Coach.seen(ctx, id)) }
    var index by remember(id) { mutableIntStateOf(0) }
    val done = { Coach.markSeen(ctx, id); pending = false }

    AnimatedVisibility(visible = active && pending, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xD90A0E18))
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(1080.dp)
                    .glass(RoundedCornerShape(44.dp), FujiColors.GlassDark)
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                AnimatedContent(
                    targetState = index,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                    label = "coach-step",
                ) { i ->
                    val step = steps[i]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        GesturePreview(step.gesture, step.at, Modifier.size(width = 400.dp, height = 250.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (steps.size > 1) {
                                Text(
                                    "ASTUCE ${i + 1} SUR ${steps.size}",
                                    color = FujiColors.Faint, fontFamily = Gothic, fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp,
                                )
                            }
                            Text(step.title, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 40.sp, fontWeight = FontWeight.Medium, lineHeight = 46.sp)
                            Text(step.text, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 24.sp, lineHeight = 34.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (steps.size > 1) {
                        steps.indices.forEach { i ->
                            val w by animateDpAsState(if (i == index) 36.dp else 10.dp, label = "coach-dot")
                            Box(Modifier.size(width = w, height = 10.dp).clip(CircleShape).background(if (i == index) FujiColors.Cream else FujiColors.GlassStrong))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val last = index == steps.lastIndex
                    if (!last) FujiButton("Passer", done, height = 80.dp, container = Color.Transparent)
                    if (index > 0) FujiButton("Précédent", { index-- }, height = 80.dp)
                    FujiButton(if (last) "C'est compris" else "Suivant", { if (last) done() else index++ }, primary = true, height = 80.dp)
                }
            }
        }
    }
}

// ── Miniature Portal screen with an animated finger ─────────────────────────

private val Finger = Color(0xFFF4F1EA)
private val Trail = Color(0x66F4F1EA)
private val Ghost = Color(0x33F4F1EA)

@Composable
private fun GesturePreview(gesture: Gesture, at: Offset, modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "gesture")
    val t by loop.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "gesture-t",
    )
    Canvas(modifier) {
        val corner = CornerRadius(28.dp.toPx())
        drawRoundRect(FujiColors.Glass, cornerRadius = corner)
        drawRoundRect(FujiColors.Line, cornerRadius = corner, style = Stroke(3.dp.toPx()))

        // Timeline: finger appears (0–.15), acts (.15–.75), lifts (.75–1).
        val move = FastOutSlowInEasing.transform(((t - 0.15f) / 0.6f).coerceIn(0f, 1f))
        val alpha = when {
            t < 0.15f -> t / 0.15f
            t > 0.85f -> (1f - t) / 0.15f
            else -> 1f
        }
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(x * w, y * h)

        when (gesture) {
            Gesture.TAP -> {
                val c = p(at.x, at.y)
                val ring = ((t - 0.25f) / 0.45f).coerceIn(0f, 1f)
                if (ring in 0.01f..0.99f) drawCircle(Finger.copy(alpha = 1f - ring), radius = 22.dp.toPx() + ring * 40.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
                finger(c, alpha)
            }
            Gesture.LONG_PRESS -> {
                val c = p(at.x, at.y)
                drawRoundRect(Ghost, topLeft = c - Offset(34.dp.toPx(), 34.dp.toPx()), size = Size(68.dp.toPx(), 68.dp.toPx()), cornerRadius = CornerRadius(16.dp.toPx()))
                drawArc(
                    Finger, -90f, 360f * move, useCenter = false,
                    topLeft = c - Offset(44.dp.toPx(), 44.dp.toPx()), size = Size(88.dp.toPx(), 88.dp.toPx()),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round), alpha = alpha,
                )
                finger(c, alpha)
            }
            Gesture.PULL_DOWN -> {
                // The control centre follows the finger down from the top edge.
                val y = 0.04f + 0.56f * move
                drawRoundRect(Ghost, topLeft = Offset(0.08f * w, 0f), size = Size(0.84f * w, y * h), cornerRadius = CornerRadius(20.dp.toPx()))
                drawRoundRect(FujiColors.GlassStrong, topLeft = Offset(0.4f * w, 0f), size = Size(0.2f * w, 14.dp.toPx()), cornerRadius = CornerRadius(7.dp.toPx()))
                swipe(p(0.5f, 0.04f), p(0.5f, y), alpha)
            }
            Gesture.PULL_UP -> {
                val y = 0.96f - 0.56f * move
                drawRoundRect(Ghost, topLeft = Offset(0.08f * w, y * h), size = Size(0.84f * w, (1f - y) * h), cornerRadius = CornerRadius(20.dp.toPx()))
                swipe(p(0.5f, 0.96f), p(0.5f, y), alpha)
            }
            Gesture.SLIDE_LEFT, Gesture.SLIDE_RIGHT -> {
                val x = if (gesture == Gesture.SLIDE_LEFT) 0.27f else 0.73f
                // Dashed middle line: the two halves of the screen.
                var dy = 0f
                while (dy < h) { drawLine(Ghost, Offset(w / 2, dy), Offset(w / 2, dy + 8.dp.toPx()), 2.dp.toPx()); dy += 16.dp.toPx() }
                gauge(Offset((if (gesture == Gesture.SLIDE_LEFT) 0.08f else 0.92f) * w, h / 2), 0.25f + 0.65f * move)
                swipe(p(x, 0.78f), p(x, 0.78f - 0.5f * move), alpha)
            }
            Gesture.DRAG -> {
                // A held card carried to another place.
                val from = p(at.x, 0.3f)
                val c = from + Offset(0f, 0.4f * h * move)
                drawRoundRect(Ghost, topLeft = from - Offset(48.dp.toPx(), 34.dp.toPx()), size = Size(96.dp.toPx(), 68.dp.toPx()), cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
                drawRoundRect(FujiColors.GlassStrong, topLeft = c - Offset(48.dp.toPx(), 34.dp.toPx()), size = Size(96.dp.toPx(), 68.dp.toPx()), cornerRadius = CornerRadius(14.dp.toPx()))
                finger(c, alpha)
            }
            Gesture.SLIDER -> {
                val y = at.y * h
                val x0 = 0.12f * w
                val x1 = 0.88f * w
                val x = x0 + (0.25f + 0.55f * move) * (x1 - x0)
                drawLine(FujiColors.GlassStrong, Offset(x0, y), Offset(x1, y), 26.dp.toPx(), StrokeCap.Round)
                drawLine(Finger.copy(alpha = 0.85f), Offset(x0, y), Offset(x, y), 26.dp.toPx(), StrokeCap.Round)
                finger(Offset(x, y), alpha)
            }
        }
    }
}

private fun DrawScope.finger(c: Offset, alpha: Float) {
    drawCircle(Color.Black.copy(alpha = 0.25f * alpha), radius = 24.dp.toPx(), center = c + Offset(0f, 3.dp.toPx()))
    drawCircle(Finger.copy(alpha = 0.92f * alpha), radius = 22.dp.toPx(), center = c)
}

private fun DrawScope.swipe(from: Offset, to: Offset, alpha: Float) {
    drawLine(Trail.copy(alpha = Trail.alpha * alpha), from, to, 14.dp.toPx(), StrokeCap.Round)
    finger(to, alpha)
}

/** Small vertical level bar, like the brightness and volume gauge of the home screen. */
private fun DrawScope.gauge(center: Offset, level: Float) {
    val bw = 10.dp.toPx()
    val bh = size.height * 0.6f
    val top = center - Offset(bw / 2, bh / 2)
    drawRoundRect(FujiColors.GlassStrong, topLeft = top, size = Size(bw, bh), cornerRadius = CornerRadius(bw / 2))
    drawRoundRect(Finger, topLeft = Offset(top.x, top.y + bh * (1f - level)), size = Size(bw, bh * level), cornerRadius = CornerRadius(bw / 2))
}
