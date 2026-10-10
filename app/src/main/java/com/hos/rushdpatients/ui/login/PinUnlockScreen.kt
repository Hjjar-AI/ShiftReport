package com.hos.rushdpatients.ui.login

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
    var pin by remember { mutableStateOf("") }

    AppCenteredContent() {
        Text(doctorName, style = MaterialTheme.typography.titleLarge)
        Text("أدخل رقمك السري", modifier = Modifier.padding(top = UiSpacing.small, bottom = UiSpacing.section))
        PasswordField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = "الرقم السري",
            keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, enabled = !busy
        )
        error?.let {
            AppNotice(it, kind = NoticeKind.ERROR, modifier = Modifier.padding(top = UiSpacing.tiny))
        }
        LoadingButton(
            text = "فتح",
            loading = busy,
            onClick = { onUnlock(pin) },
            modifier = Modifier.fillMaxWidth().padding(top = UiSpacing.screen)
        )
        onBiometric?.let { bio ->
            AppTextButton(onClick = bio) { Text("فتح بالبصمة") }
        }
        AppTextButton(onClick = onSignOut) { Text("تسجيل الخروج") }
    }
}