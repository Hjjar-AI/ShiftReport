package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Patient

/** Non-blocking completeness notes shared by the ward FAB and report preview. */
object ReportReadiness {
    fun warnings(patients: List<Patient>): List<String> = buildList {
        val noSupervisor = patients.count { it.responsibleSpecialistId.isNullOrBlank() }
        val noResident = patients.count { it.responsibleResidentId.isNullOrBlank() }
        val noDiagnosis = patients.count { it.initialDiagnosis.isBlank() }
        val noTreatment = patients.count { it.treatmentPlan.isBlank() }
        if (noSupervisor > 0) add("$noSupervisor مريض دون اختصاصي")
        if (noResident > 0) add("$noResident مريض دون مقيم")
        if (noDiagnosis > 0) add("$noDiagnosis مريض دون تشخيص أولي")
        if (noTreatment > 0) add("$noTreatment مريض دون خطة علاجية")
    }
}
