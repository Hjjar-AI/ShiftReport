package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Patient

data class ReadinessIssue(val patientIds: Set<String>)

/** One actionable note; clinical edit timestamps are not a readiness requirement. */
object ReportReadiness {
    fun issues(patients: List<Patient>): List<ReadinessIssue> {
        val ids = patients.filter {
            it.responsibleSpecialistId.isNullOrBlank() || it.responsibleResidentId.isNullOrBlank() ||
                it.initialDiagnosis.isBlank() || it.treatmentPlan.isBlank() || it.followUp.isBlank()
        }.mapTo(linkedSetOf()) { it.id }
        return if (ids.isEmpty()) emptyList() else listOf(ReadinessIssue(ids))
    }
}
