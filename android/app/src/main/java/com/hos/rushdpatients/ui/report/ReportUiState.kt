package com.hos.rushdpatients.ui.report

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import android.net.Uri
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.report.ReportSummary

data class SupervisorReportTarget(
    val doctorId: String,
    val doctorName: String,
    val chatId: Long?,
    val patientCount: Int
)

internal data class ReportDestinationReview(
    val includeGeneral: Boolean,
    val supervisors: List<SupervisorReportTarget>,
    val generalAsPdf: Boolean,
    val exportPdf: Boolean = false
)

enum class ReportStepOutcome(@StringRes private val arabicLabelRes: Int) {
    NOT_STARTED(R.string.ui_report_ui_state_001), IN_PROGRESS(R.string.ui_report_ui_state_002), CONFIRMED(R.string.ui_report_ui_state_003),
    PARTIAL(R.string.ui_report_ui_state_004), UNCERTAIN(R.string.ui_report_preview_screen_084);

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}

enum class ReportRetryAction { SEND, PREVIEW, SAVE, SAVE_SUPERVISORS, SHARE, ASSIGNMENTS }

data class ReportUiState(
    val loading: Boolean = true,
    val isAdmin: Boolean = false,
    val shift: Shift? = null,
    val patients: List<Patient> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val availableDoctors: List<Doctor> = emptyList(),
    val savingDoctors: Boolean = false,
    val fetchingDoctors: Boolean = false,
    val supervisorTargets: List<SupervisorReportTarget> = emptyList(),
    val summary: ReportSummary? = null,
    val previewMarkdown: String = "",
    val reportAsPdf: Boolean = true,
    val pdfStyle: com.hos.rushdpatients.pdf.PdfStyle = com.hos.rushdpatients.pdf.PdfStyle.CARDS,
    val pdfOptions: com.hos.rushdpatients.pdf.PdfExportOptions = com.hos.rushdpatients.pdf.PdfExportOptions(),
    val savingPdfOptions: Boolean = false,
    val previewingPdf: Boolean = false,
    val exportingLocalPdf: Boolean = false,
    val sharingPdf: Boolean = false,
    val exportUploadConfirmed: Boolean = false,
    val pdfPreviewUri: Uri? = null,
    val pdfReviewUris: Map<String, String> = emptyMap(),
    val openedPdfUris: Set<String> = emptySet(),
    val pdfReviewGeneration: Long = 0L,
    val pdfShareUri: Uri? = null,
    val sending: Boolean = false,
    val publicationWarning: String? = null,
    val publicationOutcome: ReportStepOutcome? = null,
    val deliveryRecords: List<ReportDeliveryRecord> = emptyList(),
    val deliveryOutcome: ReportStepOutcome? = null,
    val readinessWarnings: List<String> = emptyList(),
    val readinessIssues: List<com.hos.rushdpatients.domain.report.ReadinessIssue> = emptyList(),
    val changeBriefing: List<String> = emptyList(),
    val registryChangeSummary: String? = null,
    val lastOperation: String? = null,
    val retryAction: ReportRetryAction? = null,
    val error: String? = null,
    val reviewNotice: String? = null,
    val doctorRegistryAction: com.hos.rushdpatients.domain.doctor.DoctorRegistryAction? = null,
    val snackbar: String? = null
) {
    val isBusy: Boolean get() = loading || savingDoctors || fetchingDoctors || sending || previewingPdf ||
        exportingLocalPdf || sharingPdf || savingPdfOptions
}
