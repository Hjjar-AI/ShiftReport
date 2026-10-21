package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer

internal fun filterWardPatients(
    patients: List<Patient>, searchQuery: String, viewMode: WardViewMode, currentDoctorId: String,
    priorityOnly: Boolean, warningsOnly: Boolean, unassignedOnly: Boolean, urgentOnly: Boolean,
    newAdmissionsOnly: Boolean, taskFilter: DashboardFilter?, nowEpochMillis: Long, pinnedPatientIds: List<String>
): List<Patient> = patients.filter { patient ->
    val matchesOwner = viewMode != WardViewMode.MINE ||
        patient.responsibleResidentId == currentDoctorId ||
        patient.responsibleSpecialistId == currentDoctorId ||
        patient.tasks.any { !it.done && it.ownerDoctorId == currentDoctorId }
    val taskCounts = PatientTasks.counts(patient.tasks, nowEpochMillis)
    val matchesTasks = when (taskFilter) {
        DashboardFilter.TASK_PENDING -> taskCounts.pending > 0
        DashboardFilter.TASK_OVERDUE -> taskCounts.overdue > 0
        DashboardFilter.TASK_UNASSIGNED -> taskCounts.unassigned > 0
        else -> true
    }
    matchesTasks && matchesOwner && (!priorityOnly || patient.isPriority) &&
        (!warningsOnly || patient.badges.isNotEmpty()) &&
        (!unassignedOnly || patient.responsibleResidentId == null || patient.responsibleSpecialistId == null) &&
        (!urgentOnly || patient.isPriority ||
            patient.badges.any { it.priority == com.hos.rushdpatients.data.model.PatientBadgePriority.HIGH }) &&
        (!newAdmissionsOnly || patient.admittanceDays == 0) &&
        ArabicSearchNormalizer.matches(
        searchQuery,
        patient.name,
        patient.admittanceNumber,
        patient.initialDiagnosis,
        patient.treatmentPlan,
        patient.followUp,
        patient.labs,
        patient.tasks.joinToString(" ") { it.description }
    )
}.sortedByDescending { it.id in pinnedPatientIds }
