package com.cyprienbrisset.fukkatsunop.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.AccentShu
import com.cyprienbrisset.fukkatsunop.ui.theme.OnShu

@Composable
fun SumiPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(AccentShu).clickable { onClick() },
        contentAlignment = Alignment.Center) {
        Text(text, color = OnShu, fontFamily = Mincho, fontSize = 18.sp)
    }
}
