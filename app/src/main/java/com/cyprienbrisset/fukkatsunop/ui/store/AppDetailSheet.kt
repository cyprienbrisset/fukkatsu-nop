package com.cyprienbrisset.fukkatsunop.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cyprienbrisset.fukkatsunop.store.AppDetail
import com.cyprienbrisset.fukkatsunop.store.StoreApp
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.OnShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiLine
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(
    app: StoreApp,
    detail: AppDetail?,
    pct: Int?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SumiSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            // Header: always available from StoreApp
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = app.iconUrl,
                    contentDescription = app.title,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.title, color = Kinari, fontSize = 19.sp, fontFamily = Mincho)
                    Spacer(Modifier.height(4.dp))
                    Text(app.developer, color = SumiMuted, fontSize = 14.sp)
                    val d = detail
                    if (d != null && d.ratingAverage > 0f) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val full = d.ratingAverage.toInt().coerceIn(0, 5)
                            Text(
                                buildString {
                                    repeat(full) { append('★') }
                                    repeat(5 - full) { append('☆') }
                                },
                                color = Shu, fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("%.1f".format(d.ratingAverage), color = SumiMuted, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            DetailInstallButton(pct = pct, onInstall = onInstall, onOpen = onOpen, modifier = Modifier.fillMaxWidth())

            if (loading) {
                Spacer(Modifier.height(48.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Shu, modifier = Modifier.size(32.dp))
                }
                return@Column
            }

            val d = detail ?: return@Column

            if (d.shortDescription.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(d.shortDescription, color = Kinari, fontSize = 15.sp, lineHeight = 22.sp)
            }

            if (d.screenshotUrls.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(d.screenshotUrls) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.height(180.dp).width(100.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }

            if (d.description.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = SumiLine)
                Spacer(Modifier.height(16.dp))
                Text(d.description, color = SumiMuted, fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun DetailInstallButton(pct: Int?, onInstall: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    when {
        pct == null -> Box(
            modifier.clip(RoundedCornerShape(14.dp)).background(Shu).clickable { onInstall() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Installer", color = OnShu, fontFamily = Mincho, fontSize = 16.sp) }
        pct in 0..99 -> Box(
            modifier.clip(RoundedCornerShape(14.dp)).background(SumiLine).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Téléchargement $pct %", color = Kinari, fontSize = 16.sp) }
        pct == InstallProgress.INSTALLING -> Box(
            modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center,
        ) { Text("Installation…", color = SumiMuted, fontSize = 16.sp) }
        pct == InstallProgress.INSTALLED -> Box(
            modifier.clip(RoundedCornerShape(14.dp)).background(SumiLine).clickable { onOpen() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Ouvrir", color = Kinari, fontFamily = Mincho, fontSize = 16.sp) }
        else -> Box(
            modifier.clip(RoundedCornerShape(14.dp)).background(SumiLine).clickable { onInstall() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Réessayer", color = Shu, fontFamily = Mincho, fontSize = 16.sp) }
    }
}
