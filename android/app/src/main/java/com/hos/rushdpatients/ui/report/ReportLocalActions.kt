package com.hos.rushdpatients.ui.report

import android.content.Context
import androidx.core.content.FileProvider
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.report.ReportBuilder
import com.hos.rushdpatients.domain.report.ReportReview
import com.hos.rushdpatients.pdf.MediaStoreSaver
import com.hos.rushdpatients.pdf.PdfAssignmentExporter
import com.hos.rushdpatients.pdf.PdfStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Local file actions share the ViewModel state/scope and its single reviewed-artifact store. */
internal class ReportLocalActions(
    private val _state: MutableStateFlow<ReportUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val shiftId: String, private val context: Context,
    private val reportBuilder: ReportBuilder, private val settingsRepository: SettingsRepository,
    private val assignmentPdfExporter: PdfAssignmentExporter, private val mediaStoreSaver: MediaStoreSaver,
    private val adminAuthorizer: AdminAuthorizer, private val pdfWorkspace: ReportPdfWorkspace
) {

    fun previewPdf() = previewPdfFor(null)

    fun previewSupervisorPdf(supervisorId: String) = previewPdfFor(supervisorId)

    private fun previewPdfFor(supervisorId: String?) {
        val current = _state.value
        if (current.savingPdfOptions || current.savingDoctors || current.previewingPdf || current.exportingLocalPdf || current.sharingPdf || current.sending || current.shift == null) return
        val reviewGeneration = current.pdfReviewGeneration
        _state.update { it.copy(previewingPdf = true, error = null, doctorRegistryAction = null, retryAction = null,
            pdfReviewUris = it.pdfReviewUris - pdfWorkspace.pdfKey(supervisorId)) }
        viewModelScope.launch {
            var temporary: File? = null
            try {
                val built = reportBuilder.build(shiftId)
                val prepared = withContext(Dispatchers.IO) {
                    val selected = if (supervisorId == null) built else pdfWorkspace.supervisorPdfReport(built, supervisorId)
                    val options = pdfWorkspace.loadPdfOptions()
                    val style = PdfStyle.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_STYLE))
                    val dir = File(context.cacheDir, "pdf_previews").apply { mkdirs() }
                    val file = File.createTempFile("reviewed_report_", ".pdf", dir)
                    temporary = file
                    try {
                        pdfWorkspace.renderPdf(selected.patients, selected.doctors, selected.summary,
                            selected.doctorNames, selected.doctorNames, options, file, style)
                        ReviewedPdf(file, pdfWorkspace.pdfDigest(file), ReportReview.fingerprint(built), options, style, built)
                    } catch (e: Exception) { file.delete(); throw e }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", prepared.file)
                check(_state.value.pdfReviewGeneration == reviewGeneration) {
                    "تغيّرت بيانات التقرير أو خياراته؛ عاين الملف من جديد"
                }
                pdfWorkspace.replaceReview(pdfWorkspace.pdfKey(supervisorId), prepared)
                temporary = null
                _state.update { it.copy(previewingPdf = false, pdfPreviewUri = uri,
                    pdfReviewUris = it.pdfReviewUris + (pdfWorkspace.pdfKey(supervisorId) to uri.toString())) }
            } catch (e: CancellationException) {
                pdfWorkspace.removeReview(pdfWorkspace.pdfKey(supervisorId))
                _state.update { it.copy(previewingPdf = false) }; throw e
            } catch (e: Exception) {
                pdfWorkspace.removeReview(pdfWorkspace.pdfKey(supervisorId))
                _state.update { it.copy(previewingPdf = false, error = e.message ?: "تعذر إنشاء معاينة PDF",
                    retryAction = if (supervisorId == null) ReportRetryAction.PREVIEW else null) }
            } finally {
                temporary?.let { com.hos.rushdpatients.pdf.ClinicalPdfCache.delete(listOf(it)) }
            }
        }
    }

    fun consumePdfPreview() = _state.update { it.copy(pdfPreviewUri = null) }

    fun sharePdf() {
        val current = _state.value
        if (current.savingPdfOptions || current.savingDoctors || current.sharingPdf || current.exportingLocalPdf || current.previewingPdf ||
            current.sending || current.shift == null
        ) return
        _state.update { it.copy(sharingPdf = true, error = null, doctorRegistryAction = null, retryAction = null) }
        viewModelScope.launch {
            try {
                val built = reportBuilder.build(shiftId)
                val uri = withContext(Dispatchers.IO) {
                    val shareDir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
                    shareDir.listFiles()?.forEach { it.delete() }
                    val file = File(shareDir, pdfWorkspace.reportFileName(built.shift.date))
                    try {
                        pdfWorkspace.renderPdf(
                            patients = built.patients, doctors = built.doctors, summary = built.summary,
                            residentNames = built.doctorNames, supervisorNames = built.doctorNames,
                            options = pdfWorkspace.loadPdfOptions(), outputFile = file
                        )
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } catch (e: Exception) {
                        file.delete()
                        throw e
                    }
                }
                _state.update {
                    it.copy(
                        sharingPdf = false,
                        pdfShareUri = uri,
                        lastOperation = "آخر عملية ناجحة: تجهيز PDF للمشاركة",
                        retryAction = null
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(sharingPdf = false) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        sharingPdf = false,
                        error = e.message ?: "تعذر تجهيز ملف المشاركة",
                        retryAction = ReportRetryAction.SHARE
                    )
                }
            }
        }
    }

    fun consumePdfShare() = _state.update { it.copy(pdfShareUri = null) }

    fun exportAssignments() {
        val current = _state.value
        if (current.loading || current.savingPdfOptions || current.savingDoctors || current.exportingLocalPdf || current.previewingPdf ||
            current.sharingPdf || current.sending || current.shift == null) return
        _state.update { it.copy(exportingLocalPdf = true, error = null, doctorRegistryAction = null, retryAction = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val actorId = adminAuthorizer.requireAdmin().id
                    val built = reportBuilder.buildAssignments(shiftId)
                    val tmp = File.createTempFile("rushd_assignments_", ".pdf", context.cacheDir)
                    try {
                        assignmentPdfExporter.export(built.patients, built.doctorNames, built.registryDoctors, built.summary,
                            pdfWorkspace.loadPdfOptions(), tmp)
                        require(adminAuthorizer.requireAdmin().id == actorId) { "تغيّر المستخدم؛ أعد تصدير توزيع المرضى" }
                        checkNotNull(mediaStoreSaver.savePdf(context, tmp, pdfWorkspace.reportFileName(built.shift.date, "Names"),
                            verifyContents = true)) {
                            "تعذر حفظ توزيع المرضى"
                        }
                    } finally {
                        tmp.delete()
                    }
                }
                _state.update { it.copy(exportingLocalPdf = false, snackbar = "تم حفظ توزيع المرضى بالأسماء فقط",
                    lastOperation = "آخر عملية ناجحة: حفظ توزيع المرضى", retryAction = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(exportingLocalPdf = false, error = e.message ?: "تعذر حفظ توزيع المرضى",
                    retryAction = ReportRetryAction.ASSIGNMENTS) }
            }
        }
    }

    fun exportLocally() {
        val current = _state.value
        if (current.savingPdfOptions || current.savingDoctors || current.exportingLocalPdf || current.previewingPdf || current.sharingPdf || current.sending || current.shift == null) return
        _state.update { it.copy(exportingLocalPdf = true, error = null, doctorRegistryAction = null, retryAction = null) }
        viewModelScope.launch {
            try {
                val built = reportBuilder.build(shiftId)
                val options = pdfWorkspace.loadPdfOptions()
                val separate = settingsRepository.getBoolean(
                    AppConstants.SETTING_PDF_SEPARATE_BY_SUPERVISOR,
                    false
                )
                val count = if (separate) {
                    pdfWorkspace.saveSupervisorPdfs(built, options)
                } else {
                    pdfWorkspace.saveCombinedPdf(built, options)
                    1
                }
                _state.update {
                    it.copy(
                        exportingLocalPdf = false,
                        snackbar = "تم حفظ $count ملف PDF محلياً",
                        lastOperation = "آخر عملية ناجحة: حفظ PDF على الجهاز",
                        retryAction = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        exportingLocalPdf = false,
                        error = e.message ?: "تعذر حفظ ملفات PDF",
                        retryAction = ReportRetryAction.SAVE
                    )
                }
            }
        }
    }

    fun onPdfViewerUnavailable() {
        pdfWorkspace.clearReviews()
        _state.update { it.copy(pdfPreviewUri = null, pdfReviewUris = emptyMap(),
            pdfReviewGeneration = it.pdfReviewGeneration + 1,
            error = "لا يوجد تطبيق مثبت لعرض ملفات PDF؛ لم تعتمد المعاينة") }
    }
}
