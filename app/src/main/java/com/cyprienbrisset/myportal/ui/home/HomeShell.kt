package com.cyprienbrisset.myportal.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.ui.google.GoogleScreen
import com.cyprienbrisset.myportal.ui.theme.AccentShu
import com.cyprienbrisset.myportal.ui.widgets.WidgetDashboard
import kotlinx.coroutines.launch

// Three-page shell: page 0 = widget dashboard, page 1 = home, page 2 = Google.
// Two-finger horizontal swipe switches pages (avoids conflict with single-finger gestures).
@Composable
fun HomeShell(onOpenSettings: () -> Unit, onAddTile: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val scope = rememberCoroutineScope()

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
                else -> HomeScreen(onOpenSettings = onOpenSettings, onAddTile = onAddTile)
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
    }
}
