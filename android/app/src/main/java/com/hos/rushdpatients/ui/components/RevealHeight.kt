package com.hos.rushdpatients.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlin.math.roundToInt

/** Clip the existing card rather than scaling its text or recreating its local state. */
fun Modifier.revealHeight(fraction: Float, collapsed: Boolean): Modifier =
    clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, (placeable.height * fraction).roundToInt()) {
                placeable.placeRelative(0, 0)
            }
        }
        .then(if (collapsed) Modifier.clearAndSetSemantics {}.onPreviewKeyEvent { true }.pointerInput(Unit) {
            // A closing card must not accept a last-moment clinical action.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        } else Modifier)
