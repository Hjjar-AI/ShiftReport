package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
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
    position: String = "",
    positionLimit: Int? = null,
    onPositionChange: (String) -> Unit = {},
    onDraftChanged: (PatientDraft) -> Unit = {},
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    var insertionExpanded by rememberSaveable { mutableStateOf(position.isNotBlank()) }
    PatientFormDialog(
        title = uiText(R.string.ui_add_patient),
        initial = null,
        restoredDraft = draft,
        stateStore = stateStore,
        doctors = doctors,
        saving = saving,
        entryOptions = {
            if (positionLimit != null) Column(Modifier.fillMaxWidth()) {
                AppSectionHeader(
                    title = uiText(R.string.ui_add_patient_dialog_002) + if (position.isBlank()) uiText(R.string.ui_add_patient_dialog_003) else uiNumber(position),
                    expanded = insertionExpanded, onToggle = { insertionExpanded = !insertionExpanded })
                if (insertionExpanded) {
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
