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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.ui.sumi.SealIconButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.AccentShu
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface
import kotlinx.coroutines.launch

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
    var showNewEvent by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
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
                                    .background(AccentShu),
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SealIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Nouvel événement",
                    onClick = { showNewEvent = true },
                    size = 36.dp,
                )
                SealIconButton(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Déconnexion",
                    onClick = { vm.logout() },
                    size = 36.dp,
                )
            }
        }

        if (showNewEvent) {
            NewEventDialog(
                onDismiss = { showNewEvent = false },
                onCreate = { title, start, end ->
                    vm.createEvent(title, start, end)
                    showNewEvent = false
                },
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

// ── New event dialog ──────────────────────────────────────────────────────────

@Composable
private fun NewEventDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, start: java.time.Instant, end: java.time.Instant) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }   // YYYY-MM-DD
    var startStr by remember { mutableStateOf("") }  // HH:MM
    var endStr by remember { mutableStateOf("") }    // HH:MM
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.cyprienbrisset.myportal.ui.theme.SumiSurface,
        title = {
            Text("Nouvel événement", fontFamily = Mincho, color = Kinari, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Titre", title, "Réunion équipe") { title = it }
                LabeledField("Date", dateStr, "2026-09-15") { dateStr = it }
                LabeledField("Début", startStr, "14:00") { startStr = it }
                LabeledField("Fin", endStr, "15:00") { endStr = it }
                if (error != null) {
                    Text(error!!, color = Shu, fontFamily = Mincho, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton(
                text = "Créer",
                onClick = {
                    val result = runCatching {
                        val zone = java.time.ZoneId.systemDefault()
                        val date = java.time.LocalDate.parse(dateStr)
                        val start = java.time.LocalTime.parse(startStr)
                            .atDate(date).atZone(zone).toInstant()
                        val end = java.time.LocalTime.parse(endStr)
                            .atDate(date).atZone(zone).toInstant()
                        require(title.isNotBlank()) { "Titre requis" }
                        require(end.isAfter(start)) { "Fin doit être après le début" }
                        onCreate(title.trim(), start, end)
                    }
                    error = result.exceptionOrNull()?.message
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Annuler", color = SumiMuted, fontFamily = Mincho)
            }
        },
    )
}

@Composable
private fun LabeledField(label: String, value: String, placeholder: String, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SumiMuted, fontFamily = Mincho, fontSize = 12.sp)
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValue,
            placeholder = { Text(placeholder, color = SumiMuted, fontSize = 14.sp) },
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Shu,
                unfocusedBorderColor = com.cyprienbrisset.myportal.ui.theme.SumiLine,
                focusedTextColor = Kinari,
                unfocusedTextColor = Kinari,
                cursorColor = Shu,
            ),
        )
    }
}
