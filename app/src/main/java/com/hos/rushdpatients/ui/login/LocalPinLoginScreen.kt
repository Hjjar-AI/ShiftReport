package com.hos.rushdpatients.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // A submitted PIN never enters saved UI state or the ViewModel state.
    var pin by remember(doctorId) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().imePadding()
            .verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(doctorName, style = MaterialTheme.typography.titleLarge)
        Text("الدخول بالرقم السري", style = MaterialTheme.typography.titleMedium)
        Text(
            "المشروع محفوظ على هذا الجهاز. لا تحتاج إلى ملف انضمام أو اتصال بتليجرام للدخول برقمك السري السابق.",
            style = MaterialTheme.typography.bodySmall
        )
        PasswordField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(AppConstants.PIN_MAX_LENGTH) },
            label = "الرقم السري",
            keyboardType = KeyboardType.NumberPassword,
            enabled = !busy
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LoadingButton(
            text = "تسجيل الدخول",
            loading = busy,
            enabled = pin.length >= AppConstants.PIN_MIN_LENGTH,
            onClick = {
                val submitted = pin
                pin = ""
                onLogin(submitted)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
        )
        TextButton(
            enabled = !busy,
            onClick = { pin = ""; onTelegram() },
            modifier = Modifier.heightIn(min = UiSpacing.touchTarget)
        ) { Text("نسيت الرقم السري؟ تحقق عبر تليجرام") }
        TextButton(
            enabled = !busy,
            onClick = { pin = ""; onBack() },
            modifier = Modifier.heightIn(min = UiSpacing.touchTarget)
        ) { Text("اختيار طبيب آخر") }
    }
}
