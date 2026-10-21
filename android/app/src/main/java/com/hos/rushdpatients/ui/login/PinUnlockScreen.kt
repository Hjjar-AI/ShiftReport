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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.components.PasswordField

@Composable
fun PinUnlockScreen(
    doctorName: String,
    error: String?,
    busy: Boolean,
    onUnlock: (String) -> Unit,
    onBiometric: (() -> Unit)?,
    onSignOut: () -> Unit
) {
    val uiText = rememberUiStrings()
    var pin by remember { mutableStateOf("") }

    AppCenteredContent() {
        Text(doctorName, style = MaterialTheme.typography.titleLarge)
        Text(uiText(R.string.ui_pin_unlock_screen_001), modifier = Modifier.padding(top = UiSpacing.small, bottom = UiSpacing.section))
        PasswordField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = uiText(R.string.ui_local_pin_login_screen_003),
            keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, enabled = !busy
        )
        error?.let {
            AppNotice(it, kind = NoticeKind.ERROR, modifier = Modifier.padding(top = UiSpacing.tiny))
        }
        LoadingButton(
            text = uiText(R.string.ui_pin_unlock_screen_002),
            loading = busy,
            onClick = { onUnlock(pin) },
            modifier = Modifier.fillMaxWidth().padding(top = UiSpacing.screen)
        )
        onBiometric?.let { bio ->
            AppTextButton(onClick = bio, enabled = !busy) { Text(uiText(R.string.ui_pin_unlock_screen_003)) }
        }
        AppTextButton(onClick = onSignOut, enabled = !busy) { Text(uiText(R.string.ui_settings_preference_sections_019)) }
    }
}
