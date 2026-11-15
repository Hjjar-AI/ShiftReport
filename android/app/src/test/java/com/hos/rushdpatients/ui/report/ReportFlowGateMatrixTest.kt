package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Destination/review policy crossed with each independent busy flag and both report steps. */
@RunWith(Parameterized::class)
internal class ReportFlowGateMatrixTest(
    private val caseName: String,
    private val mode: Mode,
    private val operation: String,
    private val summaryPresent: Boolean,
    private val step: ReportFlowStep
) {
    data class Mode(val label: String, val general: Boolean, val supervisors: Boolean,
        val pdf: Boolean, val textReviewed: Boolean, val ready: Boolean, val destination: Boolean = true)
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any>> {
            val modes = listOf(
                Mode("reviewed-text", true, false, false, true, true),
                Mode("unreviewed-text", true, false, false, false, false),
                Mode("unopened-pdf", true, false, true, false, true),
                Mode("supervisors-only", false, true, true, false, true),
                Mode("combined-pdf", true, true, true, false, true),
                Mode("combined-unreviewed-text", true, true, false, false, false),
                Mode("no-destination", false, false, true, false, false, false)
            )
            val operations = listOf("idle", "loading", "savingDoctors", "fetchingDoctors", "sending",
                "previewingPdf", "exportingLocalPdf", "sharingPdf", "savingPdfOptions")
            return buildList {
                for (mode in modes) for (operation in operations) for (summary in listOf(false, true))
                    for (step in ReportFlowStep.values()) add(arrayOf<Any>(
                        "${mode.label}/$operation/summary=$summary/$step", mode, operation, summary, step))
            }
        }
    }
    @Test fun destinationAndReviewCannotBypassBusyOrMissingSummaryGuards() {
        val shift = Fixtures.shift()
        val doctors = listOf(Fixtures.doctor())
        val state = ReportUiState(
            loading = operation == "loading", shift = shift, doctors = doctors,
            summary = if (summaryPresent) ReportSummary(0, 0, 0, shift.date, doctors) else null,
            reportAsPdf = mode.pdf, previewMarkdown = "reviewed text",
            savingDoctors = operation == "savingDoctors", fetchingDoctors = operation == "fetchingDoctors",
            sending = operation == "sending", previewingPdf = operation == "previewingPdf",
            exportingLocalPdf = operation == "exportingLocalPdf", sharingPdf = operation == "sharingPdf",
            savingPdfOptions = operation == "savingPdfOptions"
        )
        val targets = if (mode.supervisors) listOf(SupervisorReportTarget("cedar", "أرز", -100, 0),
            SupervisorReportTarget("oak", "بلوط", -200, 0)) else emptyList()
        val gate = reportFlowGate(state, step, mode.supervisors, targets,
            if (mode.textReviewed) "reviewed text" else null, emptySet(), includeGeneral = mode.general)
        val busy = operation != "idle"
        assertEquals(caseName, busy, gate.busy)
        assertEquals(caseName, mode.ready, gate.previewReady)
        assertEquals(caseName, summaryPresent && mode.destination, gate.destinationReady)
        val maySend = !busy && summaryPresent && mode.destination && mode.ready
        val mayAdvance = !busy && summaryPresent && mode.destination &&
            (step == ReportFlowStep.DOCTORS || mode.ready)
        assertEquals(caseName, maySend, gate.sendDisabledReason == null)
        assertEquals(caseName, mayAdvance, gate.nextDisabledReason == null)
        assertEquals(caseName, (if (mode.general) setOf("general") else emptySet()) +
            (if (mode.supervisors) setOf("supervisor:cedar", "supervisor:oak") else emptySet()), gate.previewReadiness.keys)
        assertTrue(caseName, gate.previewReadiness.filterKeys { it.startsWith("supervisor:") }.values.none { it })
    }
}
