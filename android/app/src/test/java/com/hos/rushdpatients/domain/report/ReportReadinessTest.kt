package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReportReadinessTest {
    @Test fun `empty ward has no warnings and optional task owner is never a readiness requirement`() {
        assertTrue(ReportReadiness.incompletePatientIds(emptyList()).isEmpty())
        val complete = Fixtures.patient(responsibleResidentId = "r", responsibleSpecialistId = "s")
            .copy(tasks = listOf(Fixtures.task()))
        assertTrue(ReportReadiness.incompletePatientIds(listOf(complete)).isEmpty())
    }
    @Test fun `missing fields produce one actionable patient identity each`() {
        val missing = Fixtures.patient(initialDiagnosis = " ", treatmentPlan = "")
        assertEquals(setOf(missing.id, "second"),
            ReportReadiness.incompletePatientIds(listOf(missing, missing.copy(id = "second"))))
    }
}
