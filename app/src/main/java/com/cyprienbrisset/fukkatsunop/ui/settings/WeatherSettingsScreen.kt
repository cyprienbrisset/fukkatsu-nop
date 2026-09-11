package com.cyprienbrisset.fukkatsunop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.fukkatsunop.ui.sumi.HankoSeal
import com.cyprienbrisset.fukkatsunop.ui.sumi.SectionLabel
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface

@Composable
fun WeatherSettingsScreen(onBack: () -> Unit, vm: WeatherSettingsViewModel = viewModel()) {
    var query by remember { mutableStateOf("") }
    val results by vm.results.collectAsState()
    val cities by vm.cities.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HankoSeal("天", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Villes météo", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }

        // Liste des villes sélectionnées
        if (cities.isNotEmpty()) {
            SectionLabel("地", "VILLES ACTIVES — SWIPER POUR CHANGER")
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cities.forEachIndexed { index, loc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SumiSurface)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (index == 0) AccentShu else SumiMuted.copy(alpha = 0.4f)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(loc.city, color = Kinari, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Retirer",
                            tint = SumiMuted,
                            modifier = Modifier.size(20.dp).clickable { vm.removeCity(index) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Recherche
        SectionLabel("検", "AJOUTER UNE VILLE")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            query,
            { query = it; if (it.length >= 2) vm.search(it) else Unit },
            label = { Text("Rechercher…") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results) { r ->
                val alreadyAdded = cities.any { it.lat == r.lat && it.lon == r.lon }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SumiSurface)
                        .clickable(enabled = !alreadyAdded && cities.size < 5) { vm.addCity(r) }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        r.label,
                        color = if (alreadyAdded) SumiMuted else Kinari,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (alreadyAdded) {
                        Text("✓", color = AccentShu, fontSize = 14.sp)
                    }
                }
            }
            if (cities.size >= 5) {
                item {
                    Text(
                        "Maximum 5 villes",
                        color = SumiMuted,
                        fontFamily = Mincho,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
