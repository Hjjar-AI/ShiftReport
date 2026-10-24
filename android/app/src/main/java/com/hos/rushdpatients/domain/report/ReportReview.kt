package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import java.time.Instant

/** Compare report content and delivery destinations, excluding sync and device-only metadata. */
object ReportReview {
    fun requireMatches(reviewed: BuiltReport, current: BuiltReport) {
        if (!sameContent(reviewed, current)) throw ReportChangedSinceReviewException()
    }

    /** Stable clinical identity; independent of publication revisions and PDF generation time. */
    fun fingerprint(report: BuiltReport): String {
        val fields = listOf(report.shift.id, report.shift.date.toString(), report.shift.doctorIds.toString(),
            report.shift.multiDoctorMode.toString(), report.shift.sortSpecJson.orEmpty(),
            report.patients.map(::clinicalPatient).toString(), relevantDoctors(report).toString(), report.textChunks.toString())
        return digestFields(fields)
    }

    /** Different supervisor subsets remain distinct even when they share a Telegram group. */
    fun supervisorFingerprint(report: BuiltReport, supervisorId: String): String {
        require(supervisorId.isNotBlank())
        return digestFields(listOf("supervisor-pdf", supervisorId, fingerprint(report)))
    }

    private fun digestFields(fields: List<String>): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        fields.forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
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
