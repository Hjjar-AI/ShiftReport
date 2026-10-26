package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class PatientDoctorAssignmentsTest {
    private val resident = Fixtures.doctor(id = "r")
    private val supervisor = Fixtures.doctor(id = "s").copy(clinicalRole = ClinicalRole.SUPERVISOR)
    private val registry = listOf(resident, supervisor).associateBy { it.id }
    @Test fun `optional assignments and correctly typed active assignments are accepted`() {
        PatientDoctorAssignments.requireValid(Fixtures.patient(), emptyMap())
        PatientDoctorAssignments.requireValid(Fixtures.patient(responsibleResidentId = "r", responsibleSpecialistId = "s"), registry)
    }
    @Test fun `wrong missing and deleted responsible doctors are rejected`() {
        for (patient in listOf(Fixtures.patient(responsibleResidentId = "s"), Fixtures.patient(responsibleSpecialistId = "r"),
            Fixtures.patient(responsibleResidentId = "missing"))) {
            assertThrows(IllegalArgumentException::class.java) { PatientDoctorAssignments.requireValid(patient, registry) }
        }
        assertThrows(IllegalArgumentException::class.java) { PatientDoctorAssignments.requireValid(
            Fixtures.patient(responsibleResidentId = "r"), registry + ("r" to resident.copy(deletedAt = Fixtures.timestamp))) }
    }
    @Test fun `legacy task ownership does not constrain patient assignment validation`() {
        val pending = Fixtures.patient().copy(tasks = listOf(Fixtures.task().copy(ownerDoctorId = "missing")))
        PatientDoctorAssignments.requireValid(pending, registry)
        PatientDoctorAssignments.requireValid(pending.copy(tasks = listOf(Fixtures.task(done = true).copy(ownerDoctorId = "missing"))), registry)
    }
}
