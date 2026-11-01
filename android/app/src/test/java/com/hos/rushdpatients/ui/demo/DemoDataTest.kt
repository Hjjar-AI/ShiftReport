package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

import com.hos.rushdpatients.domain.patient.PatientValidator
import com.hos.rushdpatients.domain.patient.PatientValidationResult
import com.hos.rushdpatients.domain.task.PatientTasks

class DemoDataTest {
    @Test fun `synthetic demo patients obey clinical and task rules with resolvable teams`() {
        val doctors = DemoData.doctors()
        val patients = DemoData.patients(doctors)
        assertEquals(doctors.size, doctors.map { it.id }.distinct().size)
        assertEquals(patients.size, patients.map { it.id }.distinct().size)
        patients.forEach { patient ->
            assertEquals(PatientValidationResult.Valid, PatientValidator.validate(patient))
            PatientTasks.requireValid(patient.tasks)
            patient.responsibleResidentId?.let { id -> assertEquals(ClinicalRole.RESIDENT, doctors.single { it.id == id }.clinicalRole) }
            patient.responsibleSpecialistId?.let { id -> assertEquals(ClinicalRole.SUPERVISOR, doctors.single { it.id == id }.clinicalRole) }
        }
    }
    @Test fun `new in-memory repositories have independent session state`() {
        val a = InMemoryDemoWardRepository()
        val b = InMemoryDemoWardRepository()
        assertNotSame(a.state, b.state)
    }
    @Test fun `sample teams are balanced and every resident belongs to its matching supervisor`() {
        val doctors = DemoData.doctors()
        com.hos.rushdpatients.domain.doctor.DoctorTeams.validate(doctors)
        val patients = DemoData.patients(doctors)
        assertEquals(50, patients.size)
        val sizes = patients.groupingBy { it.responsibleSpecialistId }.eachCount()
        assertEquals(listOf(16, 17, 17), sizes.values.sorted())
        assertFalse(sizes.containsKey(null))
        patients.forEach { patient ->
            val resident = doctors.single { it.id == patient.responsibleResidentId }
            assertEquals(patient.responsibleSpecialistId, resident.teamSupervisorId)
        }
        assertEquals(138, patients.sumOf { it.labGroups.size })
        val groups = patients.flatMap { it.labGroups }
        assertEquals(104, groups.count { it.date != null })
        assertEquals(34, groups.count { it.date == null })
        assertEquals(setOf(java.time.LocalDate.of(2026, 10, 26), java.time.LocalDate.of(2026, 10, 27)),
            groups.mapNotNull { it.date }.toSet())
        assertTrue(groups.all { it.title.isNotBlank() && it.results.isNotBlank() })
    }

}
