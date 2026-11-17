package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiLayout
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.window.layout.FoldingFeature

/** Measure natural toolbar content; keep it together on one safe side of an intersecting hinge. */
@Composable
internal fun WardToolbarLayout(foldingFeature: FoldingFeature?, content: @Composable () -> Unit) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    val gap = with(LocalDensity.current) { UiLayout.foldClearance.roundToPx() }
    Layout(content = content, modifier = Modifier.fillMaxWidth().clipToBounds().onGloballyPositioned { origin = it.positionInWindow() }) { children, constraints ->
        val location = origin
        val fold = foldingFeature
        val width = constraints.maxWidth
        var left = 0
        var safeWidth = width
        val regions = foldSafeRegions(fold, location, width.toFloat(), constraints.maxHeight.toFloat(), gap.toFloat())
        if (fold?.orientation == FoldingFeature.Orientation.VERTICAL && regions.isNotEmpty()) {
            val region = regions.maxBy { it.width }
            left = region.x.toInt()
            safeWidth = region.width.toInt()
        }
        val placeables = children.map { it.measure(constraints.copy(minWidth = safeWidth, maxWidth = safeWidth, minHeight = 0)) }
        val naturalHeight = placeables.maxOfOrNull { it.height } ?: 0
        var top = 0
        if (fold?.orientation == FoldingFeature.Orientation.HORIZONTAL && regions.size == 2 &&
            naturalHeight > regions[0].height) {
            top = regions[1].y.toInt().coerceIn(0, (constraints.maxHeight - naturalHeight).coerceAtLeast(0))
        }
        layout(width, constraints.constrainHeight(naturalHeight + top)) {
            if (fold == null || location != null) placeables.forEach { it.place(left, top) }
        }
    }
}
