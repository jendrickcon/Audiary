package com.example.audiary.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.audiary.R

val FrauncesFontFamily = FontFamily(
    Font(R.font.fraunces, FontWeight.Normal),
    Font(R.font.fraunces, FontWeight.Medium),
    Font(R.font.fraunces, FontWeight.SemiBold),
    Font(R.font.fraunces, FontWeight.Bold)
)

val DmSansFontFamily = FontFamily(
    Font(R.font.dmsans, FontWeight.Normal),
    Font(R.font.dmsans, FontWeight.Medium),
    Font(R.font.dmsans, FontWeight.SemiBold),
    Font(R.font.dmsans, FontWeight.Bold)
)

val AudiaryTypography = Typography(
    displaySmall = TextStyle(fontFamily = FrauncesFontFamily, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FrauncesFontFamily, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FrauncesFontFamily, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = FrauncesFontFamily, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = DmSansFontFamily, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = DmSansFontFamily, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = DmSansFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = DmSansFontFamily, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = DmSansFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = DmSansFontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = DmSansFontFamily, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = DmSansFontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = DmSansFontFamily, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
)
