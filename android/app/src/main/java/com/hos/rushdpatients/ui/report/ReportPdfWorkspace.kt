package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import com.hos.rushdpatients.config.ProjectConfigStore
import android.content.Context
import androidx.lifecycle.ViewModel
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.report.BuiltReport
import com.hos.rushdpatients.domain.report.ReportReview
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.pdf.MediaStoreSaver
import com.hos.rushdpatients.pdf.PdfCardsExporter
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfRuleWeight
import com.hos.rushdpatients.pdf.PdfRuleContrast
import com.hos.rushdpatients.pdf.PdfRowShading
import com.hos.rushdpatients.pdf.PdfExportOptions
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfReportExporter
import com.hos.rushdpatients.pdf.PdfStyle
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class ReviewedPdf(val file: File, val digest: String, val reportKey: String, val options: PdfExportOptions, val style: PdfStyle, val reviewed: BuiltReport)
internal class PdfReviewRequiredException(uiText: UiStrings) : IllegalStateException(uiText(R.string.ui_report_pdf_workspace_003))

/** One reviewed-artifact store for this ViewModel; publication/delivery stay with its owner. */
internal class ReportPdfWorkspace(
    private val uiText: UiStrings,
    private val context: Context, private val settingsRepository: SettingsRepository,
    private val projectConfigStore: ProjectConfigStore, private val classicPdfExporter: PdfReportExporter,
    private val cardsPdfExporter: PdfCardsExporter, private val mediaStoreSaver: MediaStoreSaver
) {
    private val reviewedPdfs = java.util.concurrent.ConcurrentHashMap<String, ReviewedPdf>()

    @Synchronized
    fun clearReviews() {
        val files = reviewedPdfs.values.map { it.file }
        reviewedPdfs.clear()
        com.hos.rushdpatients.pdf.ClinicalPdfCache.delete(files)
    }

    @Synchronized
    fun removeReview(key: String) {
        reviewedPdfs.remove(key)?.let { com.hos.rushdpatients.pdf.ClinicalPdfCache.delete(listOf(it.file)) }
    }

    @Synchronized
    fun replaceReview(key: String, artifact: ReviewedPdf) {
        reviewedPdfs.put(key, artifact)?.let { com.hos.rushdpatients.pdf.ClinicalPdfCache.delete(listOf(it.file)) }
    }

    fun exportKey(route: String, draft: Boolean) = if (draft) "draft:$route" else route

    fun pdfKey(supervisorId: String?) = supervisorId?.let { "supervisor:$it" } ?: "general"

    fun pdfDigest(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    suspend fun checkedPdf(built: BuiltReport, supervisorId: String? = null): File =
        checkedExportPdf(built, pdfKey(supervisorId), draft = false)

    suspend fun checkedExportPdf(built: BuiltReport, route: String, draft: Boolean): File = withContext(Dispatchers.IO) {
        val artifact = reviewedPdfs[exportKey(route, draft)] ?: throw PdfReviewRequiredException(uiText)
        ReportReview.requireMatches(artifact.reviewed, built)
        if (artifact.style != PdfStyle.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_STYLE)) ||
            artifact.reportKey != ReportReview.exportFingerprint(built) ||
            artifact.options.copy(generatedAtEpochMillis = 0) != loadPdfOptions().copy(generatedAtEpochMillis = 0) ||
            !artifact.file.isFile || pdfDigest(artifact.file) != artifact.digest) throw PdfReviewRequiredException(uiText)
        artifact.file
    }

    fun supervisorPdfReport(built: BuiltReport, supervisorId: String): BuiltReport {
        val supervisor = built.registryDoctors.firstOrNull { it.id == supervisorId && !it.isDeleted }
            ?: error(uiText(R.string.report_supervisor_missing))
        val patients = built.patients.filter { it.responsibleSpecialistId == supervisorId }
        require(patients.isNotEmpty()) { uiText(R.string.ui_report_pdf_workspace_001) }
        return built.copy(patients = patients, doctors = listOf(supervisor), summary = built.summary.copy(
            patientCount = patients.size, psychoCount = patients.count { it.diagnosisType == DiagnosisType.PSYCHIATRIC || it.diagnosisType == DiagnosisType.DUAL },
            escortCount = patients.count { it.hasCompanion }, doctors = listOf(supervisor)))
    }

    suspend fun loadPdfOptions(): PdfExportOptions = PdfExportOptions(
        hospitalName = projectConfigStore.current().hospitalName,
        wardName = settingsRepository.get(AppConstants.SETTING_REPORT_WARD_NAME).orEmpty(),
        orientation = PdfOrientation.PORTRAIT,
        paperSize = PdfPaperSize.fromSetting(
            settingsRepository.get(AppConstants.SETTING_PDF_PAPER_SIZE)
        ),
        colorPreset = PdfColorPreset.fromSetting(
            settingsRepository.get(AppConstants.SETTING_PDF_COLOR_PRESET)
        ),
        darkMode = false,
        ruleWeight = PdfRuleWeight.MEDIUM,
        ruleContrast = PdfRuleContrast.MEDIUM,
        rowShading = PdfRowShading.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_ROW_SHADING)),
        patientCardStyle = PatientCardStyle.fromSetting(
            settingsRepository.get(AppConstants.SETTING_PDF_PATIENT_CARD_STYLE)
        )
    )

    suspend fun renderPdf(
        patients: List<com.hos.rushdpatients.data.model.Patient>,
        doctors: List<com.hos.rushdpatients.data.model.Doctor>,
        summary: com.hos.rushdpatients.domain.report.ReportSummary,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>,
        options: PdfExportOptions,
        outputFile: File,
        reviewedStyle: PdfStyle? = null,
        documentLabel: String? = null
    ) {
        val style = reviewedStyle ?: PdfStyle.fromSetting(
            settingsRepository.get(AppConstants.SETTING_PDF_STYLE)
        )
        when (style) {
            PdfStyle.CLASSIC -> classicPdfExporter.export(
                patients = patients,
                doctors = doctors,
                summary = summary,
                residentNames = residentNames,
                supervisorNames = supervisorNames,
                options = options,
                outputFile = outputFile,
                documentLabel = documentLabel
            )
            PdfStyle.CARDS -> cardsPdfExporter.export(
                patients = patients,
                doctors = doctors,
                summary = summary,
                residentNames = residentNames,
                supervisorNames = supervisorNames,
                options = options,
                outputFile = outputFile,
                documentLabel = documentLabel
            )
        }
    }

    fun reportFileName(date: java.time.LocalDate, route: String = "general", draft: Boolean = false,
        supervisorName: String? = null): String =
        com.hos.rushdpatients.domain.report.ReportFileNames.pdf(date, route, draft, supervisorName)
}
