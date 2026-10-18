package com.hos.rushdpatients.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Patient(
    val id: String = UUID.randomUUID().toString(),
    val admittanceNumber: String = "",
    val admittanceDate: LocalDate? = null,
    val gender: Gender = Gender.MALE,
    val name: String,
    val birthDate: LocalDate? = null,
    val hasCompanion: Boolean = false,
    val diagnosisType: DiagnosisType = DiagnosisType.PSYCHIATRIC,
    val initialDiagnosis: String = "",
    val treatmentPlan: String = "",
    val followUp: String = "",
    val labs: String = "",
    val tasks: List<PatientTask> = emptyList(),
    val responsibleResidentId: String? = null,
    val responsibleSpecialistId: String? = null,
    val badges: List<PatientBadge> = emptyList(),
    val isPriority: Boolean = false,
    val lastEditedByDoctorId: String? = null,
    val lastEditedByName: String? = null,
    val sortOrder: Int = 0,
    val revision: Long = 0,
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
) {
    val isDeleted: Boolean get() = deletedAt != null

    val age: Int?
        get() = birthDate?.let {
            ChronoUnit.YEARS.between(it, LocalDate.now()).coerceAtLeast(0).toInt()
        }

    val admittanceDays: Int?
        get() = admittanceDate?.let {
            ChronoUnit.DAYS.between(it, LocalDate.now()).coerceAtLeast(0).toInt()
        }

}
