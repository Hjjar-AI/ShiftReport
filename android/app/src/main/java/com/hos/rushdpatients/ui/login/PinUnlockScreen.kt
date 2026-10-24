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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    var pin by remember(doctorName) { mutableStateOf("") }
    var automaticBiometricAttempted by rememberSaveable(doctorName) { mutableStateOf(false) }
    val latestBiometric by rememberUpdatedState(onBiometric)
    val lifecycleOwner = LocalLifecycleOwner.current
    // Prompt once per lock screen, only after the root has decided this is an
    // actual lock and the Activity is resumed. Cancel/error leaves PIN available.
    DisposableEffect(lifecycleOwner, onBiometric != null) {
        fun promptIfReady() {
            if (!automaticBiometricAttempted && latestBiometric != null &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                automaticBiometricAttempted = true
                latestBiometric?.invoke()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) promptIfReady()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        promptIfReady()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun submit() {
        if (!busy && pin.length >= com.hos.rushdpatients.config.AppConstants.PIN_MIN_LENGTH) {
            val submitted = pin
            pin = ""
            onUnlock(submitted)
        }
    }

    AppCenteredContent() {
        Text(doctorName, style = MaterialTheme.typography.titleLarge)
        Text(uiText(R.string.ui_pin_unlock_screen_001), modifier = Modifier.padding(top = UiSpacing.small, bottom = UiSpacing.section))
        PasswordField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(com.hos.rushdpatients.config.AppConstants.PIN_MAX_LENGTH) },
            label = uiText(R.string.ui_local_pin_login_screen_003),
            keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, enabled = !busy,
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
        error?.let {
            AppNotice(it, kind = NoticeKind.ERROR, modifier = Modifier.padding(top = UiSpacing.tiny))
        }
        LoadingButton(
            text = uiText(R.string.ui_pin_unlock_screen_002),
            loading = busy,
            enabled = pin.length >= com.hos.rushdpatients.config.AppConstants.PIN_MIN_LENGTH,
            onClick = ::submit,
            modifier = Modifier.fillMaxWidth().padding(top = UiSpacing.screen)
        )
        onBiometric?.let { bio ->
            AppTextButton(onClick = bio, enabled = !busy) { Text(uiText(R.string.ui_pin_unlock_screen_003)) }
        }
        AppTextButton(onClick = onSignOut, enabled = !busy) { Text(uiText(R.string.ui_settings_preference_sections_019)) }
    }
}
