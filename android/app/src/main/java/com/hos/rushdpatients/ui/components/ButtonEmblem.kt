package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiOpacity
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.hos.rushdpatients.ui.theme.UiSize

/** Decorative corner emblem; physical bottom-left stays fixed in Arabic layouts. */
@Composable
internal fun BoxScope.ButtonEmblem(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint,
        modifier = modifier.align(AbsoluteAlignment.BottomLeft).size(UiSize.icon))
}

/** The label/progress stays centered while the decorative emblem occupies its own corner. */
@Composable
internal fun ButtonEmblemContent(icon: ImageVector, enabled: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = UiSize.iconXl), contentAlignment = Alignment.Center) {
        val color = LocalContentColor.current
        ButtonEmblem(icon, tint = color.copy(alpha = color.alpha * if (enabled) UiOpacity.emblem else UiOpacity.emblemFaint))
        content()
    }
}
