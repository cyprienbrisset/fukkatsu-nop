package com.cyprienbrisset.fukkatsunop.ui.sumi

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho

@Composable
fun WatermarkKanji(char: String, modifier: Modifier = Modifier, size: TextUnit = 320.sp) {
    Text(char, modifier = modifier.alpha(0.05f), color = Kinari, fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = size)
}
