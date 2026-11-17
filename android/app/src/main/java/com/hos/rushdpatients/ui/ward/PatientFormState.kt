package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.data.model.PatientLabGroup
import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.PatientBadgePriority
import java.time.LocalDate

/** Editable clinical values only. Retained in memory by the ward ViewModel, never in a Bundle. */
internal class PatientFormState(initial: Patient?, draft: PatientDraft?) {
    var extrasExpanded by mutableStateOf(
        initial?.let { it.tasks.isNotEmpty() || it.badges.isNotEmpty() || it.isPriority }
            ?: draft?.let { it.tasks.isNotEmpty() || it.badges.isNotEmpty() ||
                it.isPriority || it.badgeDraftText.isNotBlank() }
            ?: false
    )
    var taskEntry by mutableStateOf<com.hos.rushdpatients.data.model.PatientTask?>(null)
    var tasks by mutableStateOf(initial?.tasks ?: draft?.tasks.orEmpty())
    var admittanceNumber by mutableStateOf(initial?.admittanceNumber ?: draft?.admittanceNumber.orEmpty())
    var admittanceDate by mutableStateOf<LocalDate?>(when {
        initial != null -> initial.admittanceDate
        draft != null -> draft.admittanceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        else -> LocalDate.now()
    })
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
    var treatmentText by mutableStateOf(initial?.treatmentPlan ?: clinicalDraftText(draft?.treatmentItems.orEmpty(), draft?.treatmentDraft.orEmpty()))
    var followUpText by mutableStateOf(initial?.followUp ?: clinicalDraftText(draft?.followUpItems.orEmpty(), draft?.followUpDraft.orEmpty()))
    var labGroups by mutableStateOf(initial?.effectiveLabGroups ?: draft?.labGroups ?:
        com.hos.rushdpatients.data.model.PatientLabGroups.fromText(
            clinicalDraftText(draft?.labItems.orEmpty(), draft?.labDraft.orEmpty()), "draft-labs"))
    var residentId by mutableStateOf(initial?.responsibleResidentId ?: draft?.residentId)
    var specialistId by mutableStateOf(initial?.responsibleSpecialistId ?: draft?.specialistId)
    val birthYear: Int? get() = birthYearText.trim().toIntOrNull()
    fun age(currentYear: Int): Int? = birthYear?.let { (currentYear - it).takeIf { age -> age in 0..120 } }
    val resolvedTasks: List<PatientTask>
        get() = tasks + listOfNotNull(taskEntry?.takeIf { it.description.isNotBlank() })

    fun snapshot(): PatientFormSnapshot = PatientFormSnapshot(
        admittanceNumber = admittanceNumber,
        admittanceDate = admittanceDate,
        gender = gender,
        name = name,
        birthYearText = birthYearText,
        ageText = ageText,
        hasCompanion = hasCompanion,
        diagnosisType = diagnosisType,
        initialDiagnosis = initialDiagnosis,
        treatmentText = treatmentText,
        followUpText = followUpText,
        labGroups = labGroups,
        residentId = residentId,
        specialistId = specialistId,
        badges = badges,
        tasks = resolvedTasks,
        badgeDraftText = badgeDraftText,
        badgeDraftPriority = badgeDraftPriority,
        isPriority = isPriority
    )

}

/** Named in-memory fields avoid two independently ordered dirty-check lists. */
internal data class PatientFormSnapshot(
    val admittanceNumber: String,
    val admittanceDate: LocalDate?,
    val gender: Gender,
    val name: String,
    val birthYearText: String,
    val ageText: String,
    val hasCompanion: Boolean,
    val diagnosisType: DiagnosisType,
    val initialDiagnosis: String,
    val treatmentText: String,
    val followUpText: String,
    val labGroups: List<PatientLabGroup>,
    val residentId: String?,
    val specialistId: String?,
    val badges: List<PatientBadge>,
    val tasks: List<PatientTask>,
    val badgeDraftText: String,
    val badgeDraftPriority: PatientBadgePriority?,
    val isPriority: Boolean
)

internal class PatientFormDoctors(doctors: List<Doctor>) {
    private val active = doctors.filterNot { it.isDeleted }
    val residents = active.filter { it.clinicalRole.canBeResident() }
    val supervisors = active.filter { it.clinicalRole.canBeSupervisor() }
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
    fun restore(initial: Patient, draft: PatientDraft) {
        seed = initial
        values = PatientFormState(null, draft)
    }
    fun clear() { seed = null; values = null }
}

internal class EditPatientState(patient: Patient) {
    var initial by mutableStateOf(patient)
    var draftLoaded by mutableStateOf(false)
    var draftLoadFailed by mutableStateOf(false)
    var draftLoadAttempt by mutableStateOf(0)
    val form = PatientFormStateStore()
}

/** Read older encrypted form drafts without dropping their unfinished entry. */
private fun clinicalDraftText(items: List<String>, pending: String): String =
    (items + listOfNotNull(pending.takeIf(String::isNotEmpty))).joinToString("\n")
