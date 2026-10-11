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
    displayLarge = textStyle(40, 48, FontWeight.Bold),
    displayMedium = textStyle(34, 42, FontWeight.Bold),
    displaySmall = textStyle(30, 38, FontWeight.Bold),
    headlineLarge = textStyle(28, 34, FontWeight.Bold),
    headlineMedium = textStyle(26, 32, FontWeight.Bold),
    headlineSmall = textStyle(24, 30, FontWeight.Bold),
    titleLarge = textStyle(22, 28, FontWeight.SemiBold),
    titleMedium = textStyle(16, 22, FontWeight.SemiBold),
    titleSmall = textStyle(14, 18, FontWeight.SemiBold),
    bodyLarge = textStyle(16, 22),
    bodyMedium = textStyle(15, 20),
    bodySmall = textStyle(14, 18),
    labelLarge = textStyle(14, 18, FontWeight.SemiBold),
    labelMedium = textStyle(13, 17, FontWeight.Medium),
    labelSmall = textStyle(12, 16, FontWeight.Medium)
)
