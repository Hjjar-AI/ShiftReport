package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PatientFormSubmissionTest {
    private val badges = listOf(PatientBadge("تحذير تجريبي", PatientBadgePriority.HIGH))
    private val tasks = listOf(Fixtures.task())
    private fun submit(initial: Patient?) = patientForSubmission(initial,
        " 42 ", LocalDate.of(2026, 1, 1), Gender.FEMALE, " قهوة ", LocalDate.of(1990, 1, 1),
        true, DiagnosisType.PSYCHIATRIC, " تشخيص ", " خطة\nثانية ", " متابعة ", " تحاليل ",
        "resident", "supervisor", badges, tasks, true)
    @Test fun `edit retains identity revision ordering and provenance`() {
        val initial = Fixtures.patient().copy(revision = 17, sortOrder = 9,
            lastEditedByDoctorId = "actor", lastEditedByName = "أرز")
        val saved = submit(initial)
        assertEquals(initial.id, saved.id)
        assertEquals(initial.revision, saved.revision)
        assertEquals(initial.updatedAt, saved.updatedAt)
        assertEquals(initial.sortOrder, saved.sortOrder)
        assertEquals(initial.lastEditedByDoctorId, saved.lastEditedByDoctorId)
        assertEquals(initial.lastEditedByName, saved.lastEditedByName)
        assertEquals("شاي", initial.name)
    }
    @Test fun `submission carries every editable clinical and demographic field`() {
        val saved = submit(null)
        assertEquals("42", saved.admittanceNumber)
        assertEquals(LocalDate.of(2026, 1, 1), saved.admittanceDate)
        assertEquals("قهوة", saved.name)
        assertEquals(Gender.FEMALE, saved.gender)
        assertEquals(LocalDate.of(1990, 1, 1), saved.birthDate)
        assertEquals(DiagnosisType.PSYCHIATRIC, saved.diagnosisType)
        assertEquals("تشخيص", saved.initialDiagnosis)
        assertEquals("خطة\nثانية", saved.treatmentPlan)
        assertEquals("متابعة", saved.followUp)
        assertEquals("تحاليل", saved.labs)
        assertEquals("resident", saved.responsibleResidentId)
        assertEquals("supervisor", saved.responsibleSpecialistId)
        assertEquals(tasks, saved.tasks)
        assertEquals(badges, saved.badges)
        assertTrue(saved.hasCompanion)
        assertTrue(saved.isPriority)
    }
    @Test fun `new submissions receive separate identities and no editor attribution`() {
        val first = submit(null)
        assertNotEquals(first.id, submit(null).id)
        assertEquals(0L, first.revision)
        assertNull(first.lastEditedByDoctorId)
        assertNull(first.deletedAt)
    }
    @Test fun `cleared optional fields replace prior saved values`() {
        val saved = patientForSubmission(submit(null), "1", null, Gender.MALE, "شاي", null,
            false, DiagnosisType.PSYCHIATRIC, "تشخيص", "خطة", "متابعة", "", null, null,
            emptyList(), emptyList(), false)
        assertNull(saved.birthDate)
        assertNull(saved.admittanceDate)
        assertNull(saved.responsibleResidentId)
        assertNull(saved.responsibleSpecialistId)
        assertTrue(saved.tasks.isEmpty())
        assertTrue(saved.badges.isEmpty())
        assertEquals("", saved.labs)
        assertFalse(saved.isPriority)
    }
}
