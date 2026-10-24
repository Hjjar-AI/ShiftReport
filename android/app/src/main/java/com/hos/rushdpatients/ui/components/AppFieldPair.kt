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
    minimumRowWidth: Dp = UiLayout.fieldPairMinWidth,
    first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val fontScale = com.hos.rushdpatients.ui.theme.uiLayoutScale()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (stackWhenNarrow && maxWidth < minimumRowWidth * fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                first(Modifier.fillMaxWidth())
                separator?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                second(Modifier.fillMaxWidth())
            }
        } else Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            first(Modifier.weight(1f))
            separator?.let { Text(it, Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium) }
            second(Modifier.weight(1f))
        }
    }
}
