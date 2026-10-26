package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.domain.patient.PatientPredicates

internal enum class WardTaskFilter { PENDING;
    companion object {
        fun fromSaved(value: String): WardTaskFilter? = when (value) {
            "PENDING", "OVERDUE", "UNASSIGNED" -> PENDING
            else -> null
        }
    }
}

/** Normalize each field once per patient snapshot; preserve field-by-field substring matching. */
internal fun wardSearchIndex(patients: List<Patient>): Map<String, List<String>> = patients.associate { patient ->
    patient.id to listOf(patient.displayName, patient.admittanceNumber, patient.initialDiagnosis,
        patient.treatmentPlan, patient.followUp, patient.labs,
        patient.tasks.joinToString(" ") { it.description }).map(ArabicSearchNormalizer::normalize)
}

internal fun filterWardPatients(
    patients: List<Patient>, filters: WardFilterOptions, currentDoctorId: String,
    dayLabel: java.time.LocalDate, pinnedPatientIds: Set<String>, searchIndex: Map<String, List<String>>
): List<Patient> {
    val needle = ArabicSearchNormalizer.normalize(filters.searchQuery)
    return patients.filter { patient ->
        (filters.viewMode != WardViewMode.MINE || PatientPredicates.mine(patient, currentDoctorId)) &&
            (!filters.priorityOnly || patient.isPriority) &&
            (!filters.warningsOnly || PatientPredicates.warnings(patient)) &&
            (!filters.unassignedOnly || PatientPredicates.unassigned(patient)) &&
            (!filters.urgentOnly || PatientPredicates.urgent(patient)) &&
            (!filters.newAdmissionsOnly || PatientPredicates.newAdmission(patient, dayLabel)) &&
            when (filters.taskFilter) {
                WardTaskFilter.PENDING -> patient.tasks.any { !it.done }
                null -> true
            } && (needle.isEmpty() || searchIndex[patient.id].orEmpty().any { needle in it })
    }.sortedByDescending { it.id in pinnedPatientIds }
}
