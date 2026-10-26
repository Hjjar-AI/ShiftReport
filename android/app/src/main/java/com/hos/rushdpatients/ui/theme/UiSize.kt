package com.hos.rushdpatients.ui.theme

import androidx.compose.ui.unit.dp

/** Control dimensions are minimums so Arabic labels and larger fonts can grow. */
object UiSize {
    val pdfAppearancePreview = 72.dp
    val splashEmblem = 240.dp
    val aboutEmblemCompact = 128.dp
    val aboutEmblem = 180.dp
    val statusEdge = 4.dp
    val patientOrdinal = 32.dp
    val fieldMinHeight = UiSpacing.touchTarget
    // Keep floating labels clear of the outline even with compact layout gaps.
    val fieldLabelInset = 8.dp
    val iconXs = 12.dp
    val iconSmall = 16.dp
    val iconMedium = 20.dp
    val icon = 24.dp
    val iconXl = 32.dp
    val iconHero = 40.dp
    val emptyStateIcon = iconHero
    val loadingIndicator = 40.dp
    val progressStroke = 2.dp
}
