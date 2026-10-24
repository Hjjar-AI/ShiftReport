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
    selectedTargets: List<SupervisorReportTarget>, reviewedText: String?, openedPdfUris: Set<String>,
    includeGeneral: Boolean = !supervisorMode
): ReportFlowGate {
    val busy = state.loading || state.savingDoctors || state.sending || state.previewingPdf ||
        state.exportingLocalPdf || state.sharingPdf || state.savingPdfOptions
    val targets = if (supervisorMode) selectedTargets else emptyList()
    val hasDestination = includeGeneral || targets.isNotEmpty()
    val generalReady = !includeGeneral || if (state.reportAsPdf)
        state.pdfReviewUris["general"]?.let { it in openedPdfUris } == true
        else reviewedText == state.previewMarkdown
    val supervisorsReady = targets.all { target ->
        state.pdfReviewUris["supervisor:${target.doctorId}"]?.let { it in openedPdfUris } == true
    }
    val previewReady = hasDestination && generalReady && supervisorsReady
    val destinationReady = state.summary != null && hasDestination
    val nextDisabledReason = when {
        busy -> R.string.ui_report_preview_screen_001
        step == ReportFlowStep.DOCTORS && state.shift == null -> R.string.ui_report_preview_screen_002
        step == ReportFlowStep.DOCTORS && state.doctors.isEmpty() -> R.string.ui_report_preview_screen_003
        !destinationReady ->
            if (!hasDestination) R.string.ui_report_no_destination
            else R.string.ui_report_preview_screen_005
        step == ReportFlowStep.PREVIEW && !previewReady ->
            R.string.ui_report_preview_each_selected
        else -> null
    }
    val sendDisabledReason = when {
        busy -> R.string.ui_report_preview_screen_001
        state.isReadOnly -> R.string.ui_report_preview_screen_008
        state.summary == null -> if (state.doctors.isEmpty()) R.string.ui_report_preview_screen_003 else R.string.ui_report_preview_screen_009
        !hasDestination -> R.string.ui_report_no_destination
        !previewReady -> R.string.ui_report_preview_each_selected
        else -> null
    }
    return ReportFlowGate(busy, previewReady, destinationReady, nextDisabledReason, sendDisabledReason)
}
