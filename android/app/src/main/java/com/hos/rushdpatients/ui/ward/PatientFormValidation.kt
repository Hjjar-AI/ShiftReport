package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import java.time.LocalDate

internal enum class PatientFormField {
    ADMISSION_NUMBER, ADMISSION_DATE, NAME, BIRTH_YEAR, AGE, DIAGNOSIS, TREATMENT, FOLLOW_UP, TEAM
}

internal data class PatientFormError(val field: PatientFormField, val message: String)

internal fun validatePatientForm(uiText: UiStrings,
    admittanceDate: LocalDate?, today: LocalDate,
    ageText: String, birthYear: Int?, currentYear: Int, age: Int?, residentId: String?, specialistId: String?
): List<PatientFormError> = buildList {
    if (admittanceDate?.isAfter(today) == true) {
        add(PatientFormError(PatientFormField.ADMISSION_DATE, uiText(R.string.ui_patient_form_validation_002)))
    }
    if (ageText.toIntOrNull()?.let { it !in 0..120 } == true) {
        add(PatientFormError(PatientFormField.AGE, uiText(R.string.ui_patient_form_validation_003)))
    }
    if (birthYear != null && birthYear > currentYear) {
        add(PatientFormError(PatientFormField.BIRTH_YEAR, uiText(R.string.ui_patient_form_validation_005)))
    }
    if (birthYear != null && age == null) add(PatientFormError(PatientFormField.BIRTH_YEAR, uiText(R.string.ui_patient_form_validation_006)))
    if (residentId != null && residentId == specialistId) {
        add(PatientFormError(PatientFormField.TEAM, uiText(R.string.ui_patient_form_validation_007)))
    }
}
