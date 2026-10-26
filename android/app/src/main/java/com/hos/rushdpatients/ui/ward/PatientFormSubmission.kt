package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadge
import java.time.LocalDate

/** Convert one draft at Save; preserve saved identity/revision and all clinical fields. */
internal fun patientForSubmission(
    initial: Patient?, admittanceNumber: String, admittanceDate: LocalDate?, gender: Gender,
    name: String, resolvedBirthDate: LocalDate?, hasCompanion: Boolean, diagnosisType: DiagnosisType,
    initialDiagnosis: String, treatmentPlan: String, followUp: String, labs: String,
    residentId: String?, specialistId: String?, resolvedBadges: List<PatientBadge>,
    tasks: List<com.hos.rushdpatients.data.model.PatientTask>, isPriority: Boolean
): Patient = initial?.copy(
    admittanceNumber = admittanceNumber.trim(),
    admittanceDate = admittanceDate,
    gender = gender,
    name = name.trim(),
    birthDate = resolvedBirthDate,
    hasCompanion = hasCompanion,
    diagnosisType = diagnosisType,
    initialDiagnosis = initialDiagnosis.trim(),
    treatmentPlan = treatmentPlan.trim(),
    followUp = followUp.trim(),
    labs = labs.trim(),
    responsibleResidentId = residentId,
    responsibleSpecialistId = specialistId,
    badges = resolvedBadges,
    tasks = tasks,
    isPriority = isPriority
) ?: Patient(
    admittanceNumber = admittanceNumber.trim(),
    admittanceDate = admittanceDate,
    gender = gender,
    name = name.trim(),
    birthDate = resolvedBirthDate,
    hasCompanion = hasCompanion,
    diagnosisType = diagnosisType,
    initialDiagnosis = initialDiagnosis.trim(),
    treatmentPlan = treatmentPlan.trim(),
    followUp = followUp.trim(),
    labs = labs.trim(),
    responsibleResidentId = residentId,
    responsibleSpecialistId = specialistId,
    badges = resolvedBadges,
    tasks = tasks,
    isPriority = isPriority
)
