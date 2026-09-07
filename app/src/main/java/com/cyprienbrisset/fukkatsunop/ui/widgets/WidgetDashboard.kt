package com.cyprienbrisset.fukkatsunop.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import com.cyprienbrisset.fukkatsunop.ui.sumi.SectionLabel
import com.cyprienbrisset.fukkatsunop.ui.sumi.WatermarkKanji
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.OnShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import kotlin.math.roundToInt

private const val PREFS_NAME = "widget_dashboard"
private const val PREFS_KEY = "widget_layouts_v3"
private const val DEFAULT_H = 160
private const val DEFAULT_W = 100

// widthPct: 50, 67, 100 — width as percent of available parent width
data class WidgetLayout(val id: Int, val heightDp: Int = DEFAULT_H, val widthPct: Int = DEFAULT_W)

private val WIDTH_STEPS = listOf(50, 67, 100)

private fun loadLayouts(ctx: Context): List<WidgetLayout> {
    val raw = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREFS_KEY, "") ?: ""
    if (raw.isBlank()) return emptyList()
    return raw.split(",").mapNotNull {
        val p = it.trim().split(":")
        when (p.size) {
            3 -> WidgetLayout(
                p[0].toIntOrNull() ?: return@mapNotNull null,
                p[1].toIntOrNull() ?: DEFAULT_H,
                p[2].toIntOrNull() ?: DEFAULT_W,
            )
            2 -> WidgetLayout(p[0].toIntOrNull() ?: return@mapNotNull null, p[1].toIntOrNull() ?: DEFAULT_H)
            else -> null
        }
    }
}

private fun saveLayouts(ctx: Context, layouts: List<WidgetLayout>) {
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(PREFS_KEY, layouts.joinToString(",") { "${it.id}:${it.heightDp}:${it.widthPct}" })
        .apply()
}

