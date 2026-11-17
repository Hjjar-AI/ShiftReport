package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Placement and visibility stay with the screen; the icon supplies the essential control label. */
@Composable
fun AppFloatingAction(icon: ImageVector, label: String, onClick: () -> Unit,
    colors: ButtonColors, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AppButton(onClick = onClick, enabled = enabled, colors = colors, contentPadding = UiPadding.content,
        modifier = modifier.sizeIn(minWidth = UiSpacing.touchTarget, minHeight = UiSpacing.touchTarget)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(UiSize.iconMedium))
    }
}
