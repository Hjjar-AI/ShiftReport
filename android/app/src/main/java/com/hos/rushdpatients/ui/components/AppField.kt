package com.hos.rushdpatients.ui.components


import com.hos.rushdpatients.R

import com.hos.rushdpatients.ui.theme.UiOpacity
import androidx.compose.ui.graphics.lerp

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize

/** Form-local saving state; credentials/drafts never enter this context. */
val LocalFieldEnabled = staticCompositionLocalOf { true }

/** Native editing and Material decoration with the app's compact internal insets. */
@OptIn(ExperimentalFoundationApi::class)
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
    minLines: Int = 1,
    errorMessage: String? = null
) {
    val uiText = rememberUiStrings()
    val active = enabled && LocalFieldEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val fieldVisibility = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    LaunchedEffect(focused, density, ime) {
        if (focused) snapshotFlow { ime.getBottom(density) }.collectLatest {
            delay(100L)
            withFrameNanos { }
            fieldVisibility.bringIntoView()
        }
    }
    val textColor = if (textStyle.color != Color.Unspecified) textStyle.color
        else MaterialTheme.colorScheme.onSurface.copy(alpha = UiOpacity.content(active))
    BasicTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.bringIntoViewRequester(fieldVisibility)
            .onFocusChanged { focused = it.isFocused }.appFieldLayout(uiText, label != null, isError, errorMessage),
        enabled = active, textStyle = textStyle.copy(color = textColor),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        visualTransformation = visualTransformation, interactionSource = interactionSource,
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            AppFieldDecoration(
                value = value, enabled = active, isError = isError,
                interactionSource = interactionSource, label = label, placeholder = placeholder,
                trailingIcon = trailingIcon, supportingText = supportingText,
                singleLine = singleLine, visualTransformation = visualTransformation,
                innerTextField = innerTextField
            )
        }
    )
}

internal fun Modifier.appFieldLayout(uiText: UiStrings, hasLabel: Boolean, isError: Boolean, errorMessage: String? = null): Modifier =
    semantics(mergeDescendants = true) {
        if (isError) error(errorMessage?.takeIf { it.isNotBlank() } ?: uiText(R.string.ui_app_field_001))
    }.padding(top = if (hasLabel) UiSize.fieldLabelInset else com.hos.rushdpatients.ui.theme.UiSpace.none)
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
    val surface = MaterialTheme.colorScheme.surface
    val variant = MaterialTheme.colorScheme.surfaceVariant
    val fill = remember(surface, variant) { lerp(surface, variant, .65f) }
    val shape = MaterialTheme.shapes.small
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent
    )
    // Material clips the whole outlined container at the floating label, including its fill.
    // Paint the measured body behind that cutout so only the outline has a label gap.
    Box(Modifier.drawBehind {
        if (containerSize.width > 0 && containerSize.height > 0) {
            drawOutline(
                outline = shape.createOutline(
                    Size(containerSize.width.toFloat(), containerSize.height.toFloat()), layoutDirection, this
                ),
                color = fill
            )
        }
    }, propagateMinConstraints = true) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = value, innerTextField = innerTextField,
            enabled = enabled && LocalFieldEnabled.current, singleLine = singleLine,
            visualTransformation = visualTransformation, interactionSource = interactionSource,
            isError = isError, label = label, placeholder = placeholder,
            trailingIcon = trailingIcon, supportingText = supportingText,
            colors = colors, contentPadding = UiPadding.field,
            container = {
                Box(Modifier.onSizeChanged { containerSize = it }, propagateMinConstraints = true) {
                    OutlinedTextFieldDefaults.ContainerBox(
                        enabled = enabled && LocalFieldEnabled.current, isError = isError, interactionSource = interactionSource,
                        colors = colors, shape = shape
                    )
                }
            }
        )
    }
}
