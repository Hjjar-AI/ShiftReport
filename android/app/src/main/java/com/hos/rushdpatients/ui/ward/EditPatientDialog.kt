package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient

@Composable
internal fun EditPatientDialog(
    patient: Patient,
    doctors: List<Doctor>,
    saving: Boolean = false,
    retainedState: EditPatientState? = null,
    onConfirm: (Patient, (Patient?) -> Unit) -> Unit,
    onConfirmAndNext: ((Patient, (Patient?) -> Unit) -> Unit)? = null,
    roundsNavigation: PatientRoundsNavigation? = null,
    saveAndNextEnabled: Boolean = false,
    onSaveAndNextChanged: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    val session = retainedState ?: remember(patient.id) { EditPatientState(patient) }
    var initial by session::initial
    var baseline by session::baseline
    var rejected by session::rejected
    var latest by session::latest
    var reviewing by session::reviewing
    fun submit(draft: Patient, advance: Boolean) {
        val onStale: (Patient?) -> Unit = { saved ->
            rejected = draft
            latest = saved
            reviewing = true
        }
        if (advance) onConfirmAndNext?.invoke(draft, onStale) else onConfirm(draft, onStale)
    }
    PatientFormDialog(
        title = uiText(R.string.ui_edit_patient_dialog_001),
        initial = initial,
        savedBaseline = baseline,
        restoredDraft = null,
        stateStore = session.form,
        doctors = doctors,
        saving = saving,
        reviewAction = {
            roundsNavigation?.let { navigation ->
                Text(uiText(R.string.ui_patient_rounds_navigation_002, com.hos.rushdpatients.ui.components.uiNumber(navigation.index + 1), com.hos.rushdpatients.ui.components.uiNumber(navigation.total)),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                com.hos.rushdpatients.ui.components.AppToggleRow(
                    title = uiText(R.string.ui_edit_patient_dialog_002), checked = saveAndNextEnabled,
                    onCheckedChange = onSaveAndNextChanged, enabled = !saving)
                if (navigation.nextId == null) Text(uiText(R.string.ui_edit_patient_dialog_003),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            if (rejected != null) {
                AppTextButton(onClick = { reviewing = true }, enabled = !saving) {
                    Text(uiText(R.string.ui_edit_patient_dialog_004))
                }
            }
        },
        onDraftChanged = {},
        onConfirm = { submit(it, false) },
        onConfirmAndNext = if (onConfirmAndNext == null) null else { draft -> submit(draft, true) },
        onDismiss = onDismiss
    )
    if (reviewing) {
        val draft = rejected ?: return
        val saved = latest
        StaleEditReviewDialog(
            differences = saved?.let { patientDifferences(uiText, draft, it, doctors) }.orEmpty(),
            available = saved != null && !saved.isDeleted,
            onKeepDraft = {
                if (saved != null && !saved.isDeleted) {
                    // Preserve current persistence metadata; clinical replacements were explicitly reviewed.
                    baseline = saved
                    initial = draft.copy(
                        revision = saved.revision,
                        sortOrder = saved.sortOrder,
                        updatedAt = saved.updatedAt,
                        deletedAt = saved.deletedAt,
                        lastEditedByDoctorId = saved.lastEditedByDoctorId,
                        lastEditedByName = saved.lastEditedByName
                    )
                    reviewing = false
                }
            },
            onUseSaved = {
                if (saved != null && !saved.isDeleted) {
                    baseline = saved
                    initial = saved
                    reviewing = false
                }
            },
            onDismiss = { reviewing = false }
        )
    }
}

private fun patientDifferences(uiText: UiStrings,
    draft: Patient, saved: Patient, doctors: List<Doctor>
): List<Triple<String, String, String>> {
    val names = doctors.associate { it.id to it.fullName }
    fun yesNo(value: Boolean) = if (value) uiText(R.string.ui_yes) else uiText(R.string.ui_no)
    return buildList {
        fun <T> compare(
            label: String, value: (Patient) -> T,
            format: (T) -> String = { it?.toString().orEmpty() }
        ) {
            val draftValue = value(draft)
            val savedValue = value(saved)
            if (draftValue != savedValue) add(Triple(label, format(draftValue), format(savedValue)))
        }
        compare(uiText(R.string.ui_edit_patient_dialog_007), { it.admittanceNumber })
        compare(uiText(R.string.ui_edit_patient_dialog_008), { it.admittanceDate })
        compare(uiText(R.string.ui_patient_demographic_fields_010), { it.gender }) { if (it == Gender.MALE) uiText(R.string.ui_male) else uiText(R.string.ui_female) }
        compare(uiText(R.string.ui_edit_patient_dialog_009), { it.name })
        compare(uiText(R.string.ui_edit_patient_dialog_010), { it.birthDate })
        compare(uiText(R.string.ui_edit_patient_dialog_011), { it.hasCompanion }, ::yesNo)
        compare(uiText(R.string.ui_patient_form_dialog_005), { it.diagnosisType }) { it.uiLabel(uiText) }
        compare(uiText(R.string.ui_patient_card_024), { it.initialDiagnosis })
        compare(uiText(R.string.ui_patient_form_dialog_011), { it.treatmentPlan })
        compare(uiText(R.string.ui_patient_form_dialog_012), { it.followUp })
        compare(uiText(R.string.ui_labs), { it.labs })
        compare(uiText(R.string.ui_tasks), { it.tasks }) { PatientTasks.summary(it, names) }
        compare(uiText(R.string.ui_patient_form_dialog_015), { it.responsibleResidentId }) { id -> id?.let { names[it] ?: it }.orEmpty() }
        compare(uiText(R.string.ui_patient_form_dialog_017), { it.responsibleSpecialistId }) { id -> id?.let { names[it] ?: it }.orEmpty() }
        compare(uiText(R.string.ui_edit_patient_dialog_012), { it.badges }) { badges ->
            badges.joinToString("\n") { uiText(R.string.ui_edit_patient_dialog_014, it.text, it.priority?.uiLabel(uiText) ?: uiText(R.string.ui_edit_patient_dialog_013)) }
        }
        compare(uiText(R.string.ui_edit_patient_dialog_015), { it.isPriority }, ::yesNo)
    }
}
