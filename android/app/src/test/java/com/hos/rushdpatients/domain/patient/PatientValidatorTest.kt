package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientValidatorTest {
    private fun errors(patient: Patient) = (PatientValidator.validate(patient) as PatientValidationResult.Invalid).errors
    @Test fun `complete unassigned patient and unassigned task are valid`() {
        assertEquals(PatientValidationResult.Valid,
            PatientValidator.validate(Fixtures.patient().copy(tasks = listOf(Fixtures.task()))))
    }
    @Test fun `blank profile and clinical text remain optional`() {
        assertEquals(PatientValidationResult.Valid, PatientValidator.validate(Fixtures.patient(name = " ",
            admittanceNumber = "", initialDiagnosis = "", treatmentPlan = "", followUp = "")))
    }
    @Test fun `optional birth date is accepted but future and overage dates fail`() {
        listOf(LocalDate.now().plusDays(1), LocalDate.now().minusYears(121)).forEach {
            assertTrue(errors(Fixtures.patient().copy(birthDate = it)).contains(PatientValidationError.BIRTH_DATE_INVALID))
        }
        assertEquals(PatientValidationResult.Valid, PatientValidator.validate(Fixtures.patient().copy(birthDate = null)))
        assertEquals(PatientValidationResult.Valid, PatientValidator.validate(Fixtures.patient().copy(birthDate = LocalDate.now())))
    }
    @Test fun `future admission equal team and malformed tasks fail`() {
        val patient = Fixtures.patient(admittanceDate = LocalDate.now().plusDays(1),
            responsibleResidentId = "doc", responsibleSpecialistId = "doc")
            .copy(tasks = listOf(Fixtures.task().copy(description = "")))
        assertEquals(setOf(PatientValidationError.ADMITTANCE_DATE_INVALID,
            PatientValidationError.RESIDENT_EQUALS_SUPERVISOR, PatientValidationError.TASKS_INVALID), errors(patient).toSet())
    }
    @Test fun `admission day calculation handles null future and leap day`() {
        val today = LocalDate.of(2024, 3, 1)
        assertNull(PatientValidator.computeAdmittanceDays(null, today))
        assertEquals(0, PatientValidator.computeAdmittanceDays(today.plusDays(1), today))
        assertEquals(1, PatientValidator.computeAdmittanceDays(LocalDate.of(2024, 2, 29), today))
    }
}
