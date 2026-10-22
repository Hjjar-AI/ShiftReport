package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppSearchField(query: String, onQueryChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val uiText = rememberUiStrings()
    val active = enabled && LocalFieldEnabled.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    AppTextField(value = query, onValueChange = onQueryChange, label = { Text(label) },
        singleLine = true, enabled = active, modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
        trailingIcon = { if (query.isNotEmpty()) AppIconButton(label = uiText(R.string.ui_shift_doctor_picker_007),
            icon = AppIcons.Close,
            onClick = { onQueryChange("") },
            enabled = active) })
}
