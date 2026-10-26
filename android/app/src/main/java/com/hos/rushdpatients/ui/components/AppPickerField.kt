package com.hos.rushdpatients.ui.components


import com.hos.rushdpatients.ui.theme.UiOpacity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens

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
    icon: ImageVector = AppIcons.ArrowDropDown,
    valueTextDirection: TextDirection = TextDirection.ContentOrRtl,
    errorMessage: String? = null
) {
    val uiText = rememberUiStrings()
    val active = enabled && LocalFieldEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.fillMaxWidth().appFieldLayout(uiText, hasLabel = true, isError = isError, errorMessage = errorMessage)
            .clickable(interactionSource = interactionSource, indication = null,
                enabled = active, role = Role.Button, onClickLabel = label, onClick = onClick),
        propagateMinConstraints = true
    ) {
        AppFieldDecoration(
            value = value, enabled = active, isError = isError, interactionSource = interactionSource,
            label = { Text(label) },
            placeholder = if (placeholder == null) null else { { Text(placeholder) } },
            trailingIcon = { Icon(icon, contentDescription = null) },
            supportingText = if (supportingText == null) null else { { Text(supportingText) } },
            innerTextField = {
                Text(value, modifier = Modifier.fillMaxWidth(), softWrap = true,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = valueTextDirection,
                        lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.content(active)))
            }
        )
    }
}
