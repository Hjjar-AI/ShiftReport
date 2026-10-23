package com.hos.rushdpatients.testutil

import com.hos.rushdpatients.data.model.*
import java.time.Instant
import java.time.LocalDate

/** Synthetic records only. Fixed timestamps and January birth dates survive CSV year precision. */
object Fixtures {
    val timestamp: Instant = Instant.parse("2026-01-01T00:00:00Z")

    fun doctor(
        id: String = "doc-1", fullName: String = "د. أرز", firstName: String = "أرز",
        lastName: String = "شجرة", gender: Gender = Gender.MALE, telegramId: Long? = null,
        rank: Int = 0, isPermanentAdmin: Boolean = false, customTitle: String? = null
    ) = Doctor(id = id, fullName = fullName, firstName = firstName, lastName = lastName,
        gender = gender, telegramId = telegramId, rank = rank, isPermanentAdmin = isPermanentAdmin,
        customTitle = customTitle, updatedAt = timestamp)

    fun patient(
        id: String = "p-1", name: String = "شاي", gender: Gender = Gender.MALE,
        diagnosisType: DiagnosisType = DiagnosisType.PSYCHIATRIC,
        initialDiagnosis: String = "تشخيص تجريبي", hasCompanion: Boolean = false,
        admittanceNumber: String = "1", admittanceDate: LocalDate? = LocalDate.of(2020, 1, 1),
        treatmentPlan: String = "خطة تجريبية", labs: String = "", followUp: String = "متابعة تجريبية",
        responsibleResidentId: String? = null, responsibleSpecialistId: String? = null,
        sortOrder: Int = 1
    ) = Patient(id = id, name = name, birthDate = LocalDate.of(1990, 1, 1), gender = gender,
        diagnosisType = diagnosisType, initialDiagnosis = initialDiagnosis, hasCompanion = hasCompanion,
        admittanceNumber = admittanceNumber, admittanceDate = admittanceDate,
        treatmentPlan = treatmentPlan, labs = labs, followUp = followUp,
        responsibleResidentId = responsibleResidentId, responsibleSpecialistId = responsibleSpecialistId,
        sortOrder = sortOrder, updatedAt = timestamp)

    fun shift(id: String = "shift-1", date: LocalDate = LocalDate.of(2026, 9, 26),
        doctorIds: List<String> = listOf("doc-1"), multiDoctorMode: Boolean = false
    ) = Shift(id = id, date = date, doctorIds = doctorIds, multiDoctorMode = multiDoctorMode,
        createdAt = timestamp, updatedAt = timestamp)

    fun task(id: String = "task-1", done: Boolean = false) = PatientTask(
        id = id, description = "مهمة تجريبية", done = done,
        completedByDoctorId = if (done) "doc-1" else null,
        completedByName = if (done) "أرز" else null,
        completedAtEpochMillis = if (done) timestamp.toEpochMilli() else null)
}
