package com.cyprienbrisset.fukkatsunop.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu

@Composable
fun HorizontalVermilionRule(modifier: Modifier = Modifier, length: Dp = 120.dp, thickness: Dp = 1.dp) {
    Box(modifier.width(length).height(thickness).background(Brush.horizontalGradient(listOf(Shu, Color.Transparent))))
}

@Composable
fun VerticalVermilionRule(modifier: Modifier = Modifier, length: Dp = 120.dp, thickness: Dp = 1.dp) {
    Box(modifier.width(thickness).height(length).background(Brush.verticalGradient(listOf(Color.Transparent, Shu, Color.Transparent))))
}
