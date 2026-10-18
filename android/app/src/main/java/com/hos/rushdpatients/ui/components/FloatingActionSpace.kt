package com.hos.rushdpatients.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.theme.UiSpacing

data class FloatingActionSpace(val bottomPadding: Dp, val measure: Modifier)

/** Clearance follows the rendered action rather than a font-dependent fixed spacer. */
@Composable
fun rememberFloatingActionSpace(visible: Boolean): FloatingActionSpace {
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    return FloatingActionSpace(
        if (visible) with(density) { height.toDp() } + UiSpacing.section * 2 else UiSpacing.screen,
        Modifier.onSizeChanged { height = it.height })
}
