package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    val fontScale = com.hos.rushdpatients.ui.theme.uiLayoutScale()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (stackWhenNarrow && maxWidth < minimumRowWidth * fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                first(Modifier.fillMaxWidth())
                separator?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                second(Modifier.fillMaxWidth())
            }
        } else {
            val rowHeight = if (matchHeights) Modifier.height(IntrinsicSize.Min) else Modifier
            Row(Modifier.fillMaxWidth().then(rowHeight), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                val cellHeight = if (matchHeights) Modifier.fillMaxHeight() else Modifier
                Box(Modifier.weight(1f).then(cellHeight), propagateMinConstraints = true) {
                    first(Modifier.fillMaxWidth())
                }
                separator?.let { Text(it, Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium) }
                Box(Modifier.weight(1f).then(cellHeight), propagateMinConstraints = true) {
                    second(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
