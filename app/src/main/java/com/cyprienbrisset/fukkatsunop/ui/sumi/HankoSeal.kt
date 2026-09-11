package com.cyprienbrisset.fukkatsunop.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.ui.theme.OnShu
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu

@Composable
fun HankoSeal(char: String, modifier: Modifier = Modifier, size: Dp = 46.dp, onClick: (() -> Unit)? = null) {
    val base = Modifier.size(size).clip(RoundedCornerShape(size / 5)).background(AccentShu)
        .let { if (onClick != null) it.clickable { onClick() } else it }
    Box(modifier.then(base), contentAlignment = Alignment.Center) {
        Text(char, color = OnShu, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.1f).sp)
    }
}
