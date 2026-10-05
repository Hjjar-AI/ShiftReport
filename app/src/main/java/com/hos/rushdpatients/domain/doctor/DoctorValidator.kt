package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Doctor

sealed interface DoctorValidationResult {
    data object Valid : DoctorValidationResult
    data class Invalid(val errors: List<DoctorValidationError>) : DoctorValidationResult
}

enum class DoctorValidationError {
    FIRST_NAME_EMPTY,
    LAST_NAME_EMPTY,
    FORBIDDEN_CHARACTERS,
    CUSTOM_TITLE_TOO_LONG
}

object DoctorValidator {

    private val FORBIDDEN = setOf('\\', '"', '\'', '=', ',', '|', '@', '+', '&')

    fun validate(
        firstName: String,
        lastName: String,
        customTitle: String?
    ): DoctorValidationResult {
        val errors = mutableListOf<DoctorValidationError>()

        if (firstName.isBlank()) errors += DoctorValidationError.FIRST_NAME_EMPTY
        if (lastName.isBlank()) errors += DoctorValidationError.LAST_NAME_EMPTY

        if (firstName.any { it in FORBIDDEN } ||
            lastName.any { it in FORBIDDEN } ||
            customTitle.orEmpty().any { it in FORBIDDEN }
        ) {
            errors += DoctorValidationError.FORBIDDEN_CHARACTERS
        }

        if (customTitle != null && customTitle.length > AppConstants.CUSTOM_TITLE_MAX_LENGTH) {
            errors += DoctorValidationError.CUSTOM_TITLE_TOO_LONG
        }

        return if (errors.isEmpty()) DoctorValidationResult.Valid
        else DoctorValidationResult.Invalid(errors)
    }

    fun validate(doctor: Doctor): DoctorValidationResult = validate(
        firstName = doctor.firstName,
        lastName = doctor.lastName,
        customTitle = doctor.customTitle
    )
}