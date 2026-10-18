package com.hos.rushdpatients.domain.sort

import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingDetectorTest {

    private val doctors = mapOf(
        "d-1" to "د. أحمد",
        "d-2" to "د. سارة",
        "d-3" to "د. ليلى"
    )

    @Test
    fun `empty list is trivially grouped`() {
        assertTrue(GroupingDetector.isGroupedBySupervisor(emptyList(), doctors))
    }

    @Test
    fun `single patient is grouped`() {
        val patients = listOf(Fixtures.patient(id = "1", supervisorId = "d-1"))
        assertTrue(GroupingDetector.isGroupedBySupervisor(patients, doctors))
    }

    @Test
    fun `all same supervisor is grouped`() {
        val patients = listOf(
            Fixtures.patient(id = "1", supervisorId = "d-1"),
            Fixtures.patient(id = "2", supervisorId = "d-1"),
            Fixtures.patient(id = "3", supervisorId = "d-1")
        )
        assertTrue(GroupingDetector.isGroupedBySupervisor(patients, doctors))
    }

    @Test
    fun `contiguous groups are grouped`() {
        val patients = listOf(
            Fixtures.patient(id = "1", supervisorId = "d-1"),
            Fixtures.patient(id = "2", supervisorId = "d-1"),
            Fixtures.patient(id = "3", supervisorId = "d-2"),
            Fixtures.patient(id = "4", supervisorId = "d-2"),
            Fixtures.patient(id = "5", supervisorId = "d-3")
        )
        assertTrue(GroupingDetector.isGroupedBySupervisor(patients, doctors))
    }

    @Test
    fun `non-contiguous groups are not grouped`() {
        val patients = listOf(
            Fixtures.patient(id = "1", supervisorId = "d-1"),
            Fixtures.patient(id = "2", supervisorId = "d-2"),
            Fixtures.patient(id = "3", supervisorId = "d-1")
        )
        assertFalse(GroupingDetector.isGroupedBySupervisor(patients, doctors))
    }

    @Test
    fun `null supervisor becomes empty group`() {
        val patients = listOf(
            Fixtures.patient(id = "1", supervisorId = null),
            Fixtures.patient(id = "2", supervisorId = null)
        )
        assertTrue(GroupingDetector.isGroupedBySupervisor(patients, doctors))
    }
}