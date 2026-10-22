package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import com.hos.rushdpatients.ui.theme.UiBorder
import com.hos.rushdpatients.ui.theme.UiOpacity
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Selection has both a visible stroke and semantics; navigation/choice roles stay caller-owned. */
@Composable
fun AppSelectableSurface(
    selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, role: Role? = null,
    shape: Shape = MaterialTheme.shapes.small, bordered: Boolean = false,
    unselectedColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Surface(modifier = modifier.heightIn(min = UiSpacing.touchTarget)
        .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else unselectedColor,
        contentColor = (if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            .copy(alpha = UiOpacity.content(enabled)),
        border = if (selected || bordered) UiBorder.stroke(selected = selected) else null,
        content = content)
}
