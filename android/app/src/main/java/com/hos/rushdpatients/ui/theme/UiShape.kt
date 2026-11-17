package com.hos.rushdpatients.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/** Semantic roles map to current shapes, ready for independent future tuning. */
object UiShape {
    val card: Shape @Composable get() = MaterialTheme.shapes.medium
    val button: Shape @Composable get() = MaterialTheme.shapes.small
    val field: Shape @Composable get() = MaterialTheme.shapes.small
    val chip: Shape @Composable get() = MaterialTheme.shapes.small
    val dialog: Shape @Composable get() = MaterialTheme.shapes.extraLarge
    val sheet: Shape @Composable get() = MaterialTheme.shapes.extraLarge
}
