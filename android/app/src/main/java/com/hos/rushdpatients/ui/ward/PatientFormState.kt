package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.data.model.PatientBadgePriority
import java.time.LocalDate

/** Editable clinical values only. Retained in memory by the ward ViewModel, never in a Bundle. */
internal class PatientFormState(initial: Patient?, draft: PatientDraft?) {
    var extrasExpanded by mutableStateOf(initial != null)
    var taskDraft by mutableStateOf<TaskDraftState?>(null)
    var tasks by mutableStateOf(initial?.tasks ?: draft?.tasks.orEmpty())
    var admittanceNumber by mutableStateOf(initial?.admittanceNumber ?: draft?.admittanceNumber.orEmpty())
    var admittanceDate by mutableStateOf<LocalDate?>(initial?.admittanceDate
        ?: draft?.admittanceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
    var gender by mutableStateOf(initial?.gender ?: Gender.fromCode(draft?.gender.orEmpty()))
    var name by mutableStateOf(initial?.name ?: draft?.name.orEmpty())
    var birthYearText by mutableStateOf(initial?.birthDate?.year?.toString() ?: draft?.birthYear.orEmpty())
    var ageText by mutableStateOf(initial?.age?.toString() ?: draft?.age.orEmpty())
    var hasCompanion by mutableStateOf(initial?.hasCompanion ?: draft?.hasCompanion ?: false)
    var isPriority by mutableStateOf(initial?.isPriority ?: draft?.isPriority ?: false)
    var badges by mutableStateOf(initial?.badges ?: draft?.badges.orEmpty().map {
        PatientBadge(it.text, it.priority?.let(PatientBadgePriority::fromCode))
    })
    var badgeDraftText by mutableStateOf(draft?.badgeDraftText.orEmpty())
    var badgeDraftPriority by mutableStateOf(draft?.badgeDraftPriority?.let(PatientBadgePriority::fromCode))
    var diagnosisType by mutableStateOf(initial?.diagnosisType ?: DiagnosisType.fromCode(draft?.diagnosisType.orEmpty()))
    var initialDiagnosis by mutableStateOf(initial?.initialDiagnosis ?: draft?.initialDiagnosis.orEmpty())
    var treatmentItems by mutableStateOf(initial?.let { parseFieldItems(it.treatmentPlan) } ?: draft?.treatmentItems.orEmpty())
    var treatmentDraft by mutableStateOf(draft?.treatmentDraft.orEmpty())
    var followUpItems by mutableStateOf(initial?.let { parseFieldItems(it.followUp) } ?: draft?.followUpItems.orEmpty())
    var followUpDraft by mutableStateOf(draft?.followUpDraft.orEmpty())
    var labItems by mutableStateOf(initial?.let { parseFieldItems(it.labs) } ?: draft?.labItems.orEmpty())
    var labDraft by mutableStateOf(draft?.labDraft.orEmpty())
    var residentId by mutableStateOf(initial?.responsibleResidentId ?: draft?.residentId)
    var specialistId by mutableStateOf(initial?.responsibleSpecialistId ?: draft?.specialistId)
}

/** Reset only when the explicitly reviewed initial patient changes, not on each add-draft callback. */
internal class PatientFormStateStore {
    private var seed: Patient? = null
    private var values: PatientFormState? = null
    fun obtain(initial: Patient?, draft: PatientDraft?): PatientFormState {
        if (values == null || seed != initial) {
            seed = initial
            values = PatientFormState(initial, draft)
        }
        return checkNotNull(values)
    }
    fun clear() { seed = null; values = null }
}

internal class EditPatientState(patient: Patient) {
    var initial by mutableStateOf(patient)
    var baseline by mutableStateOf(patient)
    var rejected by mutableStateOf<Patient?>(null)
    var latest by mutableStateOf<Patient?>(null)
    var reviewing by mutableStateOf(false)
    val form = PatientFormStateStore()
}
