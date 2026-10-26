package com.hos.rushdpatients.sync

import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class PatientSyncMergeTest {
    private fun sameClinicalContent(left: com.hos.rushdpatients.data.model.Patient?, right: com.hos.rushdpatients.data.model.Patient?) =
        left?.let(KnownShiftSnapshot::patientContent) == right?.let(KnownShiftSnapshot::patientContent)

    private val patient = Fixtures.patient()
    @Test fun `null distinguishes deletion from a saved record`() {
        assertTrue(sameClinicalContent(null, null))
        assertFalse(sameClinicalContent(patient, null))
        assertFalse(sameClinicalContent(null, patient))
    }
    @Test fun `revision timestamp and local ordering are not clinical differences`() {
        assertTrue(sameClinicalContent(patient, patient.copy(revision = 99, sortOrder = 8,
            updatedAt = Fixtures.timestamp.plusSeconds(60))))
    }
    @Test fun `clinical edits identity and deletion remain visible while attribution is ignored`() {
        listOf(patient.copy(id = "other"), patient.copy(name = "قهوة"), patient.copy(labs = "result"),
            patient.copy(treatmentPlan = "changed"), patient.copy(followUp = "changed"),
            patient.copy(tasks = listOf(Fixtures.task())), patient.copy(isPriority = true),
            patient.copy(responsibleResidentId = "doc"), patient.copy(responsibleSpecialistId = "supervisor"),
            patient.copy(deletedAt = Fixtures.timestamp)
        ).forEach { assertFalse("Missed change: $it", sameClinicalContent(patient, it)) }
        assertTrue(sameClinicalContent(patient, patient.copy(lastEditedByDoctorId = "doc")))
    }
}