@Composable
fun WidgetDashboard() {
    val ctx = LocalContext.current
    var layouts by remember { mutableStateOf(loadLayouts(ctx)) }
    var editMode by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    var pendingPickId by remember { mutableStateOf<Int?>(null) }
    var pendingInfo by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }

    // Drag-to-reorder state (pixels)
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val itemHeightsPx = remember { mutableStateMapOf<Int, Float>() }

    fun insertionIndex(fromIdx: Int, offsetPx: Float): Int {
        return if (offsetPx >= 0f) {
            var remaining = offsetPx; var target = fromIdx; var below = fromIdx + 1
            while (below < layouts.size) {
                val h = itemHeightsPx[below] ?: return target
                if (remaining < h / 2f) return target
                remaining -= h; target = below++
            }
            target
        } else {
            var remaining = -offsetPx; var target = fromIdx; var above = fromIdx - 1
            while (above >= 0) {
                val h = itemHeightsPx[above] ?: return target
                if (remaining < h / 2f) return target
                remaining -= h; target = above--
            }
            target
        }
    }

    DisposableEffect(Unit) {
        AppWidgetHostHelper.startListening(ctx)
        onDispose { AppWidgetHostHelper.stopListening(ctx) }
    }

    // Config launcher — called after bind if widget needs setup
    val configLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingPickId ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            layouts = layouts + WidgetLayout(id)
            saveLayouts(ctx, layouts)
        } else {
            AppWidgetHostHelper.deleteId(ctx, id)
        }
        pendingPickId = null; pendingInfo = null
    }

    fun launchConfigIfNeeded(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            pendingPickId = id
            pendingInfo = info
            configLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            })
        } else {
            layouts = layouts + WidgetLayout(id)
            saveLayouts(ctx, layouts)
            pendingPickId = null; pendingInfo = null
        }
    }

    // Bind launcher — system dialog to grant bind permission
    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingPickId ?: return@rememberLauncherForActivityResult
        val info = pendingInfo ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            launchConfigIfNeeded(id, info)
        } else {
            AppWidgetHostHelper.deleteId(ctx, id)
            pendingPickId = null; pendingInfo = null
        }
    }

    fun handlePicked(info: AppWidgetProviderInfo) {
        showPicker = false
        val id = AppWidgetHostHelper.allocateId(ctx)
        pendingPickId = id
        pendingInfo = info
        val bound = AppWidgetManager.getInstance(ctx).bindAppWidgetIdIfAllowed(id, info.provider)
        if (bound) {
            launchConfigIfNeeded(id, info)
        } else {
            bindLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            })
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0E0E))) {
        WatermarkKanji("風", Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 60.dp))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionLabel("ウィジェット", "MES WIDGETS", modifier = Modifier.weight(1f))
                if (editMode) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2A2A2A))
                            .clickable { editMode = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Terminer", tint = Shu, modifier = Modifier.size(18.dp))
                    }
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Shu).clickable { showPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Ajouter un widget", tint = OnShu, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            if (layouts.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Aucun widget", color = Kinari.copy(alpha = 0.35f), fontFamily = Mincho, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Appuie sur + pour en ajouter", color = Kinari.copy(alpha = 0.2f), fontFamily = Mincho, fontSize = 12.sp)
                    }
                }
            } else {
                val dIdx = draggingIndex
                val insertIdx = if (dIdx != null) insertionIndex(dIdx, dragOffsetPx) else -1

                layouts.forEachIndexed { index, layout ->
                    val isDragging = dIdx == index
                    val draggedH = if (dIdx != null) itemHeightsPx[dIdx] ?: 0f else 0f

                    val shiftTarget = when {
                        isDragging || dIdx == null -> 0f
                        dIdx < insertIdx && index in (dIdx + 1)..insertIdx -> -draggedH
                        dIdx > insertIdx && index in insertIdx until dIdx -> draggedH
                        else -> 0f
                    }
                    val animatedShift by animateFloatAsState(shiftTarget, spring(), label = "shift$index")
                    val yOffsetPx = if (isDragging) dragOffsetPx else animatedShift

                    WidgetCard(
                        layout = layout,
                        editMode = editMode,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 12.dp)
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, yOffsetPx.roundToInt()) }
                            .onGloballyPositioned { itemHeightsPx[index] = it.size.height.toFloat() },
                        onLongPress = { editMode = true },
                        onDelete = { pendingDeleteId = layout.id },
                        onResizeH = { newH ->
                            val m = layouts.toMutableList()
                            m[index] = layout.copy(heightDp = newH)
                            layouts = m; saveLayouts(ctx, layouts)
                        },
                        onResizeW = { newPct ->
                            val m = layouts.toMutableList()
                            m[index] = layout.copy(widthPct = newPct)
                            layouts = m; saveLayouts(ctx, layouts)
                        },
                        onDragStart = { draggingIndex = index; dragOffsetPx = 0f },
                        onDrag = { dy -> dragOffsetPx += dy },
                        onDragEnd = {
                            val to = insertionIndex(index, dragOffsetPx)
                            if (to != index) {
                                val m = layouts.toMutableList()
                                val item = m.removeAt(index); m.add(to, item)
                                layouts = m; saveLayouts(ctx, layouts)
                            }
                            draggingIndex = null; dragOffsetPx = 0f
                        },
                    )
                }
                Spacer(Modifier.height(48.dp))
            }
        }

        if (pendingDeleteId != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text("Supprimer ce widget ?", fontFamily = Mincho, fontWeight = FontWeight.SemiBold) },
                confirmButton = {
                    TextButton(onClick = {
                        val id = pendingDeleteId!!
                        layouts = layouts.filterNot { it.id == id }
                        saveLayouts(ctx, layouts)
                        AppWidgetHostHelper.deleteId(ctx, id)
                        pendingDeleteId = null
                    }) { Text("Supprimer", color = Shu, fontFamily = Mincho) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) {
                        Text("Annuler", color = Kinari.copy(alpha = 0.6f), fontFamily = Mincho)
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Kinari,
            )
        }
    }

    if (showPicker) {
        WidgetPickerSheet(
            onDismiss = { showPicker = false },
            onPicked = { info -> handlePicked(info) },
        )
    }
}

