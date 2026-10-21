package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.ward.DoctorDropdown
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.AppDialogDismissAction
import com.hos.rushdpatients.ui.components.AppDialogConfirmAction
import com.hos.rushdpatients.ui.components.AppDialogHeader
import androidx.compose.runtime.CompositionLocalProvider
import com.hos.rushdpatients.ui.components.LocalFieldEnabled
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.util.ArabicNumbers
import com.hos.rushdpatients.ui.ward.StaleEditReviewDialog
import com.hos.rushdpatients.domain.doctor.DoctorEditInput
import com.hos.rushdpatients.ui.components.PasswordField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditDoctorDialog(
    existing: Doctor?,
    onConfirm: (Doctor?, DoctorEditInput, (Doctor?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false,
    doctors: List<Doctor> = emptyList()
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
    var teamSupervisorId by remember(existing?.id) { mutableStateOf(existing?.teamSupervisorId) }
    var pin by remember(existing?.id) { mutableStateOf("") }
    var customTitle by remember(existing?.id) { mutableStateOf(existing?.customTitle ?: "") }
    var telegramIdText by remember(existing?.id) { mutableStateOf(existing?.telegramId?.toString() ?: "") }

    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = firstName != baseline?.firstName.orEmpty() || lastName != baseline?.lastName.orEmpty() ||
        gender != (baseline?.gender ?: Gender.MALE) || clinicalRole != (baseline?.clinicalRole ?: ClinicalRole.RESIDENT) ||
        teamSupervisorId != baseline?.teamSupervisorId || pin.isNotEmpty() ||
        customTitle != baseline?.customTitle.orEmpty() || telegramIdText != baseline?.telegramId?.toString().orEmpty()
    val requestDismiss: () -> Unit = { if (!saving) { if (dirty) confirmDiscard = true else onDismiss() } }

    fun restore(input: DoctorEditInput) {
        firstName = input.firstName
        lastName = input.lastName
        gender = input.gender
        clinicalRole = input.clinicalRole
        teamSupervisorId = input.teamSupervisorId
        pin = input.pin.orEmpty()
        customTitle = input.customTitle.orEmpty()
        telegramIdText = input.telegramId?.toString().orEmpty()
        telegramIdError = false
    }
    CompositionLocalProvider(LocalFieldEnabled provides !saving) {
        if (!reviewing && !confirmDiscard) AlertDialog(
            onDismissRequest = requestDismiss,
            title = { AppDialogHeader(if (existing == null) "إضافة طبيب" else "تحرير الطبيب") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth().appDialogBody()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
                ) {
                    if (rejectedInput != null) {
                        AppTextButton(onClick = { reviewing = true }, enabled = !saving) {
                            Text("مراجعة المسودة المرفوضة والنسخة المحفوظة")
                        }
                    }
                    AppSectionHeader(title = "البيانات الشخصية")
                    AppTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("الاسم الأول") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("اسم العائلة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
                    ) {
                        FilterChip(
                            enabled = !saving,
                            selected = gender == Gender.MALE,
                            onClick = { gender = Gender.MALE },
                            label = { Text("ذكر") }
                        )
                        FilterChip(
                            enabled = !saving,
                            selected = gender == Gender.FEMALE,
                            onClick = { gender = Gender.FEMALE },
                            label = { Text("أنثى") }
                        )
                    }
                    AppTextField(
                        value = customTitle,
                        onValueChange = { if (it.length <= 16) customTitle = it },
                        label = { Text("لقب مخصص (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppSectionHeader(title = "الدور السريري والفريق")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        ClinicalRole.entries.forEach { role ->
                            FilterChip(
                            enabled = !saving,
                                selected = clinicalRole == role,
                                onClick = { clinicalRole = role },
                                label = { Text(role.arabicLabel) }
                            )
                        }
                    }
                    if (clinicalRole == ClinicalRole.RESIDENT) {
                        DoctorDropdown(label = "مشرف الفريق", undefinedLabel = "دون فريق",
                            selectedId = teamSupervisorId,
                            doctors = doctors.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.SUPERVISOR && it.id != existing?.id },
                            onSelected = { teamSupervisorId = it }, enabled = !saving,
                            supportingText = "فريق الطبيب الثابت؛ تعيين مريض للتغطية لا يغير هذا الفريق.")
                    }
                    AppSectionHeader(title = "الحساب والتحقق")
                    Text("صلاحية الإدارة مستقلة عن الدور السريري؛ تُعدّل من صفحة صلاحيات المديرين.")
                    PasswordField(
                        keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next,
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                        label = if (existing == null) "الرقم السري (4-8 أرقام)"
                        else "رقم سري جديد (اتركه فارغاً دون تغيير)"
                    )
                    AppTextField(
                        value = telegramIdText,
                        onValueChange = { telegramIdText = it; telegramIdError = false },
                        label = { Text("معرف تليجرام (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                        isError = telegramIdError,
                        supportingText = { if (telegramIdError) Text("أدخل معرّفاً رقمياً موجباً أو اترك الحقل فارغاً") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                AppDialogConfirmAction(text = UiAction.SAVE.label, loading = saving,
                    enabled = !saving,
                    onClick = {
                        val telegramId = ArabicNumbers.parseLongOrNull(telegramIdText)
                        if (telegramIdText.isNotBlank() && (telegramId == null || telegramId <= 0)) {
                            telegramIdError = true
                            return@AppDialogConfirmAction
                        }
                        val input = DoctorEditInput(
                            firstName, lastName, gender, clinicalRole,
                            pin.takeIf { it.isNotBlank() }, customTitle.takeIf { it.isNotBlank() }, telegramId,
                            teamSupervisorId.takeIf { clinicalRole == ClinicalRole.RESIDENT }
                        )
                        val expected = baseline
                        onConfirm(expected, input) { saved ->
                            rejectedInput = input
                            rejectedBaseline = expected
                            latest = saved
                            reviewing = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = UiSpacing.tiny)
                )
            },
            dismissButton = {
                AppDialogDismissAction(onClick = requestDismiss, enabled = !saving)
            }
        )

    }
    if (confirmDiscard) ConfirmDialog(title = "تجاهل التغييرات؟",
        message = "لم تُحفظ تعديلات الطبيب. هل تريد الخروج دون حفظ؟",
        confirmText = "تجاهل والخروج", dismissText = "متابعة التحرير", destructive = true,
        onConfirm = { confirmDiscard = false; onDismiss() }, onDismiss = { confirmDiscard = false })
    if (reviewing) {
        val draft = rejectedInput ?: return
        val saved = latest
        val differences = saved?.let {
            val savedInput = doctorInput(it)
            val draftValues = listOf(draft.firstName, draft.lastName,
                if (draft.gender == Gender.MALE) "ذكر" else "أنثى", draft.clinicalRole.arabicLabel,
                draft.customTitle.orEmpty(), draft.telegramId?.toString().orEmpty(),
                doctors.firstOrNull { it.id == draft.teamSupervisorId }?.fullName ?: draft.teamSupervisorId.orEmpty())
            val savedValues = listOf(savedInput.firstName, savedInput.lastName,
                if (savedInput.gender == Gender.MALE) "ذكر" else "أنثى", savedInput.clinicalRole.arabicLabel,
                savedInput.customTitle.orEmpty(), savedInput.telegramId?.toString().orEmpty(),
                doctors.firstOrNull { it.id == savedInput.teamSupervisorId }?.fullName ?: savedInput.teamSupervisorId.orEmpty())
            val labels = listOf("الاسم الأول", "اسم العائلة", "الجنس", "التصنيف السريري", "اللقب", "معرّف تليجرام", "مشرف الفريق")
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
    null, doctor.customTitle, doctor.telegramId, doctor.teamSupervisorId
)

private fun doctorPermissionLabel(doctor: Doctor?): String = when {
    doctor == null -> "غير متاح"
    doctor.isPermanentAdmin -> "مدير دائم؛ رتبة ${doctor.rank}"
    doctor.isAdmin -> "مدير؛ رتبة ${doctor.rank}"
    else -> "دون صلاحية مدير"
}
