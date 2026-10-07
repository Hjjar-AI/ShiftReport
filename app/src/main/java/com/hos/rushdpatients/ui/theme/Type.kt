package com.hos.rushdpatients.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun textStyle(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp
)

val Typography = Typography(
    displayLarge = textStyle(40, 52, FontWeight.Bold),
    displayMedium = textStyle(34, 44, FontWeight.Bold),
    displaySmall = textStyle(30, 40, FontWeight.Bold),
    headlineLarge = textStyle(28, 38, FontWeight.Bold),
    headlineMedium = textStyle(26, 36, FontWeight.Bold),
    headlineSmall = textStyle(24, 32, FontWeight.Bold),
    titleLarge = textStyle(22, 30, FontWeight.SemiBold),
    titleMedium = textStyle(16, 24, FontWeight.SemiBold),
    titleSmall = textStyle(14, 22, FontWeight.SemiBold),
    bodyLarge = textStyle(16, 26),
    bodyMedium = textStyle(15, 24),
    bodySmall = textStyle(14, 22),
    labelLarge = textStyle(14, 22, FontWeight.SemiBold),
    labelMedium = textStyle(13, 20, FontWeight.Medium),
    labelSmall = textStyle(12, 18, FontWeight.Medium)
)
