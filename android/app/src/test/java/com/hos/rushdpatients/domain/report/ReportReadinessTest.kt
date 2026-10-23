package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReportReadinessTest {
    @Test fun `empty ward has no warnings and optional task owner is never a readiness requirement`() {
        assertTrue(ReportReadiness.warnings(emptyList()).isEmpty())
        val complete = Fixtures.patient(responsibleResidentId = "r", responsibleSpecialistId = "s")
            .copy(tasks = listOf(Fixtures.task()))
        assertTrue(ReportReadiness.warnings(listOf(complete)).isEmpty())
    }
    @Test fun `missing fields aggregate patient counts separately`() {
        val missing = Fixtures.patient(initialDiagnosis = " ", treatmentPlan = "")
        assertEquals(listOf("2 مريض دون اختصاصي", "2 مريض دون مقيم", "2 مريض دون تشخيص أولي", "2 مريض دون خطة علاجية"),
            ReportReadiness.warnings(listOf(missing, missing.copy(id = "second"))))
    }
}
