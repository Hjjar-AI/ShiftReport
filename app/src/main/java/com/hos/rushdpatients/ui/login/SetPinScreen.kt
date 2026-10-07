package com.hos.rushdpatients.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(doctor.fullName, style = MaterialTheme.typography.titleLarge)
        Text(
            "اختر رقمك السري",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "مطلوب مرة واحدة على هذا الجهاز لفتح التطبيق بسرعة.",
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
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
            modifier = Modifier.padding(top = 12.dp)
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        LoadingButton(
            text = "حفظ",
            loading = busy,
            onClick = { onConfirm(pin, confirm) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
        TextButton(onClick = onCancel, enabled = !busy) { Text("إلغاء") }
    }
}