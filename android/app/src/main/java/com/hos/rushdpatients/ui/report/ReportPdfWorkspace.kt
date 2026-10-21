package com.hos.rushdpatients.ui.report

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
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

internal data class ReviewedPdf(val file: File, val digest: String, val reportKey: String, val options: PdfExportOptions, val style: PdfStyle, val reviewed: BuiltReport)
internal class PdfReviewRequiredException : IllegalStateException("عاين PDF لهذه الوجهة أولاً؛ تغيّر الملف أو البيانات أو خيارات PDF يتطلب معاينة جديدة")

/** One reviewed-artifact store for this ViewModel; publication/delivery stay with its owner. */
internal class ReportPdfWorkspace(
    private val context: Context, private val settingsRepository: SettingsRepository,
    private val projectConfigStore: ProjectConfigStore, private val classicPdfExporter: PdfReportExporter,
    private val cardsPdfExporter: PdfCardsExporter, private val mediaStoreSaver: MediaStoreSaver
) {
    val reviewedPdfs = java.util.concurrent.ConcurrentHashMap<String, ReviewedPdf>()

    fun pdfKey(supervisorId: String?) = supervisorId?.let { "supervisor:$it" } ?: "general"

    fun pdfDigest(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    suspend fun checkedPdf(built: BuiltReport, supervisorId: String? = null): File = withContext(Dispatchers.IO) {
        val artifact = reviewedPdfs[pdfKey(supervisorId)] ?: throw PdfReviewRequiredException()
        ReportReview.requireMatches(artifact.reviewed, built)
        if (artifact.style != PdfStyle.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_STYLE)) ||
            artifact.reportKey != ReportReview.fingerprint(built) ||
            artifact.options.copy(generatedAtEpochMillis = 0) != loadPdfOptions().copy(generatedAtEpochMillis = 0) ||
            !artifact.file.isFile || pdfDigest(artifact.file) != artifact.digest) throw PdfReviewRequiredException()
        artifact.file
    }

    fun supervisorPdfReport(built: BuiltReport, supervisorId: String): BuiltReport {
        val supervisor = built.registryDoctors.firstOrNull { it.id == supervisorId && !it.isDeleted }
            ?: error("المشرف غير موجود")
        val patients = built.patients.filter { it.responsibleSpecialistId == supervisorId }
        require(patients.isNotEmpty()) { "لا يوجد مرضى لهذا المشرف" }
        return built.copy(patients = patients, doctors = listOf(supervisor), summary = built.summary.copy(
            patientCount = patients.size, psychoCount = patients.count { it.diagnosisType == DiagnosisType.PSYCHIATRIC || it.diagnosisType == DiagnosisType.DUAL },
            escortCount = patients.count { it.hasCompanion }, doctors = listOf(supervisor)))
    }

    suspend fun loadPdfOptions(): PdfExportOptions = PdfExportOptions(
        hospitalName = projectConfigStore.current().hospitalName,
        wardName = settingsRepository.get(AppConstants.SETTING_REPORT_WARD_NAME).orEmpty(),
        orientation = PdfOrientation.fromSetting(
            settingsRepository.get(AppConstants.SETTING_PDF_ORIENTATION)
        ),
        paperSize = PdfPaperSize.fromSetting(
            settingsRepository.get(AppConstants.SETTING_PDF_PAPER_SIZE)
        ),
        colorPreset = PdfColorPreset.fromSetting(
            settingsRepository.get(AppConstants.SETTING_PDF_COLOR_PRESET)
        ),
        darkMode = settingsRepository.getBoolean(AppConstants.SETTING_PDF_DARK_MODE, false),
        ruleWeight = PdfRuleWeight.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_RULE_WEIGHT)),
        ruleContrast = PdfRuleContrast.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_RULE_CONTRAST)),
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
        reviewedStyle: PdfStyle? = null
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
                outputFile = outputFile
            )
            PdfStyle.CARDS -> cardsPdfExporter.export(
                patients = patients,
                doctors = doctors,
                summary = summary,
                residentNames = residentNames,
                supervisorNames = supervisorNames,
                options = options,
                outputFile = outputFile
            )
        }
    }

    suspend fun saveCombinedPdf(built: BuiltReport, options: PdfExportOptions) {
        val tmp = File.createTempFile("rushd_local_report_", ".pdf", context.cacheDir)
        try {
            renderPdf(
                patients = built.patients,
                doctors = built.doctors,
                summary = built.summary,
                residentNames = built.doctorNames,
                supervisorNames = built.doctorNames,
                options = options,
                outputFile = tmp
            )
            val name = reportFileName(built.shift.date)
            checkNotNull(mediaStoreSaver.savePdf(context, tmp, name)) { "تعذر حفظ ملف PDF" }
        } finally {
            tmp.delete()
        }
    }

    suspend fun saveSupervisorPdfs(
        built: BuiltReport,
        options: PdfExportOptions
    ): Int {
        val grouped = built.patients.groupBy { it.responsibleSpecialistId }
            .ifEmpty { mapOf<String?, List<com.hos.rushdpatients.data.model.Patient>>(null to emptyList()) }
        var saved = 0
        grouped.forEach { (supervisorId, patients) ->
            val supervisor = built.registryDoctors.firstOrNull { it.id == supervisorId }
            val tmp = File.createTempFile("rushd_supervisor_report_", ".pdf", context.cacheDir)
            try {
                val summary = built.summary.copy(
                    patientCount = patients.size,
                    psychoCount = patients.count {
                        it.diagnosisType == DiagnosisType.PSYCHIATRIC ||
                                it.diagnosisType == DiagnosisType.DUAL
                    },
                    escortCount = patients.count { it.hasCompanion },
                    doctors = listOfNotNull(supervisor)
                )
                renderPdf(
                    patients = patients,
                    doctors = listOfNotNull(supervisor).ifEmpty { built.doctors },
                    summary = summary,
                    residentNames = built.doctorNames,
                    supervisorNames = built.doctorNames,
                    options = options,
                    outputFile = tmp
                )
                val safeName = (supervisor?.fullName ?: "بدون_مشرف")
                    .replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
                    .take(60)
                val displayName = reportFileName(built.shift.date, safeName)
                checkNotNull(mediaStoreSaver.savePdf(context, tmp, displayName)) {
                    "تعذر حفظ ملف PDF للمشرف ${supervisor?.fullName.orEmpty()}"
                }
                saved++
            } finally {
                tmp.delete()
            }
        }
        return saved
    }

    fun reportFileName(date: java.time.LocalDate, suffix: String? = null): String {
        val created = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val extra = suffix?.takeIf { it.isNotBlank() }?.let { "_$it" }.orEmpty()
        return "Rushd_Report_${date}_${created}$extra.pdf"
    }
}
