package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection

@Composable
fun AppPickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    icon: ImageVector = Icons.Filled.ArrowDropDown,
    valueTextDirection: TextDirection = TextDirection.ContentOrRtl
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.fillMaxWidth().appFieldLayout(hasLabel = true, isError = isError)
            .clickable(interactionSource = interactionSource, indication = null,
                enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
    ) {
        AppFieldDecoration(
            value = value, enabled = enabled, isError = isError, interactionSource = interactionSource,
            label = { Text(label) },
            placeholder = if (placeholder == null) null else { { Text(placeholder) } },
            trailingIcon = { Icon(icon, contentDescription = null) },
            supportingText = if (supportingText == null) null else { { Text(supportingText) } },
            innerTextField = {
                Text(value, style = MaterialTheme.typography.bodyLarge.copy(textDirection = valueTextDirection),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
            }
        )
    }
}
