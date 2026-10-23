package com.hos.rushdpatients.sync

import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class PatientSyncMergeTest {
    private val patient = Fixtures.patient()
    @Test fun `null distinguishes deletion from a saved record`() {
        assertTrue(samePatientContent(null, null))
        assertFalse(samePatientContent(patient, null))
        assertFalse(samePatientContent(null, patient))
    }
    @Test fun `revision timestamp and local ordering are not clinical differences`() {
        assertTrue(samePatientContent(patient, patient.copy(revision = 99, sortOrder = 8,
            updatedAt = Fixtures.timestamp.plusSeconds(60))))
    }
    @Test fun `clinical edits identity deletion and attribution remain visible`() {
        listOf(patient.copy(id = "other"), patient.copy(name = "قهوة"), patient.copy(labs = "result"),
            patient.copy(treatmentPlan = "changed"), patient.copy(followUp = "changed"),
            patient.copy(tasks = listOf(Fixtures.task())), patient.copy(isPriority = true),
            patient.copy(responsibleResidentId = "doc"), patient.copy(responsibleSpecialistId = "supervisor"),
            patient.copy(deletedAt = Fixtures.timestamp), patient.copy(lastEditedByDoctorId = "doc")
        ).forEach { assertFalse("Missed change: $it", samePatientContent(patient, it)) }
    }
}
