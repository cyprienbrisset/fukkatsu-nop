package com.cyprienbrisset.fukkatsunop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.data.tile.TileEntity
import com.cyprienbrisset.fukkatsunop.data.tile.TileType
import com.cyprienbrisset.fukkatsunop.launch.InstalledApp
import com.cyprienbrisset.fukkatsunop.ui.home.TileIcon
import com.cyprienbrisset.fukkatsunop.ui.store.StoreBody
import com.cyprienbrisset.fukkatsunop.ui.sumi.HankoSeal
import com.cyprienbrisset.fukkatsunop.ui.sumi.Medallion
import com.cyprienbrisset.fukkatsunop.ui.sumi.SectionLabel
import com.cyprienbrisset.fukkatsunop.ui.sumi.Segment
import com.cyprienbrisset.fukkatsunop.ui.sumi.SegmentedChoice
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho

@Composable
fun TileEditScreen(onBack: () -> Unit, vm: TileEditViewModel = viewModel()) {
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(0) } // 0=App, 1=Web
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var webLabel by remember { mutableStateOf("") }
    var webUrl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = vm.installedApps() }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("＋", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Tuiles", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }

        SegmentedChoice(
            listOf(Segment("アプリ", "Application"), Segment("ウェブ", "Web"), Segment("ストア", "Store"), Segment("システム", "Système")),
            selectedIndex = mode, onSelect = { mode = it },
        )
        Spacer(Modifier.height(22.dp))

        if (mode == 2) {
            // FukkaStore : rechercher et installer des apps sans passer par les Réglages.
            StoreBody(Modifier.weight(1f))
            return@Column
        }

        if (mode == 3) {
            SectionLabel("システム", "TUILES SYSTÈME")
            Spacer(Modifier.height(16.dp))
            Medallion(label = "Écran Mac", onClick = { vm.addAirPlay() }) {
                Icon(
                    imageVector = Icons.Rounded.Monitor,
                    contentDescription = "Écran Mac",
                    modifier = Modifier.size(46.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            return@Column
        }

        if (mode == 0) {
            SectionLabel("追加", "TOUCHEZ POUR AJOUTER")
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(apps, key = { it.packageName }) { a ->
                    Medallion(label = a.label, onClick = { vm.addApp(a.label, a.packageName) }) {
                        TileIcon(
                            tile = TileEntity(type = TileType.APP, label = a.label, packageName = a.packageName, position = 0),
                            size = 46.dp,
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(webLabel, { webLabel = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(webUrl, { webUrl = it }, label = { Text("URL (ex. jellyfin.local)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            SumiPrimaryButton("保存 · Ajouter", onClick = {
                if (webLabel.isNotBlank() && webUrl.isNotBlank()) { vm.addWeb(webLabel, webUrl); webLabel = ""; webUrl = "" }
            })
            Spacer(Modifier.weight(1f))
        }

        // Existing tiles — tap a medallion to delete (touch-first, no menu).
        if (tiles.isNotEmpty()) {
            SectionLabel("現在", "VOS TUILES — TOUCHEZ POUR SUPPRIMER")
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(150.dp),
            ) {
                items(tiles, key = { it.id }) { tile ->
                    Medallion(label = tile.label, onClick = { vm.delete(tile) }) {
                        TileIcon(tile = tile, size = 40.dp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
