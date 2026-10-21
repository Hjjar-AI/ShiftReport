package com.hos.rushdpatients.ui.ward

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
            title = "نقل إلى السلة",
            message = "سيُنقل ${p.name} إلى سلة المحذوفات. يمكنك استعادته من السلة.",
            confirmText = "نقل إلى السلة",
            destructive = true,
            onConfirm = { onDeleteConfirmed(p) },
            onDismiss = onDismissDelete
        )
    }
}
