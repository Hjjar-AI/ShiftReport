package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

/** Hides only on downward list movement; upward movement and either boundary reveal navigation. */
@Composable
internal fun rememberWardNavigationVisible(grid: LazyGridState, enabled: Boolean, context: Any?): Boolean {
    val accessibility = androidx.compose.ui.platform.LocalContext.current
        .getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(accessibility) {
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    var visible by remember(grid, context) { mutableStateOf(true) }
    LaunchedEffect(grid, enabled, context, touchExploration) {
        visible = true
        if (!enabled || touchExploration) return@LaunchedEffect
        var previousIndex = grid.firstVisibleItemIndex
        var previousOffset = grid.firstVisibleItemScrollOffset
        snapshotFlow {
            ScrollPosition(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset,
                grid.isScrollInProgress, grid.canScrollBackward, grid.canScrollForward)
        }.distinctUntilChanged().collect { position ->
            if (!position.backward || !position.forward) visible = true
            else if (position.scrolling) {
                if (position.index < previousIndex || position.index == previousIndex && position.offset < previousOffset) visible = true
                else if (position.index > previousIndex || position.offset > previousOffset) visible = false
            }
            previousIndex = position.index
            previousOffset = position.offset
        }
    }
    return !enabled || touchExploration || visible
}

private data class ScrollPosition(val index: Int, val offset: Int, val scrolling: Boolean,
    val backward: Boolean, val forward: Boolean)
