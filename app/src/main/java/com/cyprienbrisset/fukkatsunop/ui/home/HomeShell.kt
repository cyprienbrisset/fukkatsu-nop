package com.cyprienbrisset.fukkatsunop.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.system.voice.VoiceService
import com.cyprienbrisset.fukkatsunop.ui.google.GoogleScreen
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface
import com.cyprienbrisset.fukkatsunop.ui.widgets.WidgetDashboard
import kotlinx.coroutines.launch

// Three-page shell: page 0 = widget dashboard, page 1 = home, page 2 = Google.
// Two-finger horizontal swipe switches pages (avoids conflict with single-finger gestures).
@Composable
fun HomeShell(onOpenSettings: () -> Unit, onAddTile: () -> Unit, onOpenAlarms: () -> Unit = {}) {
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val scope = rememberCoroutineScope()
    val voiceState by VoiceService.state.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pagerState.currentPage) {
                val threshold = 60.dp.toPx()
                awaitEachGesture {
                    var maxPointers = 0
                    var totalDx = 0f
                    var prevCentroidX = Float.NaN

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val active = event.changes.filter { it.pressed }
                        if (active.size > maxPointers) maxPointers = active.size
                        // Track centroid only when 2+ fingers are down
                        if (active.size >= 2) {
                            val cx = active.map { it.position.x }.average().toFloat()
                            if (!prevCentroidX.isNaN()) totalDx += cx - prevCentroidX
                            prevCentroidX = cx
                        }
                    } while (event.changes.any { it.pressed })

                    // Switch page only if 2-finger gesture was used
                    if (maxPointers >= 2) {
                        val page = pagerState.currentPage
                        when {
                            totalDx > threshold && page > 0 ->
                                scope.launch { pagerState.animateScrollToPage(page - 1) }
                            totalDx < -threshold && page < 2 ->
                                scope.launch { pagerState.animateScrollToPage(page + 1) }
                        }
                    }
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,  // single-finger swipe disabled; 2-finger handled above
        ) { page ->
            when (page) {
                0 -> WidgetDashboard()
                2 -> GoogleScreen()
                else -> HomeScreen(onOpenSettings = onOpenSettings, onAddTile = onAddTile, onOpenAlarms = onOpenAlarms)
            }
        }

        // Three-dot page indicator
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val isSelected = pagerState.currentPage == index
                val size by animateDpAsState(if (isSelected) 8.dp else 5.dp, label = "dot")
                Box(
                    Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(AccentShu.copy(alpha = if (isSelected) 1f else 0.3f)),
                )
            }
        }

        // Voice listening banner — shown when in COMMAND mode (after "Portal" wake word)
        AnimatedVisibility(
            visible = voiceState == VoiceService.ListenState.COMMAND,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SumiSurface)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = AccentShu,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Parlez…",
                    color = Kinari,
                    fontFamily = Mincho,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
