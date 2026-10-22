package com.hos.rushdpatients.ui.login

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppCenteredContent
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.components.PasswordField
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
fun LocalPinLoginScreen(
    doctorId: String,
    doctorName: String,
    busy: Boolean,
    error: String?,
    onLogin: (String) -> Unit,
    onTelegram: () -> Unit,
    onBack: () -> Unit
) {
    val uiText = rememberUiStrings()
    // A submitted PIN never enters saved UI state or the ViewModel state.
    var pin by remember(doctorId) { mutableStateOf("") }
    fun submit() {
        if (!busy && pin.length >= AppConstants.PIN_MIN_LENGTH) {
            val submitted = pin
            pin = ""
            onLogin(submitted)
        }
    }
    AppCenteredContent(
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small, Alignment.CenterVertically)
    ) {
        Text(doctorName, style = MaterialTheme.typography.titleLarge)
        Text(uiText(R.string.ui_local_pin_login_screen_001), style = MaterialTheme.typography.titleMedium)
        Text(
            uiText(R.string.ui_local_pin_login_screen_002),
            style = MaterialTheme.typography.bodySmall
        )
        PasswordField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(AppConstants.PIN_MAX_LENGTH) },
            label = uiText(R.string.ui_local_pin_login_screen_003),
            keyboardType = KeyboardType.NumberPassword,
            keyboardActions = KeyboardActions(onDone = { submit() }),
            enabled = !busy
        )
        error?.let { AppNotice(it, kind = NoticeKind.ERROR) }
        LoadingButton(
            text = uiText(R.string.ui_local_pin_login_screen_004),
            loading = busy,
            enabled = pin.length >= AppConstants.PIN_MIN_LENGTH,
            onClick = ::submit,
            modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
        )
        AppTextButton(
            enabled = !busy,
            onClick = { pin = ""; onTelegram() },
            modifier = Modifier.heightIn(min = UiSpacing.touchTarget)
        ) { Text(uiText(R.string.ui_local_pin_login_screen_005)) }
        AppTextButton(
            enabled = !busy,
            onClick = { pin = ""; onBack() },
            modifier = Modifier.heightIn(min = UiSpacing.touchTarget)
        ) { Text(uiText(R.string.ui_local_pin_login_screen_006)) }
    }
}
