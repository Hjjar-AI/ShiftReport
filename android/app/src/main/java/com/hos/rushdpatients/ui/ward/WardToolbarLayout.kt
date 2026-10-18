package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature

/** Measure natural toolbar content; keep it together on one safe side of an intersecting hinge. */
@Composable
internal fun WardToolbarLayout(foldingFeature: FoldingFeature?, content: @Composable () -> Unit) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    Layout(content = content, modifier = Modifier.fillMaxWidth().clipToBounds().onGloballyPositioned { origin = it.positionInWindow() }) { children, constraints ->
        val location = origin
        val fold = foldingFeature
        val width = constraints.maxWidth
        var left = 0
        var safeWidth = width
        if (fold != null && location != null && fold.orientation == FoldingFeature.Orientation.VERTICAL &&
            fold.bounds.bottom > location.y && fold.bounds.top < location.y + constraints.maxHeight) {
            val start = (fold.bounds.left - location.x).toInt().coerceIn(0, width)
            val end = (fold.bounds.right - location.x).toInt().coerceIn(0, width)
            if (end > 0 && start < width) {
                val before = (start - gap).coerceAtLeast(0)
                val after = (width - end - gap).coerceAtLeast(0)
                if (after > before) { left = (end + gap).coerceAtMost(width); safeWidth = after }
                else safeWidth = before
            }
        }
        val placeables = children.map { it.measure(constraints.copy(minWidth = safeWidth, maxWidth = safeWidth, minHeight = 0)) }
        val naturalHeight = placeables.maxOfOrNull { it.height } ?: 0
        var top = 0
        if (fold != null && location != null && fold.orientation == FoldingFeature.Orientation.HORIZONTAL) {
            val start = (fold.bounds.top - location.y).toInt()
            val end = (fold.bounds.bottom - location.y).toInt()
            if (end >= 0 && start < naturalHeight && naturalHeight > start - gap) {
                top = (end + gap).coerceIn(0, (constraints.maxHeight - naturalHeight).coerceAtLeast(0))
            }
        }
        layout(width, constraints.constrainHeight(naturalHeight + top)) {
            if (fold == null || location != null) placeables.forEach { it.place(left, top) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WardToolbarContent(title: @Composable () -> Unit, navigation: @Composable () -> Unit,
    actions: @Composable () -> Unit) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = UiSpacing.small)) {
        if (maxWidth < 240.dp * scale.coerceAtMost(1.3f)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    navigation()
                    Column(Modifier.weight(1f)) { title() }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
            }
        } else Row(Modifier.heightIn(min = UiSpacing.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            navigation()
            Column(Modifier.weight(1f)) { title() }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
