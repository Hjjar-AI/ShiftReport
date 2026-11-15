package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReportFlowGateTest {
    private val shift = Fixtures.shift()
    private val ready = ReportUiState(loading = false, shift = shift, doctors = listOf(Fixtures.doctor()),
        summary = com.hos.rushdpatients.domain.report.ReportSummary(0, 0, 0, shift.date, listOf(Fixtures.doctor())),
        previewMarkdown = "reviewed", reportAsPdf = false)
    private fun gate(state: ReportUiState = ready, text: String? = "reviewed", opened: Set<String> = emptySet(),
        supervisor: Boolean = false, targets: List<SupervisorReportTarget> = emptyList()) =
        reportFlowGate(state, ReportFlowStep.PREVIEW, supervisor, targets, text, opened)
    @Test fun `text review must match current preview before sending`() {
        assertNull(gate().sendDisabledReason)
        assertNotNull(gate(text = "stale").sendDisabledReason)
        assertNotNull(gate(text = null).sendDisabledReason)
        assertNotNull(gate(ready.copy(summary = null)).sendDisabledReason)
    }
    @Test fun `PDF viewing is optional while current artifact readiness remains visible`() {
        val pdf = ready.copy(reportAsPdf = true, pdfReviewUris = mapOf("general" to "current"))
        assertTrue(gate(pdf, opened = setOf("old")).previewReady)
        assertEquals(false, gate(pdf, opened = setOf("old")).previewReadiness["general"])
        assertEquals(true, gate(pdf, opened = setOf("current")).previewReadiness["general"])
        assertNull(gate(pdf, opened = setOf("current")).sendDisabledReason)
    }
    @Test fun `supervisor PDF viewing is optional and each opened artifact is tracked separately`() {
        val targets = listOf(SupervisorReportTarget("a", "أرز", -100, 1), SupervisorReportTarget("b", "بلوط", -200, 1))
        val state = ready.copy(pdfReviewUris = mapOf("supervisor:a" to "a.pdf", "supervisor:b" to "b.pdf"))
        assertNotNull(gate(state, supervisor = true).sendDisabledReason)
        assertTrue(gate(state, supervisor = true, targets = targets, opened = setOf("a.pdf")).previewReady)
        assertEquals(mapOf("supervisor:a" to true, "supervisor:b" to false),
            gate(state, supervisor = true, targets = targets, opened = setOf("a.pdf")).previewReadiness)
        assertNull(gate(state, supervisor = true, targets = targets, opened = setOf("a.pdf", "b.pdf")).sendDisabledReason)
    }
    @Test fun `all in-progress operations prevent send and progression`() {
        listOf(ready.copy(loading = true), ready.copy(savingDoctors = true), ready.copy(sending = true),
            ready.copy(previewingPdf = true), ready.copy(exportingLocalPdf = true), ready.copy(sharingPdf = true),
            ready.copy(savingPdfOptions = true)).forEach {
            assertTrue(gate(it).busy)
            assertNotNull(gate(it).sendDisabledReason)
            assertNotNull(gate(it).nextDisabledReason)
        }
    }
}
