package com.hos.rushdpatients.ui.ward


import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient

@Composable
internal fun EditPatientDialog(
    patient: Patient,
    doctors: List<Doctor>,
    saving: Boolean = false,
    draftWarning: String? = null,
    retainedState: EditPatientState? = null,
    onConfirm: (Patient, (Patient?) -> Unit) -> Unit,
    onConfirmAndNext: ((Patient, (Patient?) -> Unit) -> Unit)? = null,
    roundsNavigation: PatientRoundsNavigation? = null,
    onDismiss: () -> Unit,
    onLoadDraft: (String, (PatientDraft?) -> Unit) -> Unit = { _, done -> done(null) },
    onDraftChanged: (String, PatientDraft) -> Unit = { _, _ -> },
    onDiscardDraft: (String) -> Unit = {}
) {
    val uiText = rememberUiStrings()
    val session = retainedState ?: remember(patient.id) { EditPatientState(patient) }
    androidx.compose.runtime.LaunchedEffect(patient.id) {
        if (!session.draftLoaded) onLoadDraft(patient.id) { draft ->
            if (draft != null) {
                session.form.restore(session.initial, draft)
            }
            session.draftLoaded = true
        }
    }
    if (!session.draftLoaded) return
    val initial = session.initial
    fun submit(draft: Patient, advance: Boolean) {
        val replacement = draft.copy(sortOrder = patient.sortOrder)
        if (advance) onConfirmAndNext?.invoke(replacement, {}) else onConfirm(replacement, {})
    }
    PatientFormDialog(
        title = uiText(R.string.ui_edit_patient_dialog_001),
        initial = initial,
        savedBaseline = patient,
        restoredDraft = null,
        stateStore = session.form,
        doctors = doctors,
        saving = saving,
        draftWarning = draftWarning,
        reviewAction = {
            roundsNavigation?.let { navigation ->
                Text(uiText(R.string.ui_patient_rounds_navigation_002, com.hos.rushdpatients.ui.components.uiNumber(navigation.index + 1), com.hos.rushdpatients.ui.components.uiNumber(navigation.total)),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                if (navigation.nextId == null) Text(uiText(R.string.ui_edit_patient_dialog_003),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

        },
        onDraftChanged = { onDraftChanged(patient.id, it) },
        onConfirm = { submit(it, false) },
        onConfirmAndNext = if (onConfirmAndNext == null) null else { draft -> submit(draft, true) },
        onDismiss = { onDiscardDraft(patient.id); onDismiss() }
    )
}
