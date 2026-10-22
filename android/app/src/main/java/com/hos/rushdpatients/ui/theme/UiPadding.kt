package com.hos.rushdpatients.ui.theme

import androidx.compose.foundation.layout.PaddingValues

/** Small internal insets; screen gutters and interaction minimums are separate tokens. */
object UiPadding {
    val screen = PaddingValues(horizontal = UiSpacing.screenGutter, vertical = UiSpacing.small)
    val card = PaddingValues(horizontal = UiSpacing.cardPadding, vertical = UiSpacing.small)
    val listItem = PaddingValues(horizontal = UiSpacing.screenGutter, vertical = UiSpacing.tiny)
    val button = PaddingValues(horizontal = UiSpacing.section, vertical = UiSpacing.small)
    val content = PaddingValues(horizontal = UiSpacing.small, vertical = UiSpacing.tiny)
    // Floating labels need slightly more room above the first line.
    val field = PaddingValues(start = UiSpacing.small, top = UiSize.fieldLabelInset, end = UiSpacing.small, bottom = UiSpacing.tiny)
    val compact = PaddingValues(horizontal = UiSpacing.tiny, vertical = UiSpacing.micro)
}
