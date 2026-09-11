package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.Sumi
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class RecentApp(val packageName: String, val label: String)

// Gradient palette — one per app, derived from packageName hash
private val CARD_GRADIENTS = listOf(
    listOf(Color(0xFF0D1B4B), Color(0xFF1A3A8F)),
    listOf(Color(0xFF4A0033), Color(0xFF8B1A5E)),
    listOf(Color(0xFF0B3D1A), Color(0xFF1A7A35)),
    listOf(Color(0xFF2D0050), Color(0xFF6A1FA0)),
    listOf(Color(0xFF003366), Color(0xFF0055AA)),
    listOf(Color(0xFF1A1A1A), Color(0xFF3A3A4A)),
    listOf(Color(0xFF4A1500), Color(0xFF8B3200)),
    listOf(Color(0xFF004040), Color(0xFF007070)),
    listOf(Color(0xFF3A2000), Color(0xFF7A4500)),
    listOf(Color(0xFF1A0030), Color(0xFF4A0060)),
)

@Composable
fun RecentAppsOverlay(
    apps: List<RecentApp>,
    onDismiss: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onDismissApp: (String) -> Unit,
    onClearAll: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val ramInfo by vm.ramInfo.collectAsState()
    val ramFreed by vm.ramFreedMb.collectAsState()

    LaunchedEffect(Unit) { vm.refreshRam() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Sumi.copy(alpha = 0.93f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        // ── RAM chip — top right ─────────────────────────────────────────────
        val ram = ramInfo
        if (ram != null) {
            val usedMb = ram.totalMb - ram.availMb
            val usedFraction = (usedMb.toFloat() / ram.totalMb).coerceIn(0f, 1f)
            val chipColor = when {
                usedFraction > 0.85f -> Color(0xFFFF6B6B)
                usedFraction > 0.65f -> Color(0xFFFFCC00)
                else -> SumiMuted
            }
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 28.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (ramFreed != null && ramFreed!! > 0) "+${ramFreed} Mo libérés"
                    else "RAM  ${usedMb} / ${ram.totalMb} Mo",
                    color = if (ramFreed != null) AccentShu else chipColor,
                    fontFamily = Mincho,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                )
                LinearProgressIndicator(
                    progress = { usedFraction },
                    modifier = Modifier.width(80.dp).height(2.dp).clip(RoundedCornerShape(1.dp)),
                    color = chipColor,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round,
                )
            }
        }

        // ── App cards — centered ─────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (apps.isEmpty()) {
                Text(
                    "Aucune application récente",
                    color = Color.White.copy(alpha = 0.45f),
                    fontFamily = Mincho,
                    fontSize = 15.sp,
                )
            } else {
                Text(
                    "APPS RÉCENTES",
                    color = Color.White.copy(alpha = 0.4f),
                    fontFamily = Mincho,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                ) {
                    apps.take(6).forEach { app ->
                        RecentAppCard(
                            app = app,
                            onClick = { onLaunchApp(app.packageName); onDismiss() },
                            onDismiss = {
                                onDismissApp(app.packageName)
                                vm.refreshRam()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "↑  Glisser vers le haut pour fermer",
                    color = Color.White.copy(alpha = 0.28f),
                    fontFamily = Mincho,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { onClearAll() }) {
                    Text("Tout fermer", color = AccentShu, fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 1.sp)
                }
                LaunchedEffect(ramFreed) {
                    if (ramFreed != null) {
                        kotlinx.coroutines.delay(1400)
                        vm.clearRamFreed()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentAppCard(
    app: RecentApp,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val offsetY = remember(app.packageName) { Animatable(0f) }

    // Load icon bitmap once for both background halo and foreground icon
    var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName) {
        iconBitmap = withContext(Dispatchers.IO) {
            runCatching {
                ctx.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }

    val gradientColors = remember(app.packageName) {
        CARD_GRADIENTS[abs(app.packageName.hashCode()) % CARD_GRADIENTS.size]
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(155.dp)
            .graphicsLayer {
                translationY = offsetY.value
                alpha = (1f + offsetY.value / 280f).coerceIn(0f, 1f)
            }
            .pointerInput(app.packageName) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    var determined = false
                    var vertical = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.position - change.previousPosition
                        totalX += delta.x
                        totalY += delta.y

                        if (!determined &&
                            (abs(totalX) > viewConfiguration.touchSlop ||
                                abs(totalY) > viewConfiguration.touchSlop)
                        ) {
                            determined = true
                            vertical = abs(totalY) > abs(totalX)
                        }

                        if (vertical && totalY < 0f) {
                            change.consume()
                            scope.launch { offsetY.snapTo(totalY) }
                        }

                        if (!change.pressed) {
                            when {
                                vertical && totalY < -80f -> scope.launch {
                                    offsetY.animateTo(-600f, spring(stiffness = Spring.StiffnessMedium))
                                    onDismiss()
                                    offsetY.snapTo(0f)
                                }
                                vertical -> scope.launch {
                                    offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                                else -> onClick()
                            }
                            break
                        }
                    }
                }
            },
    ) {
        // Card (preview area)
        Box(
            Modifier
                .fillMaxWidth()
                .height(185.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(gradientColors)),
            contentAlignment = Alignment.Center,
        ) {
            // Large halo icon (background texture)
            iconBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.13f),
                )
            }

            // Foreground icon, centered
            val fakeTile = remember(app.packageName, app.label) {
                TileEntity(0L, TileType.APP, app.label, app.packageName, null, null, 0)
            }
            TileIcon(tile = fakeTile, size = 76.dp)
        }

        Spacer(Modifier.height(9.dp))
        Text(
            app.label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
