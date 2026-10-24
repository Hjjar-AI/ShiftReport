package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Patient

enum class ReadinessKind { SUPERVISOR, RESIDENT, DIAGNOSIS, TREATMENT, FOLLOW_UP, NOT_UPDATED }
data class ReadinessIssue(val kind: ReadinessKind, val patientIds: Set<String>, val message: String)

/** Non-blocking notes carry the exact affected identities for review navigation. */
object ReportReadiness {
    fun issues(patients: List<Patient>, shiftDate: java.time.LocalDate = com.hos.rushdpatients.util.ShiftDate.current()): List<ReadinessIssue> = buildList {
        fun issue(kind: ReadinessKind, label: String, predicate: (Patient) -> Boolean) {
            val ids = patients.filter(predicate).mapTo(linkedSetOf()) { it.id }
            if (ids.isNotEmpty()) add(ReadinessIssue(kind, ids, "${ids.size} $label"))
        }
        issue(ReadinessKind.SUPERVISOR, "مريض دون اختصاصي") { it.responsibleSpecialistId.isNullOrBlank() }
        issue(ReadinessKind.RESIDENT, "مريض دون مقيم") { it.responsibleResidentId.isNullOrBlank() }
        issue(ReadinessKind.DIAGNOSIS, "مريض دون تشخيص أولي") { it.initialDiagnosis.isBlank() }
        issue(ReadinessKind.TREATMENT, "مريض دون خطة علاجية") { it.treatmentPlan.isBlank() }
        issue(ReadinessKind.FOLLOW_UP, "مريض دون متابعة") { it.followUp.isBlank() }
        val start = com.hos.rushdpatients.util.ShiftDate.endOf(shiftDate.minusDays(1))
        issue(ReadinessKind.NOT_UPDATED, "مريض لم يُحدّث خلال المناوبة") { it.updatedAt.isBefore(start) }
    }
    fun warnings(patients: List<Patient>): List<String> = issues(patients).map { it.message }
}
