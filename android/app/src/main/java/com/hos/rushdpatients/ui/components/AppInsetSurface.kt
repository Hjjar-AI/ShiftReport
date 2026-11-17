package com.hos.rushdpatients.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.hos.rushdpatients.ui.theme.UiOpacity

/** Roles keep their exact historical alpha; layout and padding belong to the content. */
enum class InsetLevel(val alpha: Float) {
    SOFT(UiOpacity.containerSoft), MUTED(UiOpacity.containerMuted),
    SUBTLE(UiOpacity.containerSubtle), STRONG(UiOpacity.containerStrong), IDLE(UiOpacity.emblemIdle)
}

@Composable
fun AppInsetSurface(level: InsetLevel, modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium, content: @Composable () -> Unit) {
    Surface(modifier = modifier, shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = level.alpha), content = content)
}
