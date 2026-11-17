package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Two related controls keep their own state and callbacks when their arrangement changes. */
@Composable
fun AppFieldPair(modifier: Modifier = Modifier, separator: String? = null, stackWhenNarrow: Boolean = true,
    minimumRowWidth: Dp = UiLayout.fieldPairMinWidth, matchHeights: Boolean = false,
    first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    AppAdaptiveRow(itemCount = 2, minimumRowWidth = minimumRowWidth, modifier = modifier,
        stackWhenNarrow = stackWhenNarrow, matchHeights = matchHeights,
        separator = separator?.let { label -> { cellModifier ->
            AppText(label, modifier = cellModifier, style = MaterialTheme.typography.labelMedium)
        } }) { index, cellModifier ->
        if (index == 0) first(cellModifier) else second(cellModifier)
    }
}
