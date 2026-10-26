package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadgePriority
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.domain.task.TaskCounts

/** Shared display predicates for ward filters, dashboard totals and the in-memory demo. */
object PatientPredicates {
    fun mine(patient: Patient, doctorId: String): Boolean =
        patient.responsibleResidentId == doctorId || patient.responsibleSpecialistId == doctorId
    fun urgent(patient: Patient): Boolean = patient.isPriority ||
        patient.badges.any { it.priority == PatientBadgePriority.HIGH }
    fun warnings(patient: Patient): Boolean = patient.badges.isNotEmpty()
    fun unassigned(patient: Patient): Boolean =
        patient.responsibleResidentId == null || patient.responsibleSpecialistId == null
    fun newAdmission(patient: Patient, dayLabel: java.time.LocalDate = com.hos.rushdpatients.util.ShiftDate.current()): Boolean = patient.admittanceDaysOn(dayLabel) == 0
}

data class WardSummary(
    val mine: Int, val urgent: Int, val warnings: Int, val unassigned: Int,
    val newAdmissions: Int, val tasks: TaskCounts
) {
    companion object {
        fun from(patients: List<Patient>, doctorId: String, dayLabel: java.time.LocalDate = com.hos.rushdpatients.util.ShiftDate.current()) = WardSummary(
            patients.count { PatientPredicates.mine(it, doctorId) }, patients.count(PatientPredicates::urgent),
            patients.count(PatientPredicates::warnings), patients.count(PatientPredicates::unassigned),
            patients.count { PatientPredicates.newAdmission(it, dayLabel) },
            PatientTasks.counts(patients.flatMap { it.tasks })
        )
    }
}
