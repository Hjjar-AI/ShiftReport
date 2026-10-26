package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.data.model.Patient

sealed interface PatientValidationResult {
    data object Valid : PatientValidationResult
    data class Invalid(val errors: List<PatientValidationError>) : PatientValidationResult
}

enum class PatientValidationError {
    ADMITTANCE_DATE_INVALID,
    BIRTH_DATE_INVALID,
    RESIDENT_EQUALS_SUPERVISOR,
    TASKS_INVALID
}

object PatientValidator {

    fun validate(patient: Patient): PatientValidationResult {
        val errors = mutableListOf<PatientValidationError>()

        val today = java.time.LocalDate.now()
        if (patient.admittanceDate?.isAfter(today) == true) {
            errors += PatientValidationError.ADMITTANCE_DATE_INVALID
        }

        val age = patient.age
        if (patient.birthDate != null && (patient.birthDate.isAfter(today) || age == null || age !in 0..120)) {
            errors += PatientValidationError.BIRTH_DATE_INVALID
        }

        if (!patient.responsibleResidentId.isNullOrBlank() &&
            patient.responsibleResidentId == patient.responsibleSpecialistId
        ) {
            errors += PatientValidationError.RESIDENT_EQUALS_SUPERVISOR
        }

        if (runCatching {
                PatientTasks.requireValid(patient.tasks)
            }.isFailure) errors += PatientValidationError.TASKS_INVALID

        return if (errors.isEmpty()) {
            PatientValidationResult.Valid
        } else {
            PatientValidationResult.Invalid(errors)
        }
    }

    fun computeAdmittanceDays(
        admittanceDate: java.time.LocalDate?,
        today: java.time.LocalDate = java.time.LocalDate.now()
    ): Int? {
        if (admittanceDate == null) return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(admittanceDate, today)
        return days.toInt().coerceAtLeast(0)
    }
}
