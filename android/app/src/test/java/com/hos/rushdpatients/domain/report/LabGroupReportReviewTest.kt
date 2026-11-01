package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.network.telegram.Markdown
import org.junit.Assert.*
import org.junit.Test

class LabGroupReportReviewTest {
    private val shift = Fixtures.shift()
    private val doctor = Fixtures.doctor()
    private fun report(patient: Patient) = BuiltReport(shift, listOf(patient), listOf(doctor),
        mapOf(doctor.id to doctor.fullName), ReportSummary(1, 1, 0, shift.date, listOf(doctor)), listOf("reviewed dummy text"))

    @Test fun `title date result and order edits each invalidate a reviewed artifact`() {
        val patient = Fixtures.patientWithLabs()
        val before = report(patient)
        listOf(patient.labGroups.reversed(),
            patient.labGroups.map { it.copy(title = it.title + " جديد") },
            patient.labGroups.map { it.copy(date = "2026-11-02") },
            patient.labGroups.map { it.copy(results = it.results + "\nnew result") }).forEach { groups ->
            val changed = report(patient.copy(labGroups = groups, labs = PatientLabGroups.text(groups)))
            assertThrows(ReportChangedSinceReviewException::class.java) { ReportReview.requireMatches(before, changed) }
        }
    }
    @Test fun `different group boundaries invalidate review even if readable text is identical`() {
        val one = listOf(PatientLabGroup("whole", results = "Na=140\n\nK=4.1"))
        val two = listOf(PatientLabGroup("na", results = "Na=140"), PatientLabGroup("k", results = "K=4.1"))
        assertEquals(PatientLabGroups.text(one), PatientLabGroups.text(two))
        assertThrows(ReportChangedSinceReviewException::class.java) {
            ReportReview.requireMatches(report(Fixtures.patientWithLabs(one)), report(Fixtures.patientWithLabs(two)))
        }
    }
    @Test fun `text exports contain every date heading and escaped result in user order`() {
        val groups = Fixtures.labGroups() + PatientLabGroup("escaped", "تحليل [خاص]", "2026-01-01", "A_* = 12.3, \"µL\"")
        val patient = Fixtures.patientWithLabs(groups)
        val text = TextReportBuilder().build(shift, listOf(patient), listOf(doctor),
            ReportSummary(1, 1, 0, shift.date, listOf(doctor)), emptyMap(), emptyMap()).joinToString("\n")
        val expected = Markdown.escape(patient.labText)
        assertTrue(text.contains(expected))
        assertTrue(text.indexOf(Markdown.escape(PatientLabGroups.heading(groups.first()))) <
            text.indexOf(Markdown.escape(PatientLabGroups.heading(groups.last()))))
    }
}
