package com.cyprienbrisset.fukkatsunop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.R

val Gothic = FontFamily(
    Font(R.font.zen_kaku_gothic_new_light, FontWeight.Light),
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_gothic_new_medium, FontWeight.Medium),
    Font(R.font.zen_kaku_gothic_new_bold, FontWeight.Bold),
)

// The Fuji redesign uses a single Japanese gothic family everywhere; the old serif
// name is kept as an alias so screens that still reference it follow the new look.
val Mincho = Gothic

val PortalTypography = Typography(
    displayLarge = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Light, fontSize = 220.sp, letterSpacing = (-6).sp),
    headlineLarge = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Medium, fontSize = 46.sp),
    headlineMedium = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Medium, fontSize = 36.sp),
    titleLarge = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Medium, fontSize = 28.sp),
    titleMedium = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Medium, fontSize = 23.sp),
    bodyLarge = TextStyle(fontFamily = Gothic, fontSize = 22.sp),
    bodyMedium = TextStyle(fontFamily = Gothic, fontSize = 20.sp),
    bodySmall = TextStyle(fontFamily = Gothic, fontSize = 18.sp),
    labelLarge = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = Gothic, fontSize = 18.sp, letterSpacing = 1.sp),
)
