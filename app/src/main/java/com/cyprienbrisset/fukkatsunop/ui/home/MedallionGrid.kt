package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayReceiver
import com.cyprienbrisset.fukkatsunop.airplay.AirPlayState
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.ui.sumi.Medallion
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MedallionGrid(
    tiles: List<TileEntity>,
    minCellWidth: Dp,
    onTileClick: (TileEntity) -> Unit,
    onLongClick: (TileEntity) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCounts: Map<String, Int> = emptyMap(),
    reorderMode: Boolean = false,
    onReorder: ((List<TileEntity>) -> Unit)? = null,
    onEnterReorder: (() -> Unit)? = null,
) {
    val lazyGridState = rememberLazyGridState()
    var displayTiles by remember { mutableStateOf(tiles) }
    var draggingKey by remember { mutableStateOf<Long?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    // Set to true by a tile's onLongClick so the grid-level long press ignores the same event.
    var tileConsumedLongPress by remember { mutableStateOf(false) }

    // Collecté une seule fois au niveau du grid — évite N collectAsState() dans items()
    // qui causaient une recomposition de toute la grille à chaque changement AirPlay.
    val airPlayState by AirPlayReceiver.state.collectAsState()

    LaunchedEffect(tiles) { if (draggingKey == null) displayTiles = tiles }
    LaunchedEffect(reorderMode) { if (!reorderMode) displayTiles = tiles }

    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier.fillMaxSize().then(
            if (!reorderMode && onEnterReorder != null) {
                Modifier.pointerInput(onEnterReorder) {
                    awaitEachGesture {
                        tileConsumedLongPress = false
                        val down = awaitFirstDown(requireUnconsumed = false)
                        awaitLongPressOrCancellation(down.id)?.let {
                            if (!tileConsumedLongPress) onEnterReorder()
                        }
                        tileConsumedLongPress = false
                    }
                }
            } else Modifier,
        ),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(displayTiles, key = { it.id }) { tile ->
            val isAirPlayLive = tile.type == TileType.AIRPLAY && airPlayState is AirPlayState.Streaming
            val isDragging = reorderMode && draggingKey == tile.id
            // Animatable + LaunchedEffect : l'animation ne tourne que pendant reorderMode.
            // rememberInfiniteTransition maintenait le Choreographer à 60 fps pour TOUS les tiles.
            val jiggle = remember { Animatable(0f) }
            LaunchedEffect(reorderMode, isDragging) {
                if (!reorderMode || isDragging) {
                    jiggle.snapTo(0f)
                    return@LaunchedEffect
                }
                val initial = if (tile.id % 2 == 0L) -1.8f else 1.8f
                val dur = 110 + (tile.id % 3).toInt() * 25
                jiggle.snapTo(initial)
                while (isActive) {
                    jiggle.animateTo(-initial, animationSpec = tween(dur, easing = LinearEasing))
                    jiggle.animateTo(initial,  animationSpec = tween(dur, easing = LinearEasing))
                }
            }
            val jiggleAngle = jiggle.value
            Box(
                modifier = Modifier.then(
                    if (isAirPlayLive)
                        Modifier.border(2.dp, Shu, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    else Modifier
                )
            ) {
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragDelta.x
                            translationY = dragDelta.y
                            alpha = 0.8f
                            scaleX = 1.08f
                            scaleY = 1.08f
                        } else if (reorderMode) {
                            rotationZ = jiggleAngle
                        }
                    }
                    .then(if (reorderMode) Modifier.animateItemPlacement() else Modifier),
            ) {
                Medallion(
                    label = tile.label,
                    onClick = { if (!reorderMode) onTileClick(tile) },
                    onLongClick = if (!reorderMode) {{
                        tileConsumedLongPress = true
                        onLongClick(tile)
                    }} else null,
                    disc = false,
                ) {
                    BadgedTileIcon(
                        tile = tile,
                        badgeCount = tile.packageName?.let { badgeCounts[it] } ?: 0,
                    )
                }
                // In reorder mode: transparent overlay captures drag gestures without conflicting
                // with Medallion's combinedClickable, since it renders on top and intercepts first.
                if (reorderMode) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(tile.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingKey = tile.id
                                        dragDelta = Offset.Zero
                                    },
                                    onDrag = { change, offset ->
                                        change.consume()
                                        dragDelta += offset
                                        val tileInfo = lazyGridState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == tile.id }
                                            ?: return@detectDragGesturesAfterLongPress
                                        val center = Offset(
                                            tileInfo.offset.x + tileInfo.size.width / 2f + dragDelta.x,
                                            tileInfo.offset.y + tileInfo.size.height / 2f + dragDelta.y,
                                        )
                                        val target = lazyGridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                            info.key != tile.id && info.key != "__add__" &&
                                                center.x.toInt() in info.offset.x..(info.offset.x + info.size.width) &&
                                                center.y.toInt() in info.offset.y..(info.offset.y + info.size.height)
                                        }
                                        if (target != null) {
                                            val from = displayTiles.indexOfFirst { it.id == tile.id }
                                            val to = displayTiles.indexOfFirst { it.id == (target.key as? Long) }
                                            if (from >= 0 && to >= 0 && from != to) {
                                                displayTiles = displayTiles.toMutableList().also { list ->
                                                    list.add(to, list.removeAt(from))
                                                }
                                                dragDelta = Offset.Zero
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingKey = null
                                        dragDelta = Offset.Zero
                                        onReorder?.invoke(displayTiles)
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragDelta = Offset.Zero
                                        displayTiles = tiles
                                    },
                                )
                            },
                    )
                }
            }
            } // end border Box
        }
        if (!reorderMode) {
            item(key = "__add__") {
                Medallion(label = "Ajouter", onClick = onAddClick, dashed = true) {
                    Text("＋", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun BadgedTileIcon(tile: TileEntity, badgeCount: Int) {
    Box(Modifier.size(72.dp)) {
        TileIcon(tile = tile, size = 64.dp, modifier = Modifier.align(Alignment.Center))
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .defaultMinSize(minWidth = 18.dp)
                    .height(18.dp)
                    .clip(CircleShape)
                    .background(Shu)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 8.sp,
                )
            }
        }
    }
}
