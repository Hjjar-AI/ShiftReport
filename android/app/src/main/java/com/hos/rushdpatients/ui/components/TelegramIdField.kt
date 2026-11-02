package com.hos.rushdpatients.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** Signed group IDs need a keyboard that exposes minus; validation stays with the caller. */
@Composable
fun TelegramIdField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    enabled: Boolean = LocalFieldEnabled.current,
    hint: String? = null
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = hint?.let { text -> { Text(text) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (signed) KeyboardType.Text else KeyboardType.Number
        ),
        modifier = modifier.fillMaxWidth()
    )
}
