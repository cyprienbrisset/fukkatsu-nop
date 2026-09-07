package com.cyprienbrisset.fukkatsunop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface

@Composable
fun WeatherSettingsScreen(onBack: () -> Unit, vm: WeatherSettingsViewModel = viewModel()) {
    var query by remember { mutableStateOf("") }
    val results by vm.results.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("天", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Ville météo", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }
        OutlinedTextField(query, { query = it; vm.search(it) }, label = { Text("Rechercher une ville") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(results) { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp))
                        .background(SumiSurface).clickable { vm.select(r); onBack() }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(r.label, color = Kinari, fontSize = 17.sp) }
            }
        }
    }
}
