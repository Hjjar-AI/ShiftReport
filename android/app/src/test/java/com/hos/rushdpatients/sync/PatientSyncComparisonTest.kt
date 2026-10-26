package com.hos.rushdpatients.sync

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientSyncComparisonTest {
    @Test fun `snapshot comparison ignores list order timestamps and local revisions`() {
        val a = Fixtures.patient(id = "a")
        val b = Fixtures.patient(id = "b")
        assertTrue(samePublishedPatients(listOf(a, b), listOf(b.copy(revision = 99), a.copy(updatedAt = Instant.EPOCH))))
        assertFalse(samePublishedPatients(listOf(a), listOf(a.copy(sortOrder = 2))))
        assertFalse(samePublishedPatients(listOf(a), emptyList()))
    }
    @Test fun `clinical comparison ignores position but preserves tasks badges and deletion`() {
        val a = Fixtures.patient()
        assertTrue(samePatientContent(a, a.copy(sortOrder = 99, revision = 99, updatedAt = Instant.EPOCH)))
        assertTrue(samePatientContent(null, null))
        assertFalse(samePatientContent(a, null))
        listOf(a.copy(tasks = listOf(Fixtures.task())), a.copy(badges = listOf(PatientBadge("تنبيه"))),
            a.copy(deletedAt = Instant.EPOCH), a.copy(labs = "changed")).forEach {
            assertFalse(samePatientContent(a, it))
        }
    }
}
