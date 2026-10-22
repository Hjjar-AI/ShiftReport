package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Direction is resolved once for back/forward/send symbols, independently of their labels. */
@Composable
fun Modifier.directionalIcon(): Modifier = scale(
    scaleX = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f
)

/** The same Arabic label names the action for TalkBack and its long-press tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconButton(
    label: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    iconModifier: Modifier = Modifier, tint: Color = LocalContentColor.current,
    directional: Boolean = false,
    size: com.hos.rushdpatients.ui.theme.UiControlSize = com.hos.rushdpatients.ui.theme.LocalUiPreferences.current.controlSize
) {
    PlainTooltipBox(tooltip = { Text(label) }) {
        val sizedIcon = iconModifier.size(size.iconSize)
        IconButton(onClick = onClick, enabled = enabled,
            modifier = modifier.sizeIn(minWidth = UiSpacing.touchTarget, minHeight = UiSpacing.touchTarget).tooltipAnchor()) {
            Icon(icon, contentDescription = label, tint = tint,
                modifier = if (directional) sizedIcon.directionalIcon() else sizedIcon)
        }
    }
}
