package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PdfPatientContentTest {
    @Test fun `notes preserve followup labs and tasks but omit routine labels and last editor`() {
        val patient = Fixtures.patient(followUp = "متابعة كاملة", labs = "CBC synthetic").copy(
            tasks = listOf(Fixtures.task().copy(description = "مهمة كاملة")), lastEditedByName = "اسم المحرر")
        val notes = PdfPatientContent.buildNotesCell(patient)
        listOf(patient.followUp, patient.labs, "مهمة كاملة").forEach { assertTrue(notes.contains(it)) }
        listOf("غير معيّنة", "أولوية عادية", "اسم المحرر").forEach { assertFalse(notes.contains(it)) }
    }
    @Test fun `diagnosis retains category clinical text and every badge`() {
        val patient = Fixtures.patient(initialDiagnosis = "تشخيص كامل").copy(
            badges = listOf(PatientBadge("تنبيه أول"), PatientBadge("تنبيه ثان", PatientBadgePriority.HIGH)))
        val content = PdfPatientContent.buildDiagnosisCell(patient)
        listOf(patient.diagnosisType.arabicLabel, patient.initialDiagnosis, "تنبيه أول", "تنبيه ثان").forEach {
            assertTrue(content.contains(it))
        }
    }
    @Test fun `missing team lookup is omitted and resolved names retain both roles`() {
        val patient = Fixtures.patient(responsibleResidentId = "r", responsibleSpecialistId = "s")
        assertEquals("", PdfPatientContent.buildSupervisorResidentCell(patient, emptyMap(), emptyMap()))
        val cell = PdfPatientContent.buildSupervisorResidentCell(patient, mapOf("r" to "أرز"), mapOf("s" to "بلوط"))
        assertTrue(cell.indexOf("بلوط") < cell.indexOf("أرز"))
    }
}
