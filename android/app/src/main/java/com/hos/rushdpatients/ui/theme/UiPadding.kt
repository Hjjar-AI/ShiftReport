package com.hos.rushdpatients.ui.theme

import androidx.compose.foundation.layout.PaddingValues

/** Small internal insets; screen gutters and interaction minimums are separate tokens. */
object UiPadding {
    val content = PaddingValues(horizontal = UiSpacing.small, vertical = UiSpacing.tiny)
    // Floating labels need slightly more room above the first line.
    val field = PaddingValues(start = UiSpacing.small, top = UiSize.fieldLabelInset, end = UiSpacing.small, bottom = UiSpacing.tiny)
    val compact = PaddingValues(horizontal = UiSpacing.tiny, vertical = UiSpacing.micro)
}
