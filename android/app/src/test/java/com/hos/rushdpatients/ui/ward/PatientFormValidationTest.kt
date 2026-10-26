package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PatientFormValidationTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "خطأ تجريبي" }
    private val today = LocalDate.of(2026, 10, 23)
    private fun errors(date: LocalDate? = today, ageText: String = "36", year: Int? = 1990,
        age: Int? = 36, resident: String? = null, supervisor: String? = null) = validatePatientForm(text,
            date, today, ageText, year, 2026, age, resident, supervisor).map { it.field }
    @Test fun `complete clinical entry permits optional team and boundary ages`() {
        assertTrue(errors().isEmpty())
        assertTrue(errors(ageText = "0", year = 2026, age = 0).isEmpty())
        assertTrue(errors(ageText = "120", year = 1906, age = 120).isEmpty())
    }
    @Test fun `blank optional profiles and clinical fields do not block save`() {
        val form = PatientFormState(null, PatientDraft())
        assertEquals("", form.name)
        assertEquals("", form.treatmentText)
        assertEquals("", form.followUpText)
        assertTrue(errors(date = null, year = null, age = null, ageText = "").isEmpty())
    }
    @Test fun `future dates and invalid ages are rejected`() {
        assertTrue(PatientFormField.ADMISSION_DATE in errors(date = today.plusDays(1)))
        assertTrue(PatientFormField.BIRTH_YEAR in errors(year = 2027))
        assertTrue(PatientFormField.BIRTH_YEAR in errors(age = null))
        listOf("-1", "121").forEach { assertTrue(PatientFormField.AGE in errors(ageText = it)) }
    }
    @Test fun `legacy separators in optional clinical text do not create validation requirements`() {
        val form = PatientFormState(null, PatientDraft(treatmentItems = listOf(" • ", "خطة"),
            followUpItems = listOf("متابعة", " • ")))
        assertEquals(" • \nخطة", form.treatmentText)
        assertEquals("متابعة\n • ", form.followUpText)
        assertTrue(errors().isEmpty())
    }
    @Test fun `same doctor cannot occupy both responsible roles`() {
        assertEquals(listOf(PatientFormField.TEAM), errors(resident = "same", supervisor = "same"))
        assertTrue(errors(resident = "resident", supervisor = "supervisor").isEmpty())
    }
}
