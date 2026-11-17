package com.hos.rushdpatients.ui.ward

import androidx.compose.ui.geometry.Offset
import androidx.window.layout.FoldingFeature

internal data class FoldSafeRegion(val x: Float, val y: Float, val width: Float, val height: Float)

/** Window pixels in, local pixels out; font scaling must never move a physical hinge. */
internal fun foldSafeRegions(
    fold: FoldingFeature?, origin: Offset?, width: Float, height: Float, clearance: Float
): List<FoldSafeRegion> {
    if (fold == null || origin == null) return emptyList()
    val bounds = fold.bounds
    val left = bounds.left - origin.x
    val right = bounds.right - origin.x
    val top = bounds.top - origin.y
    val bottom = bounds.bottom - origin.y
    return if (fold.orientation == FoldingFeature.Orientation.VERTICAL) {
        if (bottom <= 0f || top >= height || right < 0f || left > width) emptyList()
        else {
            val before = (left - clearance).coerceIn(0f, width)
            val after = (right + clearance).coerceIn(0f, width)
            listOf(FoldSafeRegion(0f, 0f, before, height), FoldSafeRegion(after, 0f, width - after, height))
        }
    } else {
        if (right <= 0f || left >= width || bottom < 0f || top > height) emptyList()
        else {
            val before = (top - clearance).coerceIn(0f, height)
            val after = (bottom + clearance).coerceIn(0f, height)
            listOf(FoldSafeRegion(0f, 0f, width, before), FoldSafeRegion(0f, after, width, height - after))
        }
    }
}
