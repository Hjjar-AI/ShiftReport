package com.hos.rushdpatients.domain.sort

import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingDetectorTest {

    @Test
    fun `empty list is trivially grouped`() {
        assertTrue(GroupingDetector.isGroupedBySupervisor(emptyList()))
    }

    @Test
    fun `single patient is grouped`() {
        val patients = listOf(Fixtures.patient(id = "1", responsibleSpecialistId = "d-1"))
        assertTrue(GroupingDetector.isGroupedBySupervisor(patients))
    }

    @Test
    fun `all same supervisor is grouped`() {
        val patients = listOf(
            Fixtures.patient(id = "1", responsibleSpecialistId = "d-1"),
            Fixtures.patient(id = "2", responsibleSpecialistId = "d-1"),
            Fixtures.patient(id = "3", responsibleSpecialistId = "d-1")
        )
        assertTrue(GroupingDetector.isGroupedBySupervisor(patients))
    }

    @Test
    fun `contiguous groups are grouped`() {
        val patients = listOf(
            Fixtures.patient(id = "1", responsibleSpecialistId = "d-1"),
            Fixtures.patient(id = "2", responsibleSpecialistId = "d-1"),
            Fixtures.patient(id = "3", responsibleSpecialistId = "d-2"),
            Fixtures.patient(id = "4", responsibleSpecialistId = "d-2"),
            Fixtures.patient(id = "5", responsibleSpecialistId = "d-3")
        )
        assertTrue(GroupingDetector.isGroupedBySupervisor(patients))
    }

    @Test
    fun `non-contiguous groups are not grouped`() {
        val patients = listOf(
            Fixtures.patient(id = "1", responsibleSpecialistId = "d-1"),
            Fixtures.patient(id = "2", responsibleSpecialistId = "d-2"),
            Fixtures.patient(id = "3", responsibleSpecialistId = "d-1")
        )
        assertFalse(GroupingDetector.isGroupedBySupervisor(patients))
    }

    @Test
    fun `null supervisor becomes empty group`() {
        val patients = listOf(
            Fixtures.patient(id = "1", responsibleSpecialistId = null),
            Fixtures.patient(id = "2", responsibleSpecialistId = null)
        )
        assertTrue(GroupingDetector.isGroupedBySupervisor(patients))
    }
}
