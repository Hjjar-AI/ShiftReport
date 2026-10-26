package com.hos.rushdpatients.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import com.hos.rushdpatients.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The same bundled faces serve Compose and PDF exports; UI loading runs asynchronously. */
private val ArabicFontFamily = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.tajawal_medium, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.tajawal_bold, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

private fun textStyle(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = ArabicFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp
)

/** Emphasis uses a real bundled face; Tajawal has no published 600/SemiBold face. */
object UiTypography { val emphasizedWeight = FontWeight.Bold }

val Typography = Typography(
    displayLarge = textStyle(40, 48, FontWeight.Bold),
    displayMedium = textStyle(34, 42, FontWeight.Bold),
    displaySmall = textStyle(30, 38, FontWeight.Bold),
    headlineLarge = textStyle(28, 34, FontWeight.Bold),
    headlineMedium = textStyle(26, 32, FontWeight.Bold),
    headlineSmall = textStyle(24, 30, FontWeight.Bold),
    titleLarge = textStyle(22, 28, UiTypography.emphasizedWeight),
    titleMedium = textStyle(16, 25, UiTypography.emphasizedWeight),
    titleSmall = textStyle(14, 22, UiTypography.emphasizedWeight),
    bodyLarge = textStyle(16, 25),
    bodyMedium = textStyle(15, 24),
    bodySmall = textStyle(14, 22),
    labelLarge = textStyle(14, 22, UiTypography.emphasizedWeight),
    labelMedium = textStyle(13, 20, FontWeight.Medium),
    labelSmall = textStyle(13, 20, FontWeight.Medium)
)
