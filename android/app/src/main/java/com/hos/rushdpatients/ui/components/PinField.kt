package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.hos.rushdpatients.config.AppConstants

/** PIN presentation and input limits only; the caller owns the memory-only value. */
@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalFieldEnabled.current,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    PasswordField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(AppConstants.PIN_MAX_LENGTH)) },
        label = label,
        modifier = modifier,
        enabled = enabled,
        keyboardType = KeyboardType.NumberPassword,
        imeAction = imeAction,
        keyboardActions = keyboardActions
    )
}
