package com.cyprienbrisset.fukkatsunop.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu

private data class AppGroup(
    val appLabel: String,
    val appIcon: ImageBitmap?,
    val widgets: List<AppWidgetProviderInfo>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetPickerSheet(
    onDismiss: () -> Unit,
    onPicked: (AppWidgetProviderInfo) -> Unit,
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager

    val groups = remember {
        AppWidgetManager.getInstance(ctx).installedProviders
            .groupBy { it.provider.packageName }
            .entries
            .map { (pkg, infos) ->
                val label = runCatching {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                }.getOrElse { pkg }
                val icon = runCatching {
                    pm.getApplicationIcon(pkg).toBitmap().asImageBitmap()
                }.getOrNull()
                AppGroup(label, icon, infos.sortedBy { it.loadLabel(pm)?.toString() ?: "" })
            }
            .sortedBy { it.appLabel }
    }

    var query by remember { mutableStateOf("") }
    val filtered = remember(query, groups) {
        if (query.isBlank()) groups
        else groups.mapNotNull { g ->
            val w = g.widgets.filter {
                (it.loadLabel(pm)?.toString() ?: "").contains(query, ignoreCase = true) ||
                    g.appLabel.contains(query, ignoreCase = true)
            }
            if (w.isEmpty()) null else g.copy(widgets = w)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(620.dp)
                    .heightIn(max = 680.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF111111))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                // ── Header ──────────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ウィジェット", color = Shu, fontFamily = Mincho, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text("Choisir un widget", color = Kinari, fontFamily = Mincho, fontWeight = FontWeight.Medium, fontSize = 20.sp)
                    }
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF232323))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Kinari.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
                    }
                }

                // ── Search ──────────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1C1C1C))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Kinari.copy(alpha = 0.22f), modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Rechercher un widget…", color = Kinari.copy(alpha = 0.18f), fontFamily = Mincho, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Kinari, fontFamily = Mincho, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF2A2A2A))
                                .clickable { query = "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Effacer", tint = Kinari.copy(alpha = 0.4f), modifier = Modifier.size(11.dp))
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 1.dp)

                // ── Widget list ──────────────────────────────────────────────
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text("Aucun résultat", color = Kinari.copy(alpha = 0.25f), fontFamily = Mincho, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        filtered.forEach { group ->
                            stickyHeader(key = "h_${group.appLabel}") {
                                Row(
                                    Modifier.fillMaxWidth().background(Color(0xFF111111))
                                        .padding(horizontal = 24.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (group.appIcon != null) {
                                        Image(
                                            BitmapPainter(group.appIcon),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)),
                                        )
                                    }
                                    Text(
                                        group.appLabel,
                                        color = Kinari.copy(alpha = 0.35f),
                                        fontFamily = Mincho,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                            items(group.widgets, key = { "${it.provider}" }) { info ->
                                PickerItem(info = info, pm = pm, onClick = { onPicked(info) })
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerItem(
    info: AppWidgetProviderInfo,
    pm: PackageManager,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val label = remember(info) { info.loadLabel(pm)?.toString() ?: "" }

    val preview = remember(info) {
        runCatching {
            if (info.previewImage != 0) {
                ctx.packageManager
                    .getResourcesForApplication(info.provider.packageName)
                    .getDrawable(info.previewImage, null)
                    ?.toBitmap()?.asImageBitmap()
            } else null
        }.getOrNull()
    }
    val appIcon = remember(info) {
        runCatching { pm.getApplicationIcon(info.provider.packageName).toBitmap().asImageBitmap() }.getOrNull()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Thumbnail
        Box(
            Modifier
                .size(width = 92.dp, height = 66.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF191919)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                preview != null -> Image(
                    BitmapPainter(preview),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
                appIcon != null -> Image(
                    BitmapPainter(appIcon),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                label,
                color = Kinari,
                fontFamily = Mincho,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${info.minWidth} × ${info.minHeight} dp",
                color = Kinari.copy(alpha = 0.22f),
                fontFamily = Mincho,
                fontSize = 11.sp,
            )
        }

        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Shu.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Ajouter", tint = Shu, modifier = Modifier.size(16.dp))
        }
    }

    HorizontalDivider(
        Modifier.padding(start = 128.dp, end = 20.dp),
        color = Color(0xFF191919),
        thickness = 0.5.dp,
    )
}
