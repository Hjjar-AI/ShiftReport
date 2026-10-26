package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Patient

/** One actionable note; clinical edit timestamps are not a readiness requirement. */
object ReportReadiness {
    fun incompletePatientIds(patients: List<Patient>): Set<String> = patients.filter {
        it.responsibleSpecialistId.isNullOrBlank() || it.responsibleResidentId.isNullOrBlank() ||
            it.initialDiagnosis.isBlank() || it.treatmentPlan.isBlank() || it.followUp.isBlank()
    }.mapTo(linkedSetOf()) { it.id }
}
