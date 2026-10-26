package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.sort.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WardPatientOrderingTest {
    private val text = mockk<UiStrings> {
        every { this@mockk.invoke(any(), *anyVararg()) } answers { firstArg<Int>().toString() }
    }
    private val doctors = listOf(Fixtures.doctor("a", fullName = "A"), Fixtures.doctor("b", fullName = "B"))
    @Test fun noGroupingReturnsNoSupplementaryGroups() {
        assertTrue(groupPatients(text, listOf(Fixtures.patient()), doctors, GroupByMode.NONE).isEmpty())
    }
    @Test fun everyGroupingModeHandlesEmptyWard() {
        for (mode in GroupByMode.entries) assertTrue(groupPatients(text, emptyList(), doctors, mode).isEmpty())
    }
    @Test fun supervisorGroupsUseRegistryNamesAndPutUnassignedLast() {
        val patients = listOf(Fixtures.patient("none"), Fixtures.patient("b", responsibleSpecialistId = "b"),
            Fixtures.patient("a", responsibleSpecialistId = "a"))
        val groups = groupPatients(text, patients, doctors, GroupByMode.SUPERVISOR)
        assertEquals(listOf("a", "b", null), groups.map { it.key })
        assertEquals(listOf("A", "B"), groups.take(2).map { it.name })
        assertEquals(patients.first(), groups.last().patients.single())
    }
    @Test fun residentGroupingUsesResidentRatherThanSupervisor() {
        val patient = Fixtures.patient(responsibleResidentId = "a", responsibleSpecialistId = "b")
        val group = groupPatients(text, listOf(patient), doctors, GroupByMode.RESIDENT).single()
        assertEquals("a", group.key)
        assertEquals("A", group.name)
    }
    @Test fun unknownDoctorReferencesRemainSeparateAndKeepPatients() {
        val patients = listOf(Fixtures.patient("a", responsibleResidentId = "missing-a"),
            Fixtures.patient("b", responsibleResidentId = "missing-b"), Fixtures.patient("none"))
        val groups = groupPatients(text, patients, emptyList(), GroupByMode.RESIDENT)
        assertEquals(setOf("missing-a", "missing-b", null), groups.map { it.key }.toSet())
        assertEquals(patients.toSet(), groups.flatMap { it.patients }.toSet())
        assertNull(groups.last().key)
    }
    @Test fun admissionDateGroupsAreNewestFirstWithUndatedLast() {
        val patients = listOf(Fixtures.patient("none", admittanceDate = null),
            Fixtures.patient("old", admittanceDate = LocalDate.of(2025, 12, 31)),
            Fixtures.patient("new", admittanceDate = LocalDate.of(2026, 1, 1)))
        val groups = groupPatients(text, patients, doctors, GroupByMode.ADMISSION_DATE)
        assertEquals(listOf("2026-01-01", "2025-12-31", null), groups.map { it.key })
    }
    @Test fun groupingPreservesExistingOrderWithinEachSection() {
        val patients = listOf(Fixtures.patient("z", responsibleSpecialistId = "a"),
            Fixtures.patient("x", responsibleSpecialistId = "b"), Fixtures.patient("a", responsibleSpecialistId = "a"))
        val groups = groupPatients(text, patients, doctors, GroupByMode.SUPERVISOR)
        assertEquals(listOf("z", "a"), groups.first().patients.map { it.id })
    }
    @Test fun allGroupingModesPartitionEveryRecordExactlyOnceWithoutDroppingClinicalFields() {
        val patients = (0 until 36).map { index -> Fixtures.patient("p-$index",
            gender = Gender.entries[index % Gender.entries.size],
            diagnosisType = DiagnosisType.entries[index % DiagnosisType.entries.size],
            responsibleSpecialistId = listOf(null, "a", "b", "missing")[index % 4],
            responsibleResidentId = listOf(null, "b", "a")[index % 3],
            labs = "نتيجة $index", followUp = "متابعة $index") }
        for (mode in GroupByMode.entries.filterNot { it == GroupByMode.NONE }) {
            val flattened = groupPatients(text, patients, doctors, mode).flatMap { it.patients }
            assertEquals(mode.name, patients.size, flattened.size)
            assertEquals(mode.name, patients.toSet(), flattened.toSet())
        }
    }
    @Test fun manualOrderRenumbersCopiesWithoutMutatingInput() {
        val patients = listOf(Fixtures.patient("b", sortOrder = 20), Fixtures.patient("a", sortOrder = 10))
        val ordered = sortPatients(patients, doctors, SortSpec.EMPTY)
        assertEquals(listOf("a", "b"), ordered.map { it.id })
        assertEquals(listOf(1, 2), ordered.map { it.sortOrder })
        assertEquals(listOf(20, 10), patients.map { it.sortOrder })
    }
    @Test fun priorityStaysFirstEvenWhenNameSortIsDescending() {
        val priority = Fixtures.patient("p", name = "A").copy(isPriority = true)
        val other = Fixtures.patient("q", name = "Z")
        val sorted = sortPatients(listOf(other, priority), doctors, SortSpec(listOf(SortLevel(SortField.NAME, SortDirection.DESC))))
        assertEquals(listOf("p", "q"), sorted.map { it.id })
    }
    @Test fun supervisorSortUsesNamesRatherThanIdentifierOrder() {
        val registry = listOf(Fixtures.doctor("z", fullName = "A"), Fixtures.doctor("a", fullName = "Z"))
        val patients = listOf(Fixtures.patient("last", responsibleSpecialistId = "a"),
            Fixtures.patient("first", responsibleSpecialistId = "z"))
        assertEquals(listOf("first", "last"), sortPatients(patients, registry,
            SortSpec(listOf(SortLevel(SortField.SUPERVISOR)))).map { it.id })
    }
}
