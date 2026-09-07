package com.cyprienbrisset.fukkatsunop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.R

val Mincho = FontFamily(
    Font(R.font.shippori_mincho_regular, FontWeight.Normal),
    Font(R.font.shippori_mincho_medium, FontWeight.Medium),
)
val Gothic = FontFamily(
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_gothic_new_medium, FontWeight.Medium),
)

val PortalTypography = Typography(
    titleLarge = TextStyle(fontFamily = Mincho, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Gothic, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Gothic, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 3.sp),
    labelMedium = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.5.sp),
)
