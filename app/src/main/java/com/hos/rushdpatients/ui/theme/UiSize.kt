package com.hos.rushdpatients.ui.theme

import androidx.compose.ui.unit.dp

/** Control dimensions are minimums so Arabic labels and larger fonts can grow. */
object UiSize {
    val fieldMinHeight = UiSpacing.touchTarget
    // Keep floating labels clear of the outline even with compact layout gaps.
    val fieldLabelInset = 8.dp
    val iconSmall = 16.dp
    val iconMedium = 20.dp
    val icon = 24.dp
    val progressStroke = 2.dp
}