@Composable
private fun WidgetCard(
    layout: WidgetLayout,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onResizeH: (Int) -> Unit,
    onResizeW: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val info = remember(layout.id) { AppWidgetManager.getInstance(ctx).getAppWidgetInfo(layout.id) }
    val pm = ctx.packageManager

    val widgetLabel = remember(info) { info?.loadLabel(pm)?.toString() ?: "" }
    val appName = remember(info) {
        val pkg = info?.provider?.packageName ?: return@remember ""
        runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrElse { pkg }
    }
    val appIcon = remember(info) {
        val pkg = info?.provider?.packageName ?: return@remember null
        runCatching { pm.getApplicationIcon(pkg).toBitmap().asImageBitmap() }.getOrNull()
    }

    val minHeightDp = remember(info) { maxOf(info?.minHeight ?: 80, 80) }
    var heightDp by remember(layout.heightDp) { mutableIntStateOf(layout.heightDp) }
    var widthPct by remember(layout.widthPct) { mutableIntStateOf(layout.widthPct) }

    // Width indicator visible only while dragging the width handle
    var showWidthHint by remember { mutableStateOf(false) }
    var widthDragAccum by remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .fillMaxWidth(widthPct / 100f)
            .shadow(if (editMode) 14.dp else 2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A))
            .pointerInput(editMode) {
                if (!editMode) detectTapGestures(onLongPress = { onLongPress() })
            },
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ── Header ─────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF202020))
                    .then(
                        if (editMode) Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { _, d -> onDrag(d.y) },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            )
                        } else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (editMode) {
                    Icon(Icons.Rounded.DragHandle, contentDescription = "Réorganiser", tint = Kinari.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }
                if (appIcon != null) {
                    Image(BitmapPainter(appIcon), contentDescription = null, modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)))
                }
                Column(Modifier.weight(1f)) {
                    if (widgetLabel.isNotBlank()) {
                        Text(widgetLabel, color = Kinari, fontFamily = Mincho, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (appName.isNotBlank() && appName != widgetLabel) {
                        Text(appName, color = Kinari.copy(alpha = 0.38f), fontFamily = Mincho, fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (editMode) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF3D1515)).clickable { onDelete() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Supprimer", tint = Color(0xFFFF6B6B), modifier = Modifier.size(14.dp))
                    }
                }
            }

            // ── Widget content ──────────────────────────────────────────────
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(heightDp.dp),
                factory = { context ->
                    AppWidgetHostHelper.createView(context, layout.id).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
            )

            // ── Height resize handle ────────────────────────────────────────
            if (editMode) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(Color(0xFF1D1D1D))
                        .pointerInput(minHeightDp) {
                            detectDragGestures { _, dragAmount ->
                                val delta = with(density) { dragAmount.y.toDp().value.toInt() }
                                heightDp = (heightDp + delta).coerceIn(minHeightDp, 600)
                                onResizeH(heightDp)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DragHandle, contentDescription = "Hauteur", tint = Kinari.copy(alpha = 0.18f), modifier = Modifier.size(16.dp))
                }
            }
        }

        // ── Width resize handle (right edge, visible in edit mode) ──────────
        if (editMode) {
            Box(
                Modifier
                    .width(22.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .background(Color(0xFF2A2A2A).copy(alpha = 0.85f))
                    .pointerInput(widthPct) {
                        detectDragGestures(
                            onDragStart = { showWidthHint = true; widthDragAccum = 0f },
                            onDragEnd = { showWidthHint = false; widthDragAccum = 0f },
                            onDragCancel = { showWidthHint = false; widthDragAccum = 0f },
                        ) { _, dragAmount ->
                            widthDragAccum += dragAmount.x
                            // Each 40px of drag snaps to next/prev step
                            val steps = (widthDragAccum / 40f).toInt()
                            if (steps != 0) {
                                val currentIdx = WIDTH_STEPS.indexOf(widthPct).takeIf { it >= 0 } ?: 2
                                val newIdx = (currentIdx + steps).coerceIn(0, WIDTH_STEPS.lastIndex)
                                if (newIdx != currentIdx) {
                                    widthPct = WIDTH_STEPS[newIdx]
                                    onResizeW(widthPct)
                                    widthDragAccum -= steps * 40f
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = "Largeur",
                    tint = Kinari.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp),
                )
            }

            // Width percentage pill shown while dragging
            if (showWidthHint) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF000000).copy(alpha = 0.75f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("$widthPct%", color = Kinari, fontFamily = Mincho, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                }
            }
        }
    }
}
