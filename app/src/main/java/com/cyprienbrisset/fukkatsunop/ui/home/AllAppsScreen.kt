package com.cyprienbrisset.fukkatsunop.ui.home

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileRepository
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.integration.OpenAppsRepository
import com.cyprienbrisset.fukkatsunop.launch.LaunchIntentResolver
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiColors
import com.cyprienbrisset.fukkatsunop.ui.fuji.FujiIconButton
import com.cyprienbrisset.fukkatsunop.ui.fuji.glass
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.cyprienbrisset.fukkatsunop.ui.theme.Gothic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class InstalledEntry(val packageName: String, val label: String)

private object InstalledAppsCache {
    @Volatile var apps: List<InstalledEntry>? = null
}

private val LETTER_GROUPS = listOf("Tout" to "", "A–C" to "ABC", "D–F" to "DEF", "G–K" to "GHIJK", "L–N" to "LMN", "O–R" to "OPQR", "S–U" to "STU", "V–Z" to "VWXYZ")

/** Every installed app: search field + letter filters usable without the keyboard. */
@Composable
fun AllAppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableIntStateOf(0) }

    val apps by produceState(InstalledAppsCache.apps ?: emptyList()) {
        InstalledAppsCache.apps?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            ctx.packageManager.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || ctx.packageManager.getLaunchIntentForPackage(it.packageName) != null }
                .filter { it.packageName != ctx.packageName && ctx.packageManager.getLaunchIntentForPackage(it.packageName) != null }
                .map { InstalledEntry(it.packageName, ctx.packageManager.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
                .also { InstalledAppsCache.apps = it }
        }
    }

    val letters = LETTER_GROUPS[group].second
    val shown = apps.filter { e ->
        (query.isBlank() || e.label.contains(query, ignoreCase = true)) &&
            (letters.isEmpty() || e.label.firstOrNull()?.uppercaseChar()?.let { it in letters } == true)
    }

    Column(
        Modifier.fillMaxSize().padding(start = 72.dp, end = 72.dp, top = 48.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            FujiIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack)
            Row(
                Modifier.weight(1f).height(96.dp).glass(RoundedCornerShape(48.dp), FujiColors.GlassStrong).padding(horizontal = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(Icons.Rounded.Search, null, tint = FujiColors.Cream, modifier = Modifier.size(34.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Rechercher une app", color = FujiColors.Faint, fontFamily = Gothic, fontSize = 28.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = FujiColors.Cream, fontFamily = Gothic, fontSize = 28.sp),
                        cursorBrush = SolidColor(FujiColors.Cream),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) FujiIconButton(Icons.Rounded.Close, "Effacer", { query = "" }, size = 64.dp)
            }
            Text("${apps.size} apps", color = FujiColors.Muted, fontFamily = Gothic, fontSize = 22.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LETTER_GROUPS.forEachIndexed { i, (label, _) ->
                val on = i == group
                Box(
                    Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (on) FujiColors.Cream else FujiColors.Glass)
                        .clickable(role = Role.Tab) { group = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) FujiColors.Ink else FujiColors.Cream, fontFamily = Gothic, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(34.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(shown, key = { it.packageName }) { e ->
                AppCell(
                    e,
                    onClick = {
                        if (LaunchIntentResolver.launch(ctx, e.packageName)) OpenAppsRepository.record(ctx, e.packageName, e.label)
                    },
                    onLongClick = {
                        scope.launch {
                            val repo = TileRepository(AppDatabase.get(ctx).tileDao())
                            repo.add(TileEntity(type = TileType.APP, label = e.label, packageName = e.packageName, position = Int.MAX_VALUE))
                            Toast.makeText(ctx, "${e.label} ajoutée aux favoris", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }

        Text(
            "Appui long sur une app : l'ajouter aux favoris",
            color = FujiColors.Faint, fontFamily = Gothic, fontSize = 20.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(e: InstalledEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, e.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(e.packageName).toBitmap(192, 192).asImageBitmap() }.getOrNull()
        }
    }
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, null, Modifier.fillMaxSize()) }
                ?: Box(Modifier.fillMaxSize().background(FujiColors.GlassStrong))
        }
        Text(e.label, color = FujiColors.Cream, fontFamily = Gothic, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
