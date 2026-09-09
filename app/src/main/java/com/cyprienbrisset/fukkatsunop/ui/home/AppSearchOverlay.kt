package com.cyprienbrisset.fukkatsunop.ui.home

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.launch.LaunchIntentResolver
import com.cyprienbrisset.fukkatsunop.ui.sumi.Medallion
import com.cyprienbrisset.fukkatsunop.ui.theme.Sumi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SearchAppEntry(val packageName: String, val label: String)

private object InstalledAppsCache {
    @Volatile var apps: List<SearchAppEntry>? = null
}

@Composable
fun AppSearchOverlay(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val allApps by produceState<List<SearchAppEntry>>(InstalledAppsCache.apps ?: emptyList()) {
        val cached = InstalledAppsCache.apps
        if (cached != null) { value = cached; return@produceState }
        value = withContext(Dispatchers.IO) {
            ctx.packageManager.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .filter { it.packageName != ctx.packageName }
                .map { SearchAppEntry(it.packageName, ctx.packageManager.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
                .also { InstalledAppsCache.apps = it }
        }
    }

    val results = remember(query, allApps) {
        if (query.isBlank()) allApps
        else allApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Sumi.copy(alpha = 0.96f))
            .pointerInput(Unit) {
                var totalY = 0f
                detectDragGestures(
                    onDragEnd = {
                        if (totalY > 80f) {
                            focusManager.clearFocus()
                            onDismiss()
                        }
                        totalY = 0f
                    },
                    onDragCancel = { totalY = 0f },
                ) { _, delta -> totalY += delta.y }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Rechercher une application…",
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = Color.White,
                    ),
                )
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { focusManager.clearFocus(); onDismiss() }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Fermer",
                        tint = Color.White,
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(results, key = { it.packageName }) { app ->
                    val fakeTile = TileEntity(
                        id = 0L,
                        type = TileType.APP,
                        label = app.label,
                        packageName = app.packageName,
                        url = null,
                        iconRef = null,
                        position = 0,
                    )
                    Medallion(
                        label = app.label,
                        onClick = {
                            LaunchIntentResolver.launch(ctx, app.packageName)
                            onDismiss()
                        },
                        disc = false,
                    ) {
                        TileIcon(tile = fakeTile, size = 56.dp)
                    }
                }
            }
        }
    }
}
