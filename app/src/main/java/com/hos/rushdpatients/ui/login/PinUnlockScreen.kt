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
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(doctorName, style = MaterialTheme.typography.titleLarge)
        Text("أدخل رقمك السري", modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        PasswordField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = "الرقم السري",
            keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, enabled = !busy
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        LoadingButton(
            text = "فتح",
            loading = busy,
            onClick = { onUnlock(pin) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
        onBiometric?.let { bio ->
            TextButton(onClick = bio) { Text("فتح بالبصمة") }
        }
        TextButton(onClick = onSignOut) { Text("تسجيل الخروج") }
    }
}