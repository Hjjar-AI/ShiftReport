package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class WardPatientFilteringTest {
    private fun filter(patients: List<com.hos.rushdpatients.data.model.Patient>,
        options: WardFilterOptions = WardFilterOptions(), pins: Set<String> = emptySet()) =
        filterWardPatients(patients, options, "doctor", 100, pins, wardSearchIndex(patients)).map { it.id }
    @Test fun `search covers all saved clinical text and task descriptions`() {
        val patients = listOf(Fixtures.patient().copy(tasks = listOf(Fixtures.task().copy(description = "تصوير تجريبي")), labs = "بوتاسيوم"))
        listOf("شاي", "تشخيص", "خطة", "متابعة", "بوتاسيوم", "تصوير", "1").forEach {
            assertEquals(listOf("p-1"), filter(patients, WardFilterOptions(searchQuery = it)))
        }
        assertTrue(filter(patients, WardFilterOptions(searchQuery = "لا يوجد")).isEmpty())
    }
    @Test fun `search normalizes Arabic diacritics and alef variants`() {
        assertEquals(listOf("p-1"), filter(listOf(Fixtures.patient(name = "أَرْز")), WardFilterOptions(searchQuery = "ارز")))
    }
    @Test fun `search cannot match across separate fields`() {
        assertTrue(filter(listOf(Fixtures.patient(name = "شاي", initialDiagnosis = "قهوة")),
            WardFilterOptions(searchQuery = "شاي قهوة")).isEmpty())
    }
    @Test fun `task filters distinguish pending overdue unassigned and completed`() {
        val patients = listOf(Fixtures.patient(id = "due").copy(tasks = listOf(Fixtures.task().copy(dueAtEpochMillis = 100))),
            Fixtures.patient(id = "future").copy(tasks = listOf(Fixtures.task().copy(ownerDoctorId = "doctor", dueAtEpochMillis = 101))),
            Fixtures.patient(id = "done").copy(tasks = listOf(Fixtures.task(done = true))), Fixtures.patient(id = "none"))
        assertEquals(listOf("due", "future"), filter(patients, WardFilterOptions(taskFilter = WardTaskFilter.PENDING)))
        assertEquals(listOf("due"), filter(patients, WardFilterOptions(taskFilter = WardTaskFilter.OVERDUE)))
        assertEquals(listOf("due"), filter(patients, WardFilterOptions(taskFilter = WardTaskFilter.UNASSIGNED)))
    }
    @Test fun `mine and priority filters intersect rather than expand results`() {
        val patients = listOf(Fixtures.patient(id = "mine", responsibleResidentId = "doctor").copy(isPriority = true),
            Fixtures.patient(id = "other").copy(isPriority = true), Fixtures.patient(id = "routine", responsibleSpecialistId = "doctor"))
        assertEquals(listOf("mine"), filter(patients, WardFilterOptions(viewMode = WardViewMode.MINE, priorityOnly = true)))
    }
    @Test fun `pinning preserves relative order and never bypasses filters`() {
        val patients = listOf(Fixtures.patient(id = "a"), Fixtures.patient(id = "b"), Fixtures.patient(id = "c"), Fixtures.patient(id = "d"))
        assertEquals(listOf("b", "d", "a", "c"), filter(patients, pins = setOf("d", "b", "missing")))
        assertTrue(filter(patients, WardFilterOptions(searchQuery = "missing"), setOf("b")).isEmpty())
        assertEquals(listOf("a", "b", "c", "d"), patients.map { it.id })
    }
}
