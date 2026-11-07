package com.cyprienbrisset.fukkatsunop.ui.fuji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.R
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import kotlinx.coroutines.delay
import java.time.LocalTime

// ── Tokens ────────────────────────────────────────────────────────────────────

object FujiColors {
    val Cream = Color(0xFFF4F1EA)
    val Ink = Color(0xFF16212B)
    val Muted = Color(0xBFF4F1EA)
    val Faint = Color(0x99F4F1EA)
    val Glass = Color(0x1AFFFFFF)
    val GlassStrong = Color(0x24FFFFFF)
    val GlassDark = Color(0x800C101C)
    val Line = Color(0x29FFFFFF)
    val Veil = Color(0xA80A0E18)
    val Badge = Color(0xFFE5533D)
    val Ok = Color(0xFF6FCF97)
    val Waiting = Color(0xFFF2C46B)
}

// ── Moment of the day & photos ───────────────────────────────────────────────

enum class Moment(@DrawableRes val photo: Int, val credit: String) {
    AUBE(R.drawable.fuji_aube, "Photo : Alpsdake · Wikimedia Commons · CC BY-SA 4.0"),
    JOUR(R.drawable.fuji_jour, "Photo : Alpsdake · Wikimedia Commons · CC BY-SA 4.0"),
    SOIR(R.drawable.fuji_soir, "Photo : Yoshi Canopus · Wikimedia Commons · CC0"),
    NUIT(R.drawable.fuji_nuit, "Photo : Victor Porof · Wikimedia Commons · CC BY-SA 3.0"),
}

fun momentFor(hour: Int): Moment = when (hour) {
    in 5..7 -> Moment.AUBE
    in 8..16 -> Moment.JOUR
    in 17..19 -> Moment.SOIR
    else -> Moment.NUIT
}

/** Current moment, re-evaluated every minute. */
@Composable
fun rememberMoment(): Moment {
    var moment by remember { mutableStateOf(momentFor(LocalTime.now().hour)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            moment = momentFor(LocalTime.now().hour)
        }
    }
    return moment
}

// Android 9/10 has no RenderEffect blur: the blurred backdrop is a tiny downscale
// stretched back up with bilinear filtering, which reads as a soft blur under the veil.
private object PhotoCache {
    private val sharp = HashMap<Moment, Bitmap>()
    private val soft = HashMap<Moment, Bitmap>()

    fun sharp(ctx: Context, m: Moment): Bitmap = sharp.getOrPut(m) {
        BitmapFactory.decodeResource(ctx.resources, m.photo)
    }

