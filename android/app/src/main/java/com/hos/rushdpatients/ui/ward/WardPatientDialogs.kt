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
    roundsPatientIds: List<String>,
    editSession: EditPatientState? = null, copyInitial: Patient? = null, copyForm: PatientFormStateStore? = null,
    onSaveEdited: (Patient, Patient, (Patient?) -> Unit, Boolean) -> Unit,
    onDismissEdit: () -> Unit,
    onCopyConfirmed: (Patient) -> Unit, onDismissCopy: () -> Unit,
    onDeleteConfirmed: (Patient) -> Unit, onDismissDelete: () -> Unit,
    onLoadEditDraft: (String, (PatientDraft?) -> Unit) -> Unit = { _, done -> done(null) },
    onEditDraftChanged: (String, PatientDraft) -> Unit = { _, _ -> },
    onDiscardEditDraft: (String) -> Unit = {}
) {
    val uiText = rememberUiStrings()
    editTarget?.let { p ->

        key(p.id) {
            EditPatientDialog(
                patient = state.patients.firstOrNull { it.id == p.id } ?: p,
                retainedState = editSession,
                onLoadDraft = onLoadEditDraft, onDraftChanged = onEditDraftChanged, onDiscardDraft = onDiscardEditDraft,
                doctors = state.doctors,
                saving = state.saving,
                draftWarning = state.draftSaveIssue?.takeIf { it.patientId == p.id }?.message,
                onConfirm = { draft, onUnavailable -> onSaveEdited(p, draft, onUnavailable, false) },
                onConfirmAndNext = if (patientRoundsNavigation(roundsPatientIds, p.id)?.nextId != null)
                    { draft, onUnavailable -> onSaveEdited(p, draft, onUnavailable, true) } else null,
                roundsNavigation = patientRoundsNavigation(roundsPatientIds, p.id),
                onDismiss = onDismissEdit
            )
        }
    }

    copyTarget?.let { source ->
        CopyPatientDialog(
            source = source,
            initialCopy = copyInitial, stateStore = copyForm,
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
