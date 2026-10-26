package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppDivider

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import java.time.Instant

@Composable
internal fun AddPatientDialog(
    doctors: List<Doctor>,
    draft: PatientDraft? = null,
    stateStore: PatientFormStateStore? = null,
    saving: Boolean = false,
    draftWarning: String? = null,
    position: String = "",
    positionLimit: Int? = null,
    onPositionChange: (String) -> Unit = {},
    onDraftChanged: (PatientDraft) -> Unit = {},
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    PatientFormDialog(
        title = uiText(R.string.ui_add_patient),
        initial = null,
        restoredDraft = draft,
        stateStore = stateStore,
        doctors = doctors,
        saving = saving,
        draftWarning = draftWarning,
        entryOptionsSummary = position.takeIf { positionLimit != null && it.isNotBlank() }?.let {
            uiText(R.string.ui_add_patient_dialog_002) + uiNumber(it)
        },
        entryOptions = {
            if (positionLimit != null) Column(Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                AppDivider()
                AppSectionHeader(
                    title = uiText(R.string.ui_add_patient_dialog_002) + if (position.isBlank()) uiText(R.string.ui_add_patient_dialog_003) else uiNumber(position))
                com.hos.rushdpatients.ui.components.AppTextField(
                    value = position, onValueChange = onPositionChange,
                    label = { Text(uiText(R.string.ui_add_patient_dialog_004, uiNumber(1), uiNumber(positionLimit))) },
                    supportingText = { Text(uiText(R.string.ui_add_patient_dialog_005)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    enabled = !saving, singleLine = true)
                if (position.isNotBlank()) AppTextButton(onClick = { onPositionChange("") }, enabled = !saving) {
                    Text(uiText(R.string.ui_add_patient_dialog_006))
                }
            }
        },
        onDraftChanged = onDraftChanged,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
internal fun CopyPatientDialog(
    source: Patient,
    doctors: List<Doctor>,
    saving: Boolean = false,
    initialCopy: Patient? = null,
    stateStore: PatientFormStateStore? = null,
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    val copy = initialCopy ?: remember(source) { newPatientCopy(source) }
    PatientFormDialog(
        title = uiText(R.string.ui_add_patient_dialog_007),
        initial = copy,
        stateStore = stateStore,
        restoredDraft = null,
        doctors = doctors,
        saving = saving,
        onDraftChanged = {},
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

internal fun newPatientCopy(source: Patient): Patient = source.copy(
    id = java.util.UUID.randomUUID().toString(), admittanceNumber = "", admittanceDate = null,
    isPriority = false, tasks = emptyList(), lastEditedByDoctorId = null, lastEditedByName = null,
    sortOrder = 0, updatedAt = Instant.now(), deletedAt = null
)
