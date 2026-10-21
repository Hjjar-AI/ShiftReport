package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

/** A whole-row destination; supporting text can wrap at large font sizes. */
@Composable
fun AppDestinationRow(title: String, summary: String, icon: ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()
        .heightIn(min = UiSpacing.touchTarget), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(UiSpacing.section), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(UiSize.icon))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (LocalLayoutDirection.current == LayoutDirection.Rtl) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
