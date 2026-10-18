package com.hos.rushdpatients.ui.report

import android.net.Uri
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.sync.PatientFieldConflict

data class SupervisorReportTarget(
    val doctorId: String,
    val doctorName: String,
    val chatId: Long?,
    val patientCount: Int
)

enum class ReportRetryAction { SEND, PREVIEW, SAVE, SHARE }

data class ReportUiState(
    val loading: Boolean = true,
    val shift: Shift? = null,
    val isReadOnly: Boolean = false,
    val patients: List<Patient> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val availableDoctors: List<Doctor> = emptyList(),
    val savingDoctors: Boolean = false,
    val supervisorTargets: List<SupervisorReportTarget> = emptyList(),
    val summary: ReportSummary? = null,
    val previewMarkdown: String = "",
    val reportAsPdf: Boolean = false,
    val previewingPdf: Boolean = false,
    val exportingLocalPdf: Boolean = false,
    val sharingPdf: Boolean = false,
    val pdfPreviewUri: Uri? = null,
    val pdfShareUri: Uri? = null,
    val sending: Boolean = false,
    val readinessWarnings: List<String> = emptyList(),
    val changeBriefing: List<String> = emptyList(),
    val mergeConflicts: List<PatientFieldConflict> = emptyList(),
    val resolvingConflicts: Boolean = false,
    val lastOperation: String? = null,
    val retryAction: ReportRetryAction? = null,
    val error: String? = null,
    val snackbar: String? = null
)
