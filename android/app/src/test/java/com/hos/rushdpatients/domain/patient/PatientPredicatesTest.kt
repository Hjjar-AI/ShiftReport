package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientPredicatesTest {
    @Test fun `mine includes pending task ownership but excludes completed ownership`() {
        val patient = Fixtures.patient().copy(tasks = listOf(Fixtures.task().copy(ownerDoctorId = "doc")))
        assertTrue(PatientPredicates.mine(patient, "doc"))
        assertFalse(PatientPredicates.mine(patient.copy(tasks = listOf(
            Fixtures.task(done = true).copy(ownerDoctorId = "doc"))), "doc"))
        assertTrue(PatientPredicates.mine(Fixtures.patient(responsibleSpecialistId = "doc"), "doc"))
    }
    @Test fun `dashboard counts overlapping categories without double counting patients`() {
        val patient = Fixtures.patient(responsibleResidentId = "doc", admittanceDate = LocalDate.now())
            .copy(isPriority = true, badges = listOf(PatientBadge("تنبيه", PatientBadgePriority.HIGH)),
                tasks = listOf(Fixtures.task().copy(dueAtEpochMillis = 100)))
        val summary = WardSummary.from(listOf(patient, Fixtures.patient(id = "other")), "doc", 100)
        assertEquals(1, summary.mine)
        assertEquals(1, summary.urgent)
        assertEquals(1, summary.warnings)
        assertEquals(2, summary.unassigned)
        assertEquals(1, summary.newAdmissions)
        assertEquals(com.hos.rushdpatients.domain.task.TaskCounts(1, 1, 1), summary.tasks)
    }
}
