package com.hos.rushdpatients.ui.login

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppCenteredContent
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.components.PasswordField

@Composable
fun SetPinScreen(
    doctor: Doctor,
    error: String?,
    busy: Boolean,
    onConfirm: (pin: String, confirm: String) -> Unit,
    onCancel: () -> Unit
) {
    val uiText = rememberUiStrings()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AppCenteredContent() {
        Text(doctor.fullName, style = MaterialTheme.typography.titleLarge)
        Text(
            uiText(R.string.ui_set_pin_screen_001),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = UiSpacing.small)
        )
        Text(
            uiText(R.string.ui_set_pin_screen_002),
            modifier = Modifier.padding(top = UiSpacing.small, bottom = UiSpacing.section),
            textAlign = TextAlign.Center
        )
        PasswordField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = uiText(R.string.ui_set_pin_screen_003),
            keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next, enabled = !busy
        )
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it.filter(Char::isDigit).take(8) },
            label = uiText(R.string.ui_set_pin_screen_004),
            keyboardType = KeyboardType.NumberPassword, enabled = !busy,
            keyboardActions = KeyboardActions(onDone = { if (!busy) onConfirm(pin, confirm) }),
            modifier = Modifier.padding(top = UiSpacing.medium)
        )
        error?.let {
            AppNotice(it, kind = NoticeKind.ERROR, modifier = Modifier.padding(top = UiSpacing.tiny))
        }
        LoadingButton(
            text = uiText(R.string.ui_save),
            loading = busy,
            onClick = { onConfirm(pin, confirm) },
            modifier = Modifier.fillMaxWidth().padding(top = UiSpacing.screen)
        )
        AppTextButton(onClick = onCancel, enabled = !busy) { Text(uiText(R.string.ui_cancel)) }
    }
}
