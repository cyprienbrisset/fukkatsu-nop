package com.cyprienbrisset.myportal.ui.google

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.ui.sumi.SealIconButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface
import kotlinx.coroutines.launch
import androidx.compose.material3.Text

@Composable
fun GoogleScreen(modifier: Modifier = Modifier) {
    val vm: GoogleViewModel = viewModel(factory = GoogleViewModel.Factory(LocalContext.current))
    val uiState by vm.state.collectAsState()

    if (uiState.authState !is AuthState.LoggedIn) {
        GoogleAuthScreen(authState = uiState.authState, onStartAuth = { vm.startDeviceFlow() })
        return
    }

    // Connected view
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val tabs = listOf("Agenda", "Meet")
    val selectedTab = pagerState.currentPage

    Column(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(SumiSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 44.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                tabs.forEachIndexed { index, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontFamily = Mincho,
                            fontSize = 14.sp,
                            color = if (selectedTab == index) Kinari else SumiMuted,
                        )
                        if (selectedTab == index) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Shu),
                            )
                        }
                    }
                }
            }

            SealIconButton(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = "Déconnexion",
                onClick = { vm.logout() },
                size = 36.dp,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> AgendaTab(state = uiState.agenda, onRetry = { vm.retryAgenda() })
                1 -> MeetTab(state = uiState.agenda, onRetry = { vm.retryAgenda() })
            }
        }
    }
}
