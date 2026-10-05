package com.hos.rushdpatients.ui.doctors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.util.ArabicNumbers
import com.hos.rushdpatients.ui.components.PasswordField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDoctorDialog(
    existing: Doctor?,
    onConfirm: (
        firstName: String,
        lastName: String,
        gender: Gender,
        clinicalRole: ClinicalRole,
        pin: String?,
        customTitle: String?,
        telegramId: Long?
    ) -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false
) {
    var firstName by remember { mutableStateOf(existing?.firstName ?: "") }
    var lastName by remember { mutableStateOf(existing?.lastName ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: Gender.MALE) }
    var clinicalRole by remember { mutableStateOf(existing?.clinicalRole ?: ClinicalRole.RESIDENT) }
    var pin by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf(existing?.customTitle ?: "") }
    var telegramIdText by remember { mutableStateOf(existing?.telegramId?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (existing == null) "إضافة طبيب" else "تعديل بيانات الطبيب") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("الاسم الأول") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("اسم العائلة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = gender == Gender.MALE,
                        onClick = { gender = Gender.MALE },
                        label = { Text("ذكر") }
                    )
                    FilterChip(
                        selected = gender == Gender.FEMALE,
                        onClick = { gender = Gender.FEMALE },
                        label = { Text("أنثى") }
                    )
                }
                Text("التصنيف السريري")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ClinicalRole.entries.forEach { role ->
                        FilterChip(
                            selected = clinicalRole == role,
                            onClick = { clinicalRole = role },
                            label = { Text(role.arabicLabel) }
                        )
                    }
                }
                PasswordField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = if (existing == null) "الرقم السري (4-8 أرقام)"
                    else "رقم سري جديد (اتركه فارغاً دون تغيير)"
                )
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { if (it.length <= 16) customTitle = it },
                    label = { Text("لقب مخصص (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = telegramIdText,
                    onValueChange = { telegramIdText = it },
                    label = { Text("معرف تليجرام (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    onConfirm(
                        firstName,
                        lastName,
                        gender,
                        clinicalRole,
                        pin.takeIf { it.isNotBlank() },
                        customTitle.takeIf { it.isNotBlank() },
                        ArabicNumbers.parseLongOrNull(telegramIdText)
                    )
                },
                modifier = Modifier.padding(horizontal = 4.dp)
            ) { Text(if (saving) "جار الحفظ…" else "حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
        }
    )
}
