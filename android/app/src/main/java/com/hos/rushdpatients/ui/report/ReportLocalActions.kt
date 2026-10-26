package com.hos.rushdpatients.ui.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.hos.rushdpatients.R
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.report.BuiltReport
import com.hos.rushdpatients.domain.report.ReportReview
import com.hos.rushdpatients.pdf.ClinicalPdfCache
import com.hos.rushdpatients.pdf.MediaStoreSaver
import com.hos.rushdpatients.pdf.PdfAssignmentExporter
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.components.UiStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Every public save/share route checks current data, opened bytes and publication authority here. */
internal class ReportLocalActions(
    private val uiText: UiStrings,
    private val _state: MutableStateFlow<ReportUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val context: Context, private val settingsRepository: SettingsRepository,
    private val assignmentPdfExporter: PdfAssignmentExporter, private val mediaStoreSaver: MediaStoreSaver,
    private val adminAuthorizer: AdminAuthorizer, private val pdfWorkspace: ReportPdfWorkspace,
    private val reviewedSnapshot: () -> BuiltReport?,
    private val validateExport: suspend (BuiltReport, Boolean) -> Unit
) {
    fun previewPdf() = previewExportPdf("general", draft = false)
    fun previewSupervisorPdf(supervisorId: String) = previewExportPdf("supervisor:$supervisorId", draft = false)

    private suspend fun checkRoute(built: BuiltReport, route: String) {
        when {
            route == "general" -> require(built.doctors.isNotEmpty()) { uiText(R.string.ui_report_preview_screen_003) }
            route == "assignments" -> adminAuthorizer.requireAdmin()
            route == "unassigned" -> require(built.patients.any { it.responsibleSpecialistId == null })
            route.startsWith("supervisor:") -> pdfWorkspace.supervisorPdfReport(built, route.removePrefix("supervisor:"))
            else -> error(uiText(R.string.report_export_invalid_route))
        }
    }

    fun previewExportPdf(route: String, draft: Boolean) {
        val current = _state.value
        if (current.isBusy || current.shift == null) return
        val key = pdfWorkspace.exportKey(route, draft)
        val generation = current.pdfReviewGeneration
        _state.update { it.copy(previewingPdf = true, error = null, retryAction = null,
            openedPdfUris = it.openedPdfUris - listOfNotNull(it.pdfReviewUris[key]).toSet(),
            pdfReviewUris = it.pdfReviewUris - key) }
        viewModelScope.launch {
            var temporary: File? = null
            try {
                val built = reviewedSnapshot() ?: throw PdfReviewRequiredException(uiText)
                validateExport(built, true) // Viewing never requires publication.
                val prepared = withContext(Dispatchers.IO) {
                    checkRoute(built, route)
                    val selected = when {
                        route.startsWith("supervisor:") -> pdfWorkspace.supervisorPdfReport(built, route.removePrefix("supervisor:"))
                        route == "unassigned" -> {
                            val patients = built.patients.filter { it.responsibleSpecialistId == null }
                            built.copy(patients = patients, summary = built.summary.copy(patientCount = patients.size,
                                psychoCount = patients.count { it.diagnosisType != com.hos.rushdpatients.data.model.DiagnosisType.ADDICTION },
                                escortCount = patients.count { it.hasCompanion }))
                        }
                        else -> built
                    }
                    val options = pdfWorkspace.loadPdfOptions()
                    val style = PdfStyle.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_STYLE))
                    val dir = File(context.cacheDir, "pdf_previews").apply { mkdirs() }
                    val file = File.createTempFile(if (draft) "draft_report_" else "reviewed_report_", ".pdf", dir)
                    temporary = file
                    val label = if (draft) uiText(R.string.report_local_draft_label) else null
                    if (route == "assignments") assignmentPdfExporter.export(built.patients, built.doctorNames,
                        built.registryDoctors, built.summary, options, file, documentLabel = label)
                    else pdfWorkspace.renderPdf(selected.patients, selected.doctors, selected.summary,
                        selected.doctorNames, selected.doctorNames, options, file, style, documentLabel = label)
                    ReviewedPdf(file, pdfWorkspace.pdfDigest(file), ReportReview.exportFingerprint(built), options, style, built)
                }
                validateExport(built, true)
                check(_state.value.pdfReviewGeneration == generation) { uiText(R.string.ui_report_local_actions_001) }
                val displayName = pdfWorkspace.reportFileName(built.summary.shiftDate, route, draft,
                    built.registryDoctors.firstOrNull { route == "supervisor:${it.id}" }?.fullName)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", prepared.file, displayName)
                pdfWorkspace.replaceReview(key, prepared)
                temporary = null
                _state.update { it.copy(previewingPdf = false, pdfPreviewUri = uri,
                    pdfReviewUris = it.pdfReviewUris + (key to uri.toString())) }
            } catch (e: CancellationException) {
                pdfWorkspace.removeReview(key)
                _state.update { it.copy(previewingPdf = false) }
                throw e
            } catch (e: Exception) {
                pdfWorkspace.removeReview(key)
                _state.update { it.copy(previewingPdf = false, error = e.message, retryAction = null) }
            } finally {
                temporary?.let { ClinicalPdfCache.delete(listOf(it)) }
            }
        }
    }

    fun onPdfPreviewOpened(uri: Uri) = _state.update { current ->
        if (current.pdfPreviewUri == uri && uri.toString() in current.pdfReviewUris.values)
            current.copy(pdfPreviewUri = null, openedPdfUris = current.openedPdfUris + uri.toString()) else current
    }

    suspend fun requireOpened(built: BuiltReport, routes: Set<String>, draft: Boolean): List<Pair<String, File>> {
        require(routes.isNotEmpty()) { uiText(R.string.report_export_select) }
        return routes.map { route ->
            checkRoute(built, route)
            val key = pdfWorkspace.exportKey(route, draft)
            check(_state.value.pdfReviewUris[key] in _state.value.openedPdfUris) { uiText(R.string.ui_report_pdf_workspace_003) }
            route to pdfWorkspace.checkedExportPdf(built, route, draft)
        }
    }

    fun exportLocally() = saveExportPdfs(setOf("general"), draft = true)
    fun sharePdf() = shareExportPdf("general", draft = true)
    fun exportAssignments() = saveExportPdfs(setOf("assignments"), draft = false)
    fun exportSupervisorPdfs() {
        val built = reviewedSnapshot() ?: return
        val routes = built.patients.mapTo(linkedSetOf()) {
            it.responsibleSpecialistId?.let { id -> "supervisor:$id" } ?: "unassigned"
        }
        saveExportPdfs(routes, draft = true)
    }

    fun saveExportPdfs(routes: Set<String>, draft: Boolean) = copyReviewed(routes, draft, share = false)
    fun shareExportPdf(route: String, draft: Boolean) = copyReviewed(setOf(route), draft, share = true)

    private fun copyReviewed(routes: Set<String>, draft: Boolean, share: Boolean) {
        if (_state.value.isBusy || _state.value.shift == null) return
        _state.update { it.copy(exportingLocalPdf = !share, sharingPdf = share, error = null, retryAction = null) }
        viewModelScope.launch {
            try {
                val built = reviewedSnapshot() ?: throw PdfReviewRequiredException(uiText)
                val files = withContext(Dispatchers.IO) {
                    validateExport(built, draft || routes == setOf("assignments"))
                    requireOpened(built, routes, draft)
                }
                var shared: Uri? = null
                for ((route, file) in files) withContext(Dispatchers.IO) {
                    validateExport(built, draft || routes == setOf("assignments"))
                    checkRoute(built, route)
                    pdfWorkspace.checkedExportPdf(built, route, draft)
                    val supervisorName = built.registryDoctors.firstOrNull {
                        route == "supervisor:${it.id}"
                    }?.fullName
                    val name = pdfWorkspace.reportFileName(built.summary.shiftDate, route, draft, supervisorName)
                    if (share) {
                        val dir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
                        val copy = File(dir, name)
                        try {
                            file.copyTo(copy, overwrite = true)
                            check(pdfWorkspace.pdfDigest(copy) == pdfWorkspace.pdfDigest(file)) { uiText(R.string.ui_report_local_actions_004) }
                            shared = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
                        } catch (e: Exception) { copy.delete(); throw e }
                    } else checkNotNull(mediaStoreSaver.savePdf(context, file, name, verifyContents = true)) {
                        uiText(R.string.ui_report_local_actions_011)
                    }
                }
                _state.update { it.copy(exportingLocalPdf = false, sharingPdf = false, pdfShareUri = shared,
                    snackbar = uiText(if (routes == setOf("assignments")) R.string.ui_report_local_actions_007
                        else if (draft) R.string.report_draft_exported else R.string.report_official_exported), retryAction = null) }
            } catch (e: CancellationException) {
                _state.update { it.copy(exportingLocalPdf = false, sharingPdf = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(exportingLocalPdf = false, sharingPdf = false, error = e.message, retryAction = null) }
            }
        }
    }

    fun consumePdfShare() = _state.update { it.copy(pdfShareUri = null) }
    fun onPdfViewerUnavailable() {
        pdfWorkspace.clearReviews()
        _state.update { it.copy(pdfPreviewUri = null, pdfReviewUris = emptyMap(), openedPdfUris = emptySet(),
            pdfReviewGeneration = it.pdfReviewGeneration + 1, error = uiText(R.string.ui_report_local_actions_012)) }
    }
}
