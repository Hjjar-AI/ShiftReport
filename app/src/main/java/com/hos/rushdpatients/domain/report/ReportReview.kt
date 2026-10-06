package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import java.time.Instant

/** Compare report content and delivery destinations, excluding sync and device-only metadata. */
object ReportReview {
    fun requireMatches(reviewed: BuiltReport, current: BuiltReport) {
        if (!sameContent(reviewed, current)) throw ReportChangedSinceReviewException()
    }

    private fun sameContent(a: BuiltReport, b: BuiltReport): Boolean {
        val sameShift = a.shift.id == b.shift.id && a.shift.date == b.shift.date &&
            a.shift.doctorIds == b.shift.doctorIds && a.shift.multiDoctorMode == b.shift.multiDoctorMode &&
            a.shift.sortSpecJson == b.shift.sortSpecJson
        return sameShift && a.patients.map(::clinicalPatient) == b.patients.map(::clinicalPatient) &&
            relevantDoctors(a) == relevantDoctors(b) && a.textChunks == b.textChunks
    }

    private fun clinicalPatient(patient: Patient): Patient = patient.copy(
        revision = 0,
        updatedAt = Instant.EPOCH,
        lastEditedByDoctorId = null,
        lastEditedByName = null
    )

    private fun relevantDoctors(report: BuiltReport): List<Doctor> {
        val ids = report.shift.doctorIds.toSet() + report.patients.flatMap {
            listOfNotNull(it.responsibleResidentId, it.responsibleSpecialistId)
        }
        return report.registryDoctors.filter { it.id in ids }.sortedBy { it.id }.map {
            it.copy(updatedAt = Instant.EPOCH, extraOptions = emptySet(), telegramUsername = null)
        }
    }
}

class ReportChangedSinceReviewException : IllegalStateException(
    "تغيّرت بيانات التقرير أو وجهة الإرسال بعد المراجعة. لم يُرسل التقرير؛ راجع المعاينة المحدثة وأكّد الإرسال من جديد."
)
