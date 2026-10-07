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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.util.ArabicNumbers
import com.hos.rushdpatients.ui.ward.StaleEditReviewDialog
import com.hos.rushdpatients.domain.doctor.DoctorEditInput
import com.hos.rushdpatients.ui.components.PasswordField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDoctorDialog(
    existing: Doctor?,
    onConfirm: (Doctor?, DoctorEditInput, (Doctor?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false
) {
    var baseline by remember(existing?.id) { mutableStateOf(existing) }
    var rejectedInput by remember(existing?.id) { mutableStateOf<DoctorEditInput?>(null) }
    var rejectedBaseline by remember(existing?.id) { mutableStateOf<Doctor?>(null) }
    var latest by remember(existing?.id) { mutableStateOf<Doctor?>(null) }
    var reviewing by remember(existing?.id) { mutableStateOf(false) }
    var telegramIdError by remember(existing?.id) { mutableStateOf(false) }
    var firstName by remember(existing?.id) { mutableStateOf(existing?.firstName ?: "") }
    var lastName by remember(existing?.id) { mutableStateOf(existing?.lastName ?: "") }
    var gender by remember(existing?.id) { mutableStateOf(existing?.gender ?: Gender.MALE) }
    var clinicalRole by remember(existing?.id) { mutableStateOf(existing?.clinicalRole ?: ClinicalRole.RESIDENT) }
    var pin by remember(existing?.id) { mutableStateOf("") }
    var customTitle by remember(existing?.id) { mutableStateOf(existing?.customTitle ?: "") }
    var telegramIdText by remember(existing?.id) { mutableStateOf(existing?.telegramId?.toString() ?: "") }

    fun restore(input: DoctorEditInput) {
        firstName = input.firstName
        lastName = input.lastName
        gender = input.gender
        clinicalRole = input.clinicalRole
        pin = input.pin.orEmpty()
        customTitle = input.customTitle.orEmpty()
        telegramIdText = input.telegramId?.toString().orEmpty()
        telegramIdError = false
    }
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
                if (rejectedInput != null) {
                    TextButton(onClick = { reviewing = true }, enabled = !saving) {
                        Text("مراجعة المسودة المرفوضة والنسخة المحفوظة")
                    }
                }
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
                    keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next,
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
                    onValueChange = { telegramIdText = it; telegramIdError = false },
                    label = { Text("معرف تليجرام (اختياري)") },
                    isError = telegramIdError,
                    supportingText = { if (telegramIdError) Text("أدخل معرّفاً رقمياً موجباً أو اترك الحقل فارغاً") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    val telegramId = ArabicNumbers.parseLongOrNull(telegramIdText)
                    if (telegramIdText.isNotBlank() && (telegramId == null || telegramId <= 0)) {
                        telegramIdError = true
                        return@Button
                    }
                    val input = DoctorEditInput(
                        firstName, lastName, gender, clinicalRole,
                        pin.takeIf { it.isNotBlank() }, customTitle.takeIf { it.isNotBlank() }, telegramId
                    )
                    val expected = baseline
                    onConfirm(expected, input) { saved ->
                        rejectedInput = input
                        rejectedBaseline = expected
                        latest = saved
                        reviewing = true
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp)
            ) { Text(if (saving) "جار الحفظ…" else "حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
        }
    )
    if (reviewing) {
        val draft = rejectedInput ?: return
        val saved = latest
        val differences = saved?.let {
            val savedInput = doctorInput(it)
            val draftValues = listOf(draft.firstName, draft.lastName,
                if (draft.gender == Gender.MALE) "ذكر" else "أنثى", draft.clinicalRole.arabicLabel,
                draft.customTitle.orEmpty(), draft.telegramId?.toString().orEmpty())
            val savedValues = listOf(savedInput.firstName, savedInput.lastName,
                if (savedInput.gender == Gender.MALE) "ذكر" else "أنثى", savedInput.clinicalRole.arabicLabel,
                savedInput.customTitle.orEmpty(), savedInput.telegramId?.toString().orEmpty())
            val labels = listOf("الاسم الأول", "اسم العائلة", "الجنس", "التصنيف السريري", "اللقب", "معرّف تليجرام")
            buildList {
                labels.indices.forEach { index ->
                    if (draftValues[index] != savedValues[index]) add(Triple(labels[index], draftValues[index], savedValues[index]))
                }
                val previous = rejectedBaseline
                if (previous?.rank != it.rank || previous.isPermanentAdmin != it.isPermanentAdmin) {
                    add(Triple("صلاحيات المدير (تُحفظ الصلاحيات الأحدث)", doctorPermissionLabel(previous), doctorPermissionLabel(it)))
                }
                if (draft.pin != null) add(Triple("الرقم السري", "تعيين رقم جديد؛ لا يُعرض هنا", "الرقم الحالي لا يُعرض هنا"))
            }
        }.orEmpty()
        StaleEditReviewDialog(
            differences = differences,
            available = saved != null && !saved.isDeleted,
            onKeepDraft = {
                if (saved != null && !saved.isDeleted) {
                    baseline = saved
                    restore(draft)
                    reviewing = false
                }
            },
            onUseSaved = {
                if (saved != null && !saved.isDeleted) {
                    baseline = saved
                    restore(doctorInput(saved))
                    reviewing = false
                }
            },
            onDismiss = { reviewing = false }
        )
    }
}

private fun doctorInput(doctor: Doctor) = DoctorEditInput(
    doctor.firstName, doctor.lastName, doctor.gender, doctor.clinicalRole,
    null, doctor.customTitle, doctor.telegramId
)

private fun doctorPermissionLabel(doctor: Doctor?): String = when {
    doctor == null -> "غير متاح"
    doctor.isPermanentAdmin -> "مدير دائم؛ رتبة ${doctor.rank}"
    doctor.isAdmin -> "مدير؛ رتبة ${doctor.rank}"
    else -> "دون صلاحية مدير"
}
