package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Form-local saving state; credentials/drafts never enter this context. */
val LocalFieldEnabled = staticCompositionLocalOf { true }

/** Native editing and Material decoration with the app's compact internal insets. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textColor = if (textStyle.color != Color.Unspecified) textStyle.color
        else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    BasicTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.appFieldLayout(label != null, isError),
        enabled = enabled, textStyle = textStyle.copy(color = textColor),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        visualTransformation = visualTransformation, interactionSource = interactionSource,
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            AppFieldDecoration(
                value = value, enabled = enabled, isError = isError,
                interactionSource = interactionSource, label = label, placeholder = placeholder,
                trailingIcon = trailingIcon, supportingText = supportingText,
                singleLine = singleLine, visualTransformation = visualTransformation,
                innerTextField = innerTextField
            )
        }
    )
}

internal fun Modifier.appFieldLayout(hasLabel: Boolean, isError: Boolean): Modifier =
    semantics(mergeDescendants = true) {
        if (isError) error("إدخال غير صالح")
    }.padding(top = if (hasLabel) UiSpacing.small else 0.dp)
        .defaultMinSize(minHeight = UiSize.fieldMinHeight)

/** Pickers and editors share label, outline, helper/error placement and icon space. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppFieldDecoration(
    value: String,
    enabled: Boolean,
    isError: Boolean,
    interactionSource: InteractionSource,
    label: (@Composable () -> Unit)?,
    placeholder: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    supportingText: (@Composable () -> Unit)?,
    singleLine: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    innerTextField: @Composable () -> Unit
) {
    val colors = OutlinedTextFieldDefaults.colors()
    OutlinedTextFieldDefaults.DecorationBox(
        value = value, innerTextField = innerTextField,
        enabled = enabled, singleLine = singleLine,
        visualTransformation = visualTransformation, interactionSource = interactionSource,
        isError = isError, label = label, placeholder = placeholder,
        trailingIcon = trailingIcon, supportingText = supportingText,
        colors = colors, contentPadding = UiPadding.field,
        container = {
            OutlinedTextFieldDefaults.ContainerBox(
                enabled = enabled, isError = isError, interactionSource = interactionSource,
                colors = colors, shape = MaterialTheme.shapes.small
            )
        }
    )
}
