package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object AppIconCache {
    val bitmaps = ConcurrentHashMap<String, android.graphics.Bitmap>()
}

fun monogramLetter(label: String): String =
    label.trim().firstOrNull()?.uppercase() ?: "?"

fun monogramColor(label: String): Long {
    val palette = longArrayOf(
        0xFF4C5FD5, 0xFFD54C7A, 0xFF3FA34D, 0xFFD58A4C,
        0xFF8A4CD5, 0xFF4CB5D5, 0xFFD5C24C, 0xFFD54C4C,
    )
    val idx = (label.trim().lowercase().hashCode() and 0x7FFFFFFF) % palette.size
    return palette[idx]
}

fun directFaviconUrl(url: String): String {
    val scheme = url.substringBefore("://", "http")
    val noScheme = url.substringAfter("://", url)
    val authority = noScheme.substringBefore('/')
    return "$scheme://$authority/favicon.ico"
}

fun googleFaviconUrl(url: String): String {
    val noScheme = url.substringAfter("://", url)
    val host = noScheme.substringBefore('/').substringBefore(':')
    return "https://www.google.com/s2/favicons?sz=128&domain=$host"
}

/** Alias used in tests — delegates to [googleFaviconUrl]. */
fun faviconUrl(url: String): String = googleFaviconUrl(url)

@Composable
fun TileIcon(tile: TileEntity, size: Dp, modifier: Modifier = Modifier, airPlayState: AirPlayState? = null) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(size / 4)

    val custom = tile.iconRef
    if (custom != null) {
        AsyncImage(
            model = custom, contentDescription = tile.label,
            modifier = modifier.size(size).clip(shape),
        )
        return
    }

    when (tile.type) {
        TileType.APP -> {
            val pkg = tile.packageName
            var bmp by remember(pkg) { mutableStateOf(pkg?.let { AppIconCache.bitmaps[it] }) }
            var failed by remember(pkg) { mutableStateOf(false) }
            LaunchedEffect(pkg) {
                if (pkg == null) { failed = true; return@LaunchedEffect }
                if (AppIconCache.bitmaps.containsKey(pkg)) { bmp = AppIconCache.bitmaps[pkg]; return@LaunchedEffect }
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap() }.getOrNull()
                }
                if (loaded != null) { AppIconCache.bitmaps[pkg] = loaded; bmp = loaded } else failed = true
            }
            when {
                bmp != null -> AsyncImage(
                    model = ImageRequest.Builder(ctx).data(bmp).build(),
                    contentDescription = tile.label,
                    modifier = modifier.size(size).clip(shape),
                )
                failed -> Monogram(tile.label, size, modifier)
                else -> androidx.compose.foundation.layout.Box(modifier.size(size))
            }
        }
        TileType.WEB -> {
            val url = tile.url
            if (url == null) {
                Monogram(tile.label, size, modifier)
            } else {
                // 0 = try direct /favicon.ico, 1 = try Google service, 2 = monogram
                var step by remember(url) { mutableStateOf(0) }
                if (step >= 2) {
                    Monogram(tile.label, size, modifier)
                } else {
                    val iconUrl = if (step == 0) directFaviconUrl(url) else googleFaviconUrl(url)
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(iconUrl).crossfade(true).build(),
                        contentDescription = tile.label,
                        modifier = modifier.size(size).clip(shape),
                        onError = { step++ },
                    )
                }
            }
        }
        TileType.AIRPLAY -> {
            val airState = airPlayState ?: AirPlayState.Waiting
            val statusText = when (airState) {
                is AirPlayState.Streaming  -> "● Live"
                is AirPlayState.Connecting -> "Connexion…"
                is AirPlayState.Error      -> "⚠ Erreur"
                else                       -> "En attente"
            }
            val statusColor = when (airState) {
                is AirPlayState.Streaming -> AccentShu
                is AirPlayState.Error     -> Kinari
                else                      -> Color.Gray
            }
            Box(
                modifier.size(size).clip(RoundedCornerShape(size / 4))
                    .background(Color(monogramColor("Mac"))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Monitor,
                        contentDescription = "Écran Mac",
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.45f),
                    )
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = (size.value / 8).sp,
                        lineHeight = (size.value / 8).sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Monogram(label: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size / 4))
            .background(Color(monogramColor(label))),
        contentAlignment = Alignment.Center,
    ) {
        Text(monogramLetter(label), color = Color.White, fontSize = (size.value / 2).sp)
    }
}
