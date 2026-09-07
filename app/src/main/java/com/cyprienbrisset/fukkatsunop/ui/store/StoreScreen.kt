package com.cyprienbrisset.fukkatsunop.ui.store

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cyprienbrisset.fukkatsunop.launch.LaunchIntentResolver
import com.cyprienbrisset.fukkatsunop.store.FukkaLoginActivity
import com.cyprienbrisset.fukkatsunop.store.StoreApp
import com.cyprienbrisset.fukkatsunop.ui.sumi.HankoSeal
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiChoiceChip
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.OnShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiLine
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface

/** Standalone store screen (reached from Réglages → FukkaStore). */
@Composable
fun StoreScreen(onBack: () -> Unit, showBack: Boolean = true, vm: StoreViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("店", size = 40.dp, onClick = if (showBack) onBack else null)
            Spacer(Modifier.width(14.dp))
            Text("FukkaStore", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }
        StoreBody(Modifier.weight(1f), vm)
    }
}

/**
 * The reusable store body: login gate → search field → category chips → app grid → install.
 * No screen chrome; embedded both in [StoreScreen] and as the "Store" tab of the tile-add page.
 */
@Composable
fun StoreBody(modifier: Modifier = Modifier, vm: StoreViewModel = viewModel()) {
    val ctx = LocalContext.current
    val loggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val content by vm.content.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    LaunchedEffect(loggedIn) { if (loggedIn) vm.loadHome() }

    val selectedApp by vm.selectedApp.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val detailLoading by vm.detailLoading.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth()) {
        if (!loggedIn) {
            Text(
                "Connectez-vous à votre compte Google pour rechercher et installer des applications.",
                color = SumiMuted,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(20.dp))
            SumiPrimaryButton("Se connecter", onClick = {
                ctx.startActivity(Intent(ctx, FukkaLoginActivity::class.java))
            })
            return@Column
        }

        SumiSearchField(
            value = query,
            onValueChange = { query = it },
            onSearch = { vm.search(query) },
            onClear = { query = ""; vm.search("") },
        )
        Spacer(Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories, key = { it.key }) { cat ->
                SumiChoiceChip(
                    text = cat.title,
                    selected = selectedKey == cat.key,
                    onClick = { vm.selectCategory(cat) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        AppResults(
            state = content,
            progress = progress,
            onInstall = { vm.install(it) },
            onOpen = { LaunchIntentResolver.launch(ctx, it.packageName) },
            onDetail = { vm.loadDetail(it) },
        )
    }

    val sel = selectedApp
    if (sel != null) {
        AppDetailSheet(
            app = sel,
            detail = detail,
            pct = progress[sel.packageName],
            loading = detailLoading,
            onDismiss = { vm.clearDetail() },
            onInstall = { vm.install(sel) },
            onOpen = { LaunchIntentResolver.launch(ctx, sel.packageName) },
        )
    }
}

@Composable
private fun SumiSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp))
            .background(SumiSurface).border(1.dp, SumiLine, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = SumiMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Rechercher une application", color = SumiMuted, fontSize = 16.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Kinari, fontSize = 16.sp),
                cursorBrush = SolidColor(Shu),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.Close, contentDescription = "Effacer", tint = SumiMuted,
                modifier = Modifier.size(22.dp).clickable { onClear() },
            )
        }
    }
}

@Composable
private fun AppResults(
    state: StoreUi,
    progress: Map<String, Int>,
    onInstall: (StoreApp) -> Unit,
    onOpen: (StoreApp) -> Unit,
    onDetail: (StoreApp) -> Unit,
) {
    when (state) {
        is StoreUi.Idle -> {}
        is StoreUi.Loading -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Shu)
            }
        }
        is StoreUi.Error -> Text(state.message, color = SumiMuted, fontSize = 15.sp)
        is StoreUi.Results -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.apps, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        pct = progress[app.packageName],
                        onInstall = { onInstall(app) },
                        onOpen = { onOpen(app) },
                        onClick = { onDetail(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: StoreApp, pct: Int?, onInstall: () -> Unit, onOpen: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SumiSurface)
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = app.iconUrl,
            contentDescription = app.title,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.title, color = Kinari, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.developer, color = SumiMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        when {
            pct == null -> Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Shu)
                    .clickable { onInstall() }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Installer", color = OnShu, fontFamily = Mincho, fontSize = 14.sp) }
            pct in 0..99 -> Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(SumiLine).padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("$pct %", color = Kinari, fontSize = 14.sp) }
            pct == InstallProgress.INSTALLING -> Text("Installation…", color = SumiMuted, fontSize = 14.sp)
            pct == InstallProgress.INSTALLED -> Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(SumiLine)
                    .clickable { onOpen() }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Ouvrir", color = Kinari, fontFamily = Mincho, fontSize = 14.sp) }
            else -> Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(SumiSurface)
                    .clickable { onInstall() }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("Réessayer", color = Shu, fontFamily = Mincho, fontSize = 14.sp) }
        }
    }
}
