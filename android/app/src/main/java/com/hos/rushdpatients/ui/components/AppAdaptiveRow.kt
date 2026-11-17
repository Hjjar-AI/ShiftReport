package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.uiLayoutScale

/** Equal cells, optional separators and incomplete rows preserve the caller's layout contract. */
@Composable
fun AppAdaptiveRow(
    itemCount: Int, minimumRowWidth: Dp, modifier: Modifier = Modifier,
    maxColumns: Int = itemCount.coerceAtLeast(1), stackWhenNarrow: Boolean = true,
    matchHeights: Boolean = false, rowGap: Dp = UiSpacing.small,
    columnGap: Dp = UiSpacing.small, verticalAlignment: Alignment.Vertical = Alignment.Top,
    separator: (@Composable (Modifier) -> Unit)? = null,
    item: @Composable (Int, Modifier) -> Unit
) {
    val scale = uiLayoutScale()
    val currentItem = rememberUpdatedState(item)
    // Cells survive a row/column switch: keep local field focus and unfinished input mounted.
    val cells = (0 until itemCount).map { index ->
        key(index) { remember { movableContentOf<Modifier> { currentItem.value(index, it) } } }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (stackWhenNarrow && maxWidth < minimumRowWidth * scale) 1 else maxColumns.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(columnGap)) {
            cells.chunked(columns).forEach { row ->
                if (columns == 1) {
                    row.single()(Modifier.fillMaxWidth())
                    if (separator != null && itemCount > 1 && row.single() != cells.last()) separator(Modifier)
                } else {
                    Row(Modifier.fillMaxWidth().then(if (matchHeights) Modifier.height(IntrinsicSize.Min) else Modifier),
                        verticalAlignment = verticalAlignment, horizontalArrangement = Arrangement.spacedBy(rowGap)) {
                        row.forEachIndexed { index, cell ->
                            if (index > 0) separator?.invoke(Modifier.align(Alignment.CenterVertically))
                            Box(Modifier.weight(1f).then(if (matchHeights) Modifier.fillMaxHeight() else Modifier),
                                propagateMinConstraints = true) {
                                cell(Modifier.fillMaxWidth().then(if (matchHeights) Modifier.fillMaxHeight() else Modifier))
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
