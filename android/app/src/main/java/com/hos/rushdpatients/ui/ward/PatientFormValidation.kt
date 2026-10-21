package com.hos.rushdpatients.ui.ward

import java.time.LocalDate

internal enum class PatientFormField {
    ADMISSION_NUMBER, ADMISSION_DATE, NAME, BIRTH_YEAR, AGE, DIAGNOSIS, TREATMENT, FOLLOW_UP, TEAM
}

internal data class PatientFormError(val field: PatientFormField, val message: String)

internal fun validatePatientForm(
    admittanceNumber: String, admittanceDate: LocalDate?, today: LocalDate, name: String,
    ageText: String, birthYear: Int?, currentYear: Int, age: Int?, initialDiagnosis: String,
    resolvedTreatments: List<String>, resolvedFollowUp: List<String>, residentId: String?, specialistId: String?
): List<PatientFormError> = buildList {
    if (admittanceNumber.isBlank()) add(PatientFormError(PatientFormField.ADMISSION_NUMBER, "رقم القبول الحالي مطلوب"))
    if (admittanceDate == null) add(PatientFormError(PatientFormField.ADMISSION_DATE, "اختر تاريخ الدخول"))
    if (admittanceDate?.isAfter(today) == true) {
        add(PatientFormError(PatientFormField.ADMISSION_DATE, "تاريخ الدخول لا يمكن أن يكون في المستقبل"))
    }
    if (name.isBlank()) add(PatientFormError(PatientFormField.NAME, "اسم المريض مطلوب"))
    if (ageText.toIntOrNull()?.let { it !in 0..120 } == true) {
        add(PatientFormError(PatientFormField.AGE, "العمر يجب أن يكون بين 0 و120 سنة"))
    }
    if (birthYear == null) add(PatientFormError(PatientFormField.BIRTH_YEAR, "أدخل سنة الميلاد أو العمر"))
    if (birthYear != null && birthYear > currentYear) {
        add(PatientFormError(PatientFormField.BIRTH_YEAR, "سنة الميلاد لا يمكن أن تكون في المستقبل"))
    }
    if (birthYear != null && age == null) add(PatientFormError(PatientFormField.BIRTH_YEAR, "سنة الميلاد تنتج عمراً غير صحيح"))
    if (initialDiagnosis.isBlank()) add(PatientFormError(PatientFormField.DIAGNOSIS, "التشخيص الأولي مطلوب"))
    if (resolvedTreatments.none { it != FIELD_SEPARATOR && it.isNotBlank() }) {
        add(PatientFormError(PatientFormField.TREATMENT, "الخطة العلاجية مطلوبة"))
    }
    if (resolvedFollowUp.none { it != FIELD_SEPARATOR && it.isNotBlank() }) {
        add(PatientFormError(PatientFormField.FOLLOW_UP, "المتابعة مطلوبة"))
    }
    if (residentId != null && residentId == specialistId) {
        add(PatientFormError(PatientFormField.TEAM, "يجب اختيار طبيبين مختلفين للمقيم والمشرف"))
    }
}
