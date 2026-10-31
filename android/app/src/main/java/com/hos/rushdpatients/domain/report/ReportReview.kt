package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.withoutTrackingMetadata
import java.time.Instant

/** Compare report content and delivery destinations, excluding sync and device-only metadata. */
object ReportReview {
    fun requireMatches(reviewed: BuiltReport, current: BuiltReport) {
        if (!sameContent(reviewed, current)) throw ReportChangedSinceReviewException()
    }

    /** Explicit v1 field list: adding a model property cannot silently change delivery identity. */
    fun fingerprint(report: BuiltReport): String = digestFields(buildList {
        add("report-content-v1")
        add(report.shift.id)
        add(report.shift.date.toString())
        add(report.summary.shiftDate.toString()) // Captured once at review, independent of wall-clock time.
        add(report.shift.doctorIds.size.toString())
        addAll(report.shift.doctorIds)
        add(report.shift.multiDoctorMode.toString())
        add(report.shift.sortSpecJson.orEmpty())
        add(report.patients.size.toString())
        report.patients.forEach { patient ->
            add(patient.id); add(patient.admittanceNumber); add(patient.admittanceDate?.toString().orEmpty())
            add(patient.gender.name); add(patient.name); add(patient.birthDate?.toString().orEmpty())
            add(patient.hasCompanion.toString()); add(patient.diagnosisType.name)
            add(patient.initialDiagnosis); add(patient.treatmentPlan); add(patient.followUp); add(patient.labs)
            add(patient.responsibleResidentId.orEmpty()); add(patient.responsibleSpecialistId.orEmpty())
            add(patient.isPriority.toString()); add(patient.sortOrder.toString())
            add(patient.tasks.size.toString())
            patient.tasks.forEach { task -> add(task.id); add(task.description); add(task.done.toString()) }
            add(patient.badges.size.toString())
            patient.badges.forEach { badge -> add(badge.text); add(badge.priority?.name.orEmpty()) }
        }
        val doctors = relevantDoctors(report)
        add(doctors.size.toString())
        doctors.forEach { doctor ->
            add(doctor.id); add(doctor.fullName); add(doctor.gender.name); add(doctor.clinicalRole.name)
            add(doctor.supervisorGroupChatId?.toString().orEmpty()); add(doctor.telegramId?.toString().orEmpty())
            add(doctor.rank.toString()); add(doctor.isPermanentAdmin.toString()); add(doctor.isDeleted.toString())
        }
        add(report.textChunks.size.toString()); addAll(report.textChunks)
    })

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

    /** Export approval also covers assignment-day highlighting, without changing delivery receipt keys. */
    fun exportFingerprint(report: BuiltReport): String = digestFields(buildList {
        add(fingerprint(report))
        report.patients.forEach { add(it.assignmentChangedOn?.toString().orEmpty()) }
        // Names-only exports also render empty supervisor teams and their resident membership.
        report.registryDoctors.sortedBy { it.id }.forEach { doctor ->
            add(doctor.id); add(doctor.fullName); add(doctor.clinicalRole.name)
            add(doctor.teamSupervisorId.orEmpty()); add(doctor.isDeleted.toString())
        }
    })

    private fun sameContent(a: BuiltReport, b: BuiltReport): Boolean = fingerprint(a) == fingerprint(b)

    private fun clinicalPatient(patient: Patient): Patient = patient.withoutTrackingMetadata().copy(revision = 0)

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
