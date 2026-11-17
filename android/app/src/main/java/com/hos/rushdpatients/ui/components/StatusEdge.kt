package com.hos.rushdpatients.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.hos.rushdpatients.ui.theme.UiSize

/** Draw at logical start; callers retain their own content insets. */
fun Modifier.statusEdge(color: Color?, width: Dp = UiSize.statusEdge): Modifier = drawBehind {
    if (color != null) {
        val edge = width.toPx()
        drawRect(color, Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - edge else 0f, 0f),
            Size(edge, size.height))
    }
}
