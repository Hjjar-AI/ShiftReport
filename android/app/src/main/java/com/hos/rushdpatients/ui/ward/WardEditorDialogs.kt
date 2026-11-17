package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.hos.rushdpatients.data.model.Patient
import kotlinx.coroutines.launch

/** Editor orchestration retains the existing editor/forms and captures the successor before save. */
@Composable
internal fun WardEditorDialogs(state: WardUiState, viewModel: WardController,
    workspace: WardWorkspaceState, roundsPatients: List<Patient>, snackbarHost: SnackbarHostState) {
    val editor = viewModel.editor
    val uiText = rememberUiStrings()
    val scope = rememberCoroutineScope()
    var returnTarget by workspace::returnTarget
    if (editor.showAdd && !state.patientDraftLoaded) {
        PatientDraftLoadDialog(state.patientDraftLoadFailed,
            onRetry = viewModel::retryLoadDraft, onDismiss = { editor.showAdd = false })
    }
    if (editor.showAdd && state.patientDraftLoaded) {
        val insertionPatients = editor.insertionPatients
        AddPatientDialog(
            doctors = state.doctors,
            draft = state.patientDraft,
            stateStore = editor.addForm,
            draftWarning = state.draftSaveIssue?.takeIf { it.patientId == null }?.message,
            saving = state.saving,
            position = editor.insertionPosition,
            positionLimit = insertionPatients.size + 1,
            onPositionChange = { value -> editor.insertionPosition = value.filter { it.isDigit() }.take(3) },
            onDraftChanged = viewModel::saveDraft,
            onConfirm = {
                val requested = editor.insertionPosition.map { digit -> Character.digit(digit, 10) }.joinToString("").toIntOrNull()
                if (editor.insertionPosition.isBlank() || requested != null) {
                    viewModel.addPatient(it, requested) { editor.showAdd = false }
                }
            },
            onDismiss = { editor.showAdd = false }
        )
    }

    fun saveEdited(p: Patient, draft: Patient, onUnavailable: (Patient?) -> Unit, advance: Boolean) {
        // Capture the displayed successor before saving can change filters/order.
        val nextId = if (advance) patientRoundsNavigation(roundsPatients.map { it.id }, p.id)?.nextId else null
        viewModel.updatePatient(draft, onUnavailable) {
            val current = viewModel.state.value
            val next = nextId?.takeIf { !current.isReadOnly && current.shift?.id == state.shift?.id }
                ?.let { id -> current.patients.firstOrNull { it.id == id && !it.isDeleted } }
            if (next != null) {
                returnTarget = null
                editor.editTarget = next
            } else {
                returnTarget = PatientReturnTarget(draft.id, draft.revision)
                editor.editTarget = null
            }
        }
    }
    WardPatientDialogs(state, editor.editTarget, editor.copyTarget, editor.deleteTarget, roundsPatients.map { it.id },
        onLoadEditDraft = viewModel::loadEditDraft, onEditDraftChanged = viewModel::saveEditDraft,
        onDiscardEditDraft = viewModel::discardEditDraft,
        editSession = editor.editSession, copyInitial = editor.copyInitial, copyForm = editor.copyForm,
        onSaveEdited = ::saveEdited,
        onDismissEdit = { editor.editTarget = null },
        onCopyConfirmed = { patient -> viewModel.addPatient(patient) { editor.copyTarget = null } },
        onDismissCopy = { editor.copyTarget = null }, onDeleteConfirmed = { p ->
            viewModel.deletePatient(p) { deletedPatient ->
                editor.deleteTarget = null
                scope.launch {
                    val result = snackbarHost.showSnackbar(
                        message = uiText(R.string.ui_ward_screen_014, p.name),
                        actionLabel = uiText(R.string.ui_ward_screen_015)
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.restorePatient(deletedPatient)
                    }
                }
            }
        }, onDismissDelete = { editor.deleteTarget = null })

}
