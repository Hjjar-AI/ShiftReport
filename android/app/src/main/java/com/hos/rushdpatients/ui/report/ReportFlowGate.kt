package com.hos.rushdpatients.ui.report

import androidx.annotation.StringRes
import com.hos.rushdpatients.R

/** Presentation readiness only; the ViewModel revalidates authorization, revisions and delivery. */
internal data class ReportFlowGate(
    val busy: Boolean, val previewReady: Boolean, val destinationReady: Boolean,
    @StringRes val nextDisabledReason: Int?, @StringRes val sendDisabledReason: Int?
)

internal fun reportFlowGate(
    state: ReportUiState, step: ReportFlowStep, supervisorMode: Boolean,
    selectedTargets: List<SupervisorReportTarget>, reviewedText: String?, openedPdfUris: Set<String>
): ReportFlowGate {
    val busy = state.loading || state.savingDoctors || state.sending || state.previewingPdf ||
        state.exportingLocalPdf || state.sharingPdf || state.savingPdfOptions
    val pdfSelected = supervisorMode || state.reportAsPdf
    val previewReady = if (!pdfSelected) reviewedText == state.previewMarkdown
        else if (supervisorMode) selectedTargets.isNotEmpty() && selectedTargets.all { target ->
            state.pdfReviewUris["supervisor:${target.doctorId}"]?.let { it in openedPdfUris } == true
        }
        else state.pdfReviewUris["general"]?.let { it in openedPdfUris } == true
    val destinationReady = state.summary != null && (!supervisorMode || selectedTargets.isNotEmpty())
    val nextDisabledReason = when {
        busy -> R.string.ui_report_preview_screen_001
        step == ReportFlowStep.DOCTORS && state.shift == null -> R.string.ui_report_preview_screen_002
        step == ReportFlowStep.DOCTORS && state.doctors.isEmpty() -> R.string.ui_report_preview_screen_003
        step != ReportFlowStep.DOCTORS && !destinationReady ->
            if (supervisorMode && selectedTargets.isEmpty()) R.string.ui_report_preview_screen_004
            else R.string.ui_report_preview_screen_005
        step == ReportFlowStep.PREVIEW && !previewReady ->
            if (supervisorMode) R.string.ui_report_preview_screen_006 else R.string.ui_report_preview_screen_007
        else -> null
    }
    val sendDisabledReason = when {
        busy -> R.string.ui_report_preview_screen_001
        state.isReadOnly -> R.string.ui_report_preview_screen_008
        state.summary == null -> if (state.doctors.isEmpty()) R.string.ui_report_preview_screen_003 else R.string.ui_report_preview_screen_009
        supervisorMode && selectedTargets.isEmpty() -> R.string.ui_report_preview_screen_004
        !previewReady -> if (supervisorMode) R.string.ui_report_preview_screen_010
            else R.string.ui_report_preview_screen_011
        else -> null
    }
    return ReportFlowGate(busy, previewReady, destinationReady, nextDisabledReason, sendDisabledReason)
}
