package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PatientFormValidationTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "خطأ تجريبي" }
    private val today = LocalDate.of(2026, 10, 23)
    private fun errors(number: String = "1", date: LocalDate? = today, name: String = "شاي",
        ageText: String = "36", year: Int? = 1990, age: Int? = 36, diagnosis: String = "تشخيص",
        treatment: List<String> = listOf("خطة"), followUp: List<String> = listOf("متابعة"),
        resident: String? = null, supervisor: String? = null) = validatePatientForm(text,
            number, date, today, name, ageText, year, 2026, age, diagnosis, treatment, followUp,
            resident, supervisor).map { it.field }
    @Test fun `complete clinical entry permits optional team and boundary ages`() {
        assertTrue(errors().isEmpty())
        assertTrue(errors(ageText = "0", year = 2026, age = 0).isEmpty())
        assertTrue(errors(ageText = "120", year = 1906, age = 120).isEmpty())
    }
    @Test fun `missing required fields produce actionable field errors`() {
        assertEquals(listOf(PatientFormField.ADMISSION_NUMBER, PatientFormField.ADMISSION_DATE,
            PatientFormField.NAME, PatientFormField.BIRTH_YEAR, PatientFormField.DIAGNOSIS,
            PatientFormField.TREATMENT, PatientFormField.FOLLOW_UP),
            errors(number = " ", date = null, name = " ", year = null, age = null,
                diagnosis = " ", treatment = emptyList(), followUp = emptyList()))
    }
    @Test fun `future dates and invalid ages are rejected`() {
        assertTrue(PatientFormField.ADMISSION_DATE in errors(date = today.plusDays(1)))
        assertTrue(PatientFormField.BIRTH_YEAR in errors(year = 2027))
        assertTrue(PatientFormField.BIRTH_YEAR in errors(age = null))
        listOf("-1", "121").forEach { assertTrue(PatientFormField.AGE in errors(ageText = it)) }
    }
    @Test fun `separators and whitespace cannot satisfy clinical fields`() {
        val result = errors(treatment = listOf(" ", FIELD_SEPARATOR), followUp = listOf(FIELD_SEPARATOR, ""))
        assertEquals(listOf(PatientFormField.TREATMENT, PatientFormField.FOLLOW_UP), result)
        assertTrue(errors(treatment = listOf(FIELD_SEPARATOR, "خطة"), followUp = listOf("متابعة", FIELD_SEPARATOR)).isEmpty())
    }
    @Test fun `same doctor cannot occupy both responsible roles`() {
        assertEquals(listOf(PatientFormField.TEAM), errors(resident = "same", supervisor = "same"))
        assertTrue(errors(resident = "resident", supervisor = "supervisor").isEmpty())
    }
}
