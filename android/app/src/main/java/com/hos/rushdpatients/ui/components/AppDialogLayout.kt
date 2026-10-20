package com.hos.rushdpatients.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.theme.UiDialog

/**
 * Bound a scrollable dialog body using its actual available viewport, including
 * resized keyboard/fold layouts. The caller retains scrolling and fixed actions.
 * No screen-height estimate or second subtraction of keyboard insets is needed.
 */
fun Modifier.appDialogBody(maximumHeight: Dp = UiDialog.bodyMaxHeight): Modifier = layout { measurable, constraints ->
    val cap = maximumHeight.roundToPx().coerceAtLeast(0)
    val available = if (constraints.hasBoundedHeight)
        (constraints.maxHeight * UiDialog.availableHeightFraction).toInt().coerceAtLeast(0) else cap
    // Respect an explicit minimum supplied by the caller while capping flexible bodies.
    val height = minOf(cap, available).coerceAtLeast(constraints.minHeight)
    val child = measurable.measure(constraints.copy(maxHeight = height))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
}
