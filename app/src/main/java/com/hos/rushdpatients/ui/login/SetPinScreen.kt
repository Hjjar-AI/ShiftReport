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
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AppCenteredContent() {
        Text(doctor.fullName, style = MaterialTheme.typography.titleLarge)
        Text(
            "اختر رقمك السري",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = UiSpacing.small)
        )
        Text(
            "مطلوب مرة واحدة على هذا الجهاز لفتح التطبيق بسرعة.",
            modifier = Modifier.padding(top = UiSpacing.small, bottom = UiSpacing.section),
            textAlign = TextAlign.Center
        )
        PasswordField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = "الرقم السري (4-8 أرقام)",
            keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next, enabled = !busy
        )
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it.filter(Char::isDigit).take(8) },
            label = "تأكيد الرقم السري",
            keyboardType = KeyboardType.NumberPassword, enabled = !busy,
            modifier = Modifier.padding(top = UiSpacing.medium)
        )
        error?.let {
            AppNotice(it, kind = NoticeKind.ERROR, modifier = Modifier.padding(top = UiSpacing.tiny))
        }
        LoadingButton(
            text = "حفظ",
            loading = busy,
            onClick = { onConfirm(pin, confirm) },
            modifier = Modifier.fillMaxWidth().padding(top = UiSpacing.screen)
        )
        AppTextButton(onClick = onCancel, enabled = !busy) { Text("إلغاء") }
    }
}