    fun soft(ctx: Context, m: Moment): Bitmap = soft.getOrPut(m) {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val src = BitmapFactory.decodeResource(ctx.resources, m.photo, opts)
        val w = 240
        val h = (w.toFloat() * src.height / src.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        // Three box-blur passes approximate a gaussian; cheap enough at 240 px wide.
        repeat(3) { boxBlur(px, w, h, 6) }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        blurPass(px, tmp, w, h, r, horizontal = true)
        blurPass(tmp, px, w, h, r, horizontal = false)
    }

    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            var rs = 0; var gs = 0; var bs = 0
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + c] else src[c * w + line]
            }
            for (i in -r..r) { val p = at(i); rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF }
            val n = 2 * r + 1
            for (i in 0 until len) {
                val idx = if (horizontal) line * w + i else i * w + line
                dst[idx] = (0xFF shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
                val out = at(i - r); val inn = at(i + r + 1)
                rs += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                gs += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                bs += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}

@Composable
fun FujiPhoto(moment: Moment, modifier: Modifier = Modifier, blurred: Boolean = false) {
    val ctx = LocalContext.current
    Crossfade(targetState = moment, animationSpec = tween(1200), modifier = modifier, label = "fuji") { m ->
        val bmp = remember(m, blurred) {
            (if (blurred) PhotoCache.soft(ctx, m) else PhotoCache.sharp(ctx, m)).asImageBitmap()
        }
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Blurred photo + dark veil, used behind every content screen. */
@Composable
fun FujiBackdrop(modifier: Modifier = Modifier, veil: Color = FujiColors.Veil) {
    val moment = rememberMoment()
    Box(modifier.fillMaxSize()) {
        FujiPhoto(moment, Modifier.fillMaxSize(), blurred = true)
        Box(Modifier.fillMaxSize().background(veil))
    }
}

// ── Screen fit ───────────────────────────────────────────────────────────────

/**
 * The Fuji layouts are drawn for the 1920 dp wide Portal+. On narrower screens
 * (Portal Go / Mini, 1280 dp) shrink the density so the same layout fits as is.
 */
@Composable
fun FitToPortal(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val d = LocalDensity.current
    if (widthDp >= 1800 || widthDp <= 0) {
        content()
    } else {
        val f = widthDp / 1920f
        CompositionLocalProvider(LocalDensity provides Density(d.density * f, d.fontScale), content = content)
    }
}

// ── Press feedback ───────────────────────────────────────────────────────────

@Composable
private fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true, role: Role = Role.Button): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

// ── Buttons ──────────────────────────────────────────────────────────────────

@Composable
fun FujiIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    filled: Boolean = false,
    container: Color = if (filled) FujiColors.Cream else FujiColors.GlassStrong,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (filled) FujiColors.Ink else FujiColors.Cream, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun FujiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 88.dp,
    enabled: Boolean = true,
    container: Color = if (primary) FujiColors.Cream else FujiColors.GlassStrong,
) {
    val fg = if (primary) FujiColors.Ink else FujiColors.Cream
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (enabled) container else container.copy(alpha = container.alpha * 0.4f))
            .pressable(onClick, enabled)
            .padding(horizontal = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(28.dp))
        Text(
            text,
            color = fg.copy(alpha = if (enabled) 1f else 0.5f),
            fontFamily = Gothic,
            fontSize = 24.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun FujiSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val offset by animateFloatAsState(if (checked) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "switch")
    Box(
        modifier
            .size(width = 128.dp, height = 72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(if (checked) FujiColors.Cream else Color(0x2EFFFFFF))
            .then(if (onCheckedChange != null) Modifier.pressable({ onCheckedChange(!checked) }, role = Role.Switch) else Modifier)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .padding(start = (56.dp * offset))
                .size(60.dp)
                .clip(CircleShape)
                .background(if (checked) FujiColors.Ink else FujiColors.Cream),
        )
    }
}

/** Pill segmented control, e.g. Agenda / Meet / Chat. */
@Composable
fun FujiSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 76.dp,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(height / 2 + 6.dp))
            .background(FujiColors.Glass)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .height(height)
                    .widthIn(min = 160.dp)
                    .clip(RoundedCornerShape(height / 2))
                    .background(if (sel) FujiColors.Cream else Color.Transparent)
                    .pressable({ onSelect(i) }, role = Role.Tab)
                    .padding(horizontal = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) FujiColors.Ink else FujiColors.Cream, fontFamily = Gothic, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Surfaces ─────────────────────────────────────────────────────────────────

fun Modifier.glass(shape: Shape = RoundedCornerShape(32.dp), color: Color = FujiColors.Glass): Modifier =
    this.clip(shape).background(color)

@Composable
fun FujiCard(
    modifier: Modifier = Modifier,
    color: Color = FujiColors.Glass,
    padding: PaddingValues = PaddingValues(36.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .glass(RoundedCornerShape(36.dp), color)
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** One tappable settings-style row: title, subtitle and a trailing slot. */
@Composable
fun FujiRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .glass(RoundedCornerShape(30.dp), FujiColors.Glass)
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 32.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

// ── Screen scaffold ──────────────────────────────────────────────────────────

/**
 * Standard content screen: back button, big title, optional actions on the right.
 * The blurred Fuji backdrop is drawn once by AppNav behind every route.
 */
@Composable
fun FujiScreen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(start = 72.dp, end = 72.dp, top = 48.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (onBack != null) FujiIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack)
            Text(
                title,
                color = FujiColors.Cream,
                fontFamily = Gothic,
                fontSize = 46.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        Box(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

@Composable
fun FujiBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(FujiColors.Badge)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontFamily = Gothic, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FujiLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = FujiColors.Muted, fontFamily = Gothic, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(14.dp).clip(CircleShape).background(color))
}
