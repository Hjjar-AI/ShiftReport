package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.ui.components.ConfirmDialog

@Composable
internal fun WardPatientDialogs(
    state: WardUiState, editTarget: Patient?, copyTarget: Patient?, deleteTarget: Patient?,
    roundsPatientIds: List<String>, onSaveEdited: (Patient, Patient, (Patient?) -> Unit, Boolean) -> Unit,
    onSaveAndNextChanged: (Boolean) -> Unit, onDismissEdit: () -> Unit,
    onCopyConfirmed: (Patient) -> Unit, onDismissCopy: () -> Unit,
    onDeleteConfirmed: (Patient) -> Unit, onDismissDelete: () -> Unit
) {
    val uiText = rememberUiStrings()
    editTarget?.let { p ->

        key(p.id) {
            EditPatientDialog(
                patient = p,
                doctors = state.doctors,
                saving = state.saving,
                onConfirm = { draft, onStale -> onSaveEdited(p, draft, onStale, false) },
                onConfirmAndNext = if (state.saveAndNext &&
                    patientRoundsNavigation(roundsPatientIds, p.id)?.nextId != null)
                    { draft, onStale -> onSaveEdited(p, draft, onStale, true) } else null,
                roundsNavigation = patientRoundsNavigation(roundsPatientIds, p.id),
                saveAndNextEnabled = state.saveAndNext,
                onSaveAndNextChanged = onSaveAndNextChanged,
                onDismiss = onDismissEdit
            )
        }
    }

    copyTarget?.let { source ->
        CopyPatientDialog(
            source = source,
            doctors = state.doctors,
            saving = state.saving,
            onConfirm = onCopyConfirmed,
            onDismiss = onDismissCopy
        )
    }

    deleteTarget?.let { p ->
        ConfirmDialog(
            title = uiText(R.string.ui_ward_patient_dialogs_001),
            message = uiText(R.string.ui_ward_patient_dialogs_002, p.name),
            confirmText = uiText(R.string.ui_ward_patient_dialogs_001),
            destructive = true,
            onConfirm = { onDeleteConfirmed(p) },
            onDismiss = onDismissDelete
        )
    }
}
