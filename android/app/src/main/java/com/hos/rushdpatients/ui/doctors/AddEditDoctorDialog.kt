package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
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
    doctors: List<Doctor> = emptyList(),
    operationIssue: String? = null
) {
    val uiText = rememberUiStrings()
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
    var telegramIdText by remember(existing?.id) { mutableStateOf(existing?.telegramId?.toString() ?: "") }

    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = firstName != baseline?.firstName.orEmpty() || lastName != baseline?.lastName.orEmpty() ||
        gender != (baseline?.gender ?: Gender.MALE) || clinicalRole != (baseline?.clinicalRole ?: ClinicalRole.RESIDENT) ||
        teamSupervisorId != baseline?.teamSupervisorId || pin.isNotEmpty() ||
        telegramIdText != baseline?.telegramId?.toString().orEmpty()
    val requestDismiss: () -> Unit = { if (!saving) { if (dirty) confirmDiscard = true else onDismiss() } }

    fun restore(input: DoctorEditInput) {
        firstName = input.firstName
        lastName = input.lastName
        gender = input.gender
        clinicalRole = input.clinicalRole
        teamSupervisorId = input.teamSupervisorId
        pin = input.pin.orEmpty()
        telegramIdText = input.telegramId?.toString().orEmpty()
        telegramIdError = false
    }
    CompositionLocalProvider(LocalFieldEnabled provides !saving) {
        if (!reviewing && !confirmDiscard) AppAlertDialog(
            onDismissRequest = requestDismiss,
            title = { AppDialogHeader(if (existing == null) uiText(R.string.ui_add_edit_doctor_dialog_001) else uiText(R.string.ui_add_edit_doctor_dialog_002)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth().appDialogBody()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
                ) {
                    operationIssue?.let {
                        com.hos.rushdpatients.ui.components.AppNotice(it,
                            kind = com.hos.rushdpatients.ui.components.NoticeKind.ERROR)
                    }
                    if (rejectedInput != null) {
                        AppTextButton(onClick = { reviewing = true }, enabled = !saving) {
                            Text(uiText(R.string.ui_edit_patient_dialog_004))
                        }
                    }
                    AppSectionHeader(title = uiText(R.string.ui_patient_demographic_fields_005))
                    AppTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text(uiText(R.string.ui_add_edit_doctor_dialog_003)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text(uiText(R.string.ui_add_edit_doctor_dialog_004)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
                    ) {
                        AppFilterChip(
                            enabled = !saving,
                            selected = gender == Gender.MALE,
                            onClick = { gender = Gender.MALE },
                            label = { Text(uiText(R.string.ui_male)) }
                        )
                        AppFilterChip(
                            enabled = !saving,
                            selected = gender == Gender.FEMALE,
                            onClick = { gender = Gender.FEMALE },
                            label = { Text(uiText(R.string.ui_female)) }
                        )
                    }
                    AppSectionHeader(title = uiText(R.string.ui_add_edit_doctor_dialog_006))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        ClinicalRole.entries.forEach { role ->
                            AppFilterChip(
                            enabled = !saving,
                                selected = clinicalRole == role,
                                onClick = { clinicalRole = role },
                                label = { Text(role.uiLabel(uiText)) }
                            )
                        }
                    }
                    if (clinicalRole == ClinicalRole.RESIDENT) {
                        DoctorDropdown(label = uiText(R.string.ui_add_edit_doctor_dialog_007), undefinedLabel = uiText(R.string.ui_team_management_dialog_001),
                            selectedId = teamSupervisorId,
                            doctors = doctors.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.SUPERVISOR && it.id != existing?.id },
                            onSelected = { teamSupervisorId = it }, enabled = !saving,
                            supportingText = uiText(R.string.ui_add_edit_doctor_dialog_008))
                    }
                    AppSectionHeader(title = uiText(R.string.ui_add_edit_doctor_dialog_009))
                    Text(uiText(R.string.ui_add_edit_doctor_dialog_010))
                    PasswordField(
                        keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next,
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                        label = if (existing == null) uiText(R.string.ui_set_pin_screen_003)
                        else uiText(R.string.ui_add_edit_doctor_dialog_011)
                    )
                    AppTextField(
                        value = telegramIdText,
                        onValueChange = { telegramIdText = it; telegramIdError = false },
                        label = { Text(uiText(R.string.ui_add_edit_doctor_dialog_012)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                        isError = telegramIdError,
                        errorMessage = uiText(R.string.ui_add_edit_doctor_dialog_013),
                        supportingText = { if (telegramIdError) Text(uiText(R.string.ui_add_edit_doctor_dialog_013)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                AppDialogConfirmAction(text = UiAction.SAVE.label(uiText), loading = saving,
                    enabled = !saving,
                    onClick = {
                        val telegramId = ArabicNumbers.parseLongOrNull(telegramIdText)
                        if (telegramIdText.isNotBlank() && (telegramId == null || telegramId <= 0)) {
                            telegramIdError = true
                            return@AppDialogConfirmAction
                        }
                        val input = DoctorEditInput(
                            firstName, lastName, gender, clinicalRole,
                            pin.takeIf { it.isNotBlank() }, baseline?.customTitle, telegramId,
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
    if (confirmDiscard) ConfirmDialog(title = uiText(R.string.ui_patient_form_dialog_039),
        message = uiText(R.string.ui_add_edit_doctor_dialog_014),
        confirmText = uiText(R.string.ui_patient_task_editor_029), dismissText = uiText(R.string.ui_patient_task_editor_030), destructive = true,
        onConfirm = { confirmDiscard = false; onDismiss() }, onDismiss = { confirmDiscard = false })
    if (reviewing) {
        val draft = rejectedInput ?: return
        val saved = latest
        val differences = saved?.let {
            val savedInput = doctorInput(it)
            val draftValues = listOf(draft.firstName, draft.lastName,
                if (draft.gender == Gender.MALE) uiText(R.string.ui_male) else uiText(R.string.ui_female), draft.clinicalRole.uiLabel(uiText),
                draft.telegramId?.toString().orEmpty(),
                doctors.firstOrNull { it.id == draft.teamSupervisorId }?.fullName ?: draft.teamSupervisorId.orEmpty())
            val savedValues = listOf(savedInput.firstName, savedInput.lastName,
                if (savedInput.gender == Gender.MALE) uiText(R.string.ui_male) else uiText(R.string.ui_female), savedInput.clinicalRole.uiLabel(uiText),
                savedInput.telegramId?.toString().orEmpty(),
                doctors.firstOrNull { it.id == savedInput.teamSupervisorId }?.fullName ?: savedInput.teamSupervisorId.orEmpty())
            val labels = listOf(uiText(R.string.ui_add_edit_doctor_dialog_003), uiText(R.string.ui_add_edit_doctor_dialog_004), uiText(R.string.ui_patient_demographic_fields_010), uiText(R.string.ui_add_edit_doctor_dialog_015), uiText(R.string.ui_add_edit_doctor_dialog_017), uiText(R.string.ui_add_edit_doctor_dialog_007))
            buildList {
                labels.indices.forEach { index ->
                    if (draftValues[index] != savedValues[index]) add(Triple(labels[index], draftValues[index], savedValues[index]))
                }
                val previous = rejectedBaseline
                if (previous?.rank != it.rank || previous.isPermanentAdmin != it.isPermanentAdmin) {
                    add(Triple(uiText(R.string.ui_add_edit_doctor_dialog_018), doctorPermissionLabel(uiText, previous), doctorPermissionLabel(uiText, it)))
                }
                if (draft.pin != null) add(Triple(uiText(R.string.ui_local_pin_login_screen_003), uiText(R.string.ui_add_edit_doctor_dialog_019), uiText(R.string.ui_add_edit_doctor_dialog_020)))
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

private fun doctorPermissionLabel(uiText: UiStrings, doctor: Doctor?): String = when {
    doctor == null -> uiText(R.string.ui_add_edit_doctor_dialog_021)
    doctor.isPermanentAdmin -> uiText(R.string.ui_add_edit_doctor_dialog_022, doctor.rank)
    doctor.isAdmin -> uiText(R.string.ui_add_edit_doctor_dialog_023, doctor.rank)
    else -> uiText(R.string.ui_add_edit_doctor_dialog_024)
}
