package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DoctorImportPlanTest {
    @Test fun `preview distinguishes clinical changes additions and omitted retained doctors`() {
        val old = Fixtures.doctor(id = "a", fullName = "أرز")
        val retained = Fixtures.doctor(id = "b", fullName = "بلوط")
        val added = Fixtures.doctor(id = "c", fullName = "صنوبر")
        val unchanged = old.copy(updatedAt = Instant.EPOCH, extraOptions = setOf("pin:synthetic", "syncAlias:old"))
        val plan = DoctorImportPlan(listOf(unchanged, added), listOf(old, retained), "a", listOf(unchanged, added))
        assertEquals(DoctorImportChanges(listOf("صنوبر"), emptyList(), listOf("أرز"), listOf("بلوط")), plan.changes())
        val changed = old.copy(clinicalRole = ClinicalRole.SUPERVISOR)
        assertEquals(listOf("أرز"), plan.copy(reviewedChanges = listOf(changed)).changes().changed)
    }
}
