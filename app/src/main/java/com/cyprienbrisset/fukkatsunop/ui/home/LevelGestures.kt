package com.cyprienbrisset.fukkatsunop.ui.home

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import kotlinx.coroutines.delay

/** Media volume and screen brightness, as 0..1 levels. */
object DeviceLevels {
    fun volume(ctx: Context): Float {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(ctx: Context, f: Float) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f.coerceIn(0f, 1f) * max), 0)
    }

    fun brightness(ctx: Context): Float =
        runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)

    /** System brightness when WRITE_SETTINGS is granted (provision script), else this window only. */
    fun setBrightness(ctx: Context, f: Float) {
        val v = f.coerceIn(0.02f, 1f)
        if (Settings.System.canWrite(ctx)) {
            runCatching {
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt())
            }
        } else {
            (ctx as? Activity)?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = v } }
        }
    }
}

enum class LevelKind { BRIGHTNESS, VOLUME }

/** Netflix-style vertical gauge shown while sliding on the left (brightness) or right (volume). */
@Composable
fun LevelHud(kind: LevelKind, value: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(120.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(FujiColors.GlassDark)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(
            if (kind == LevelKind.BRIGHTNESS) Icons.Rounded.WbSunny else Icons.AutoMirrored.Rounded.VolumeUp,
            null, tint = FujiColors.Cream, modifier = Modifier.size(40.dp),
        )
        Box(Modifier.width(20.dp).height(360.dp).clip(RoundedCornerShape(10.dp)).background(FujiColors.GlassStrong)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(10.dp))
                    .background(FujiColors.Cream),
            )
        }
        Text("${(value * 100).toInt()} %", color = FujiColors.Cream, fontFamily = Gothic, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

/** Compact volume slider for the "now playing" card: tap or drag anywhere on the track. */
@Composable
fun CardVolume(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var level by remember { mutableFloatStateOf(DeviceLevels.volume(ctx)) }
    // Follow the hardware volume keys and other apps.
    LaunchedEffect(Unit) {
        while (true) { delay(1500); level = DeviceLevels.volume(ctx) }
    }
    val set: (Float) -> Unit = { f -> level = f.coerceIn(0f, 1f); DeviceLevels.setVolume(ctx, level) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Volume", tint = FujiColors.Cream, modifier = Modifier.size(32.dp))
        Box(
            Modifier
                .weight(1f)
                .height(64.dp)
                .pointerInput(Unit) { detectTapGestures { o -> set(o.x / size.width) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume(); set(change.position.x / size.width) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(FujiColors.GlassStrong))
            Box(Modifier.fillMaxWidth(level).height(14.dp).clip(RoundedCornerShape(7.dp)).background(FujiColors.Cream))
        }
        Text("${(level * 100).toInt()} %", color = FujiColors.Muted, fontFamily = Gothic, fontSize = 20.sp, modifier = Modifier.width(64.dp))
    }
}
