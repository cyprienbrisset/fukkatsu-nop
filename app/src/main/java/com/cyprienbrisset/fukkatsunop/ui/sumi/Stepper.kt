package com.cyprienbrisset.fukkatsunop.ui.sumi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiLine
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiMuted
import com.cyprienbrisset.fukkatsunop.ui.theme.SumiSurface

@Composable
fun Stepper(value: Int, onUp: () -> Unit, onDown: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chevron("▲", onUp)
        Text("%02d".format(value), fontFamily = Mincho, color = Kinari, fontSize = 68.sp, textAlign = TextAlign.Center, modifier = Modifier.width(104.dp))
        Chevron("▼", onDown)
    }
}

@Composable
private fun Chevron(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.width(72.dp).height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(SumiSurface).border(BorderStroke(1.dp, SumiLine), RoundedCornerShape(12.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = SumiMuted, fontSize = 18.sp) }
}
