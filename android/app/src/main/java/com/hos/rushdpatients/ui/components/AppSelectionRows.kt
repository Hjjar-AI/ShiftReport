package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.ui.theme.UiOpacity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Exactly one interaction owner for the label and its visual control. */
@Composable
fun AppChoiceRow(label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val active = enabled && LocalFieldEnabled.current
    Row(modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
        .selectable(selected = selected, enabled = active, role = Role.RadioButton, onClick = onClick)
        .padding(UiPadding.content), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        RadioButton(selected = selected, onClick = null, enabled = active)
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.content(active)))
    }
}

@Composable
fun AppCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val active = enabled && LocalFieldEnabled.current
    Row(modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
        .toggleable(value = checked, enabled = active, role = Role.Checkbox, onValueChange = onCheckedChange)
        .padding(UiPadding.content), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = active)
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.content(active)))
    }
}

@Composable
fun AppToggleRow(title: String, checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true) {
    val active = enabled && LocalFieldEnabled.current && onCheckedChange != null
    Row(modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
        .toggleable(value = checked, enabled = active, role = Role.Switch,
            onValueChange = { onCheckedChange?.invoke(it) })
        .padding(UiPadding.content), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.content(active)))
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UiOpacity.content(active))) }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = active)
    }
}
