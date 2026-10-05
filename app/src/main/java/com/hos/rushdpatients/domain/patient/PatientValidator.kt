package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.data.model.Patient

sealed interface PatientValidationResult {
    data object Valid : PatientValidationResult
    data class Invalid(val errors: List<PatientValidationError>) : PatientValidationResult
}

enum class PatientValidationError {
    NAME_EMPTY,
    ADMITTANCE_NUMBER_EMPTY,
    ADMITTANCE_DATE_INVALID,
    BIRTH_DATE_INVALID,
    INITIAL_DIAGNOSIS_EMPTY,
    TREATMENT_PLAN_EMPTY,
    FOLLOW_UP_EMPTY,
    RESIDENT_EQUALS_SUPERVISOR
}

object PatientValidator {

    fun validate(patient: Patient): PatientValidationResult {
        val errors = mutableListOf<PatientValidationError>()

        if (patient.name.isBlank()) {
            errors += PatientValidationError.NAME_EMPTY
        }

        if (patient.admittanceNumber.isBlank()) {
            errors += PatientValidationError.ADMITTANCE_NUMBER_EMPTY
        }

        val today = java.time.LocalDate.now()
        if (patient.admittanceDate == null || patient.admittanceDate.isAfter(today)) {
            errors += PatientValidationError.ADMITTANCE_DATE_INVALID
        }

        val age = patient.age
        if (patient.birthDate == null || patient.birthDate.isAfter(today) || age == null || age !in 0..120) {
            errors += PatientValidationError.BIRTH_DATE_INVALID
        }

        if (patient.initialDiagnosis.isBlank()) errors += PatientValidationError.INITIAL_DIAGNOSIS_EMPTY
        if (patient.treatmentPlan.isBlank()) errors += PatientValidationError.TREATMENT_PLAN_EMPTY
        if (patient.followUp.isBlank()) errors += PatientValidationError.FOLLOW_UP_EMPTY
        if (!patient.responsibleResidentId.isNullOrBlank() &&
            patient.responsibleResidentId == patient.responsibleSpecialistId
        ) {
            errors += PatientValidationError.RESIDENT_EQUALS_SUPERVISOR
        }

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
