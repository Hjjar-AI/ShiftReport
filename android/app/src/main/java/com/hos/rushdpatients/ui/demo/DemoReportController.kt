package com.hos.rushdpatients.ui.demo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.report.*
import com.hos.rushdpatients.network.telegram.Markdown
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.sync.KnownShiftSnapshot
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.ui.components.readinessWarnings
import com.hos.rushdpatients.ui.report.*
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

/** Fake publication/delivery; the report UI, readiness rules and PDF renderer are production code. */
internal class DemoReportController(
    private val context: Context,
    private val uiText: UiStrings,
    private val scope: CoroutineScope,
    private val repository: InMemoryDemoWardRepository,
    private val exporter: PdfReportExporter,
    private val assignmentRenderer: PdfAssignmentRenderer,
    private val saver: MediaStoreSaver,
    private val textBuilder: TextReportBuilder,
    private val selectDoctors: (List<String>) -> Unit
) : ReportController {
    private val _state = MutableStateFlow(ReportUiState())
    override val state = _state.asStateFlow()
    override val simulation = true
    private var reviewed: DemoWardState? = null
    private var exportPublished: DemoWardState? = null
    private val files = mutableMapOf<String, File>()
    private val fileDigests = mutableMapOf<String, String>()
    private var reportDay = ShiftDate.current()
    private var fileJob: Job? = null
    private var generation = 0L
    val busy get() = _state.value.isBusy

    override fun load(reviewNotice: String?) {
        if (_state.value.isBusy && !_state.value.loading) return
        clearReviews()
        val snapshot = repository.state.value
        reviewed = snapshot
        val shift = snapshot.shift
        val changes = repository.changes(snapshot)
        val briefing = buildList {
            changes.added.forEach { add(uiText(R.string.report_patient_added, it.displayName)) }
            changes.changed.forEach { add(uiText(R.string.report_patient_changed, it.displayName)) }
            changes.removed.forEach { add(uiText(R.string.report_patient_removed, it.displayName)) }
            if (changes.settingsChanged) add(uiText(R.string.report_shift_settings_changed))
        }
        reportDay = ShiftDate.current()
        val summary = snapshot.summary.copy(shiftDate = reportDay).takeIf { snapshot.shiftDoctors.isNotEmpty() }
        val text = summary?.let {
            textBuilder.build(shift.copy(date = it.shiftDate), snapshot.ordered, snapshot.shiftDoctors, it, snapshot.names, snapshot.names)
                .joinToString("\n\n", transform = Markdown::toPlainText)
        }.orEmpty()
        _state.update { it.copy(loading = false, isAdmin = true, shift = shift, patients = snapshot.ordered,
            doctors = snapshot.shiftDoctors, availableDoctors = snapshot.doctors,
            summary = summary, previewMarkdown = uiText(R.string.demo_document_label) + "\n\n" + text,
            supervisorTargets = snapshot.doctors.filter { doctor -> doctor.clinicalRole.canBeSupervisor() }
                .mapNotNull { doctor -> snapshot.patients.count { it.responsibleSpecialistId == doctor.id }
                    .takeIf { it > 0 }?.let { SupervisorReportTarget(doctor.id, doctor.fullName, -1L, it) } },
            readinessWarnings = readinessWarnings(uiText, snapshot.patients),
            readinessIssues = ReportReadiness.issues(snapshot.patients), changeBriefing = briefing,
            publicationOutcome = null, deliveryOutcome = null, error = null, reviewNotice = reviewNotice,
            exportUploadConfirmed = exportPublished?.let(::matches) == true,
            deliveryRecords = repository.receipts.value, retryAction = null) }
    }

    override fun setShiftDoctors(ids: List<String>, onSuccess: () -> Unit) {
        if (busy) return
        selectDoctors(ids)
        onSuccess()
        load()
    }
    override fun setReportAsPdf(value: Boolean) { if (!busy) _state.update { it.copy(reportAsPdf = value) } }
    override fun setPdfAppearance(style: PdfStyle, requested: PdfExportOptions) {
        if (busy) return
        clearReviews()
        _state.update { it.copy(pdfStyle = style, pdfOptions = requested.copy(orientation = PdfOrientation.PORTRAIT,
            darkMode = false, ruleWeight = PdfRuleWeight.MEDIUM, ruleContrast = PdfRuleContrast.MEDIUM)) }
    }
    override fun fetchDoctorsForReport() { if (!busy) load(uiText(R.string.demo_fetch_complete)) }
    override fun dismissSnackbar() { _state.update { it.copy(snackbar = null) } }
    override fun previewPdf() = previewExportPdf("general", false)
    override fun previewSupervisorPdf(supervisorId: String) = previewExportPdf(key(supervisorId), false)

    private fun clearReviews() {
        generation++
        val obsolete = files.values.toList()
        files.clear()
        fileDigests.clear()
        ClinicalPdfCache.delete(obsolete)
        _state.update { it.copy(pdfPreviewUri = null, pdfShareUri = null, pdfReviewUris = emptyMap(),
            openedPdfUris = emptySet(), pdfReviewGeneration = generation) }
    }
    fun reset() {
        fileJob?.cancel()
        clearReviews()
        reviewed = null
        exportPublished = null
        _state.value = ReportUiState()
    }
    private fun matches(snapshot: DemoWardState): Boolean {
        val current = repository.state.value
        return snapshot.shiftId == current.shiftId && snapshot.actorId == current.actorId &&
            !KnownShiftSnapshot.changes(current.ordered, snapshot.ordered,
                current.shiftDoctorIds != snapshot.shiftDoctorIds || current.sort != snapshot.sort).pending &&
            current.doctors == snapshot.doctors
    }
    private fun requireSnapshot(): DemoWardState {
        val snapshot = checkNotNull(reviewed)
        check(matches(snapshot)) { uiText(R.string.demo_review_changed) }
        return snapshot
    }
    private fun key(supervisor: String?) = supervisor?.let { "supervisor:$it" } ?: "general"
    override fun previewExportPdf(route: String, draft: Boolean) {
        val supervisor = route.takeIf { it.startsWith("supervisor:") }?.removePrefix("supervisor:")
        require(route == "general" || route == "unassigned" || route == "assignments" || supervisor != null)
        val artifactKey = if (draft) "draft:$route" else route
        if (busy) return
        val snapshot = reviewed ?: return
        val current = _state.value
        val token = generation
        val capturedDay = reportDay
        _state.update { it.copy(previewingPdf = true, error = null,
            openedPdfUris = it.openedPdfUris - listOfNotNull(it.pdfReviewUris[artifactKey]).toSet(),
            pdfReviewUris = it.pdfReviewUris - artifactKey) }
        fileJob = scope.launch {
            var temporary: File? = null
            try {
                requireSnapshot()
                val file = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "pdf_previews").apply { mkdirs() }
                    val output = File.createTempFile("DEMO_", ".pdf", directory)
                    temporary = output
                    val patients = snapshot.ordered.filter { if (route == "unassigned") it.responsibleSpecialistId == null
                        else supervisor == null || it.responsibleSpecialistId == supervisor }
                    val doctors = if (supervisor == null) snapshot.shiftDoctors else snapshot.doctors.filter { it.id == supervisor }
                    val options = current.pdfOptions.copy(hospitalName = uiText(R.string.ui_demo_navigation_001))
                    val label = listOfNotNull(uiText(R.string.demo_document_label),
                        if (draft) uiText(R.string.report_local_draft_label) else null).joinToString(" · ")
                    if (route == "assignments") {
                        check(current.isAdmin)
                        assignmentRenderer.export(snapshot.ordered, snapshot.names, snapshot.doctors,
                            snapshot.summary.copy(shiftDate = capturedDay), options, output, documentLabel = label)
                    } else {
                        require(patients.isNotEmpty() && doctors.isNotEmpty()) { uiText(R.string.ui_demo_ward_view_model_006) }
                        val summary = requireNotNull(current.summary).copy(patientCount = patients.size,
                            psychoCount = patients.count { it.diagnosisType == com.hos.rushdpatients.data.model.DiagnosisType.PSYCHIATRIC ||
                                it.diagnosisType == com.hos.rushdpatients.data.model.DiagnosisType.DUAL },
                            escortCount = patients.count { it.hasCompanion }, doctors = doctors)
                        exporter.export(patients, doctors, summary, snapshot.names, snapshot.names, options,
                            elegant = current.pdfStyle == PdfStyle.CARDS, outputFile = output, documentLabel = label)
                    }
                    check(output.length() > 0)
                    output
                }
                val fileHash = withContext(Dispatchers.IO) { digest(file) }
                if (token != generation) return@launch
                requireSnapshot()
                val displayName = ReportFileNames.pdf(capturedDay, route, draft,
                    snapshot.doctors.firstOrNull { route == "supervisor:${it.id}" }?.fullName, demo = true)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file, displayName)
                files.put(artifactKey, file)?.let { previous -> ClinicalPdfCache.delete(listOf(previous)) }
                fileDigests[artifactKey] = fileHash
                temporary = null
                _state.update { it.copy(pdfPreviewUri = uri, pdfReviewUris = it.pdfReviewUris + (artifactKey to uri.toString())) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message) } }
            finally {
                withContext(NonCancellable + Dispatchers.IO) { temporary?.delete() }
                _state.update { it.copy(previewingPdf = false) }
            }
        }
    }
    override fun onPdfPreviewOpened(uri: Uri) {
        _state.update { if (it.pdfPreviewUri == uri && uri.toString() in it.pdfReviewUris.values)
            it.copy(pdfPreviewUri = null, openedPdfUris = it.openedPdfUris + uri.toString()) else it }
    }
    override fun onPdfViewerUnavailable() {
        _state.update { it.copy(pdfPreviewUri = null, error = uiText(R.string.ui_report_local_actions_002)) }
    }
    private fun digest(file: File): String = file.inputStream().use { stream ->
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var length = stream.read(buffer)
        while (length >= 0) {
            if (length > 0) hash.update(buffer, 0, length)
            length = stream.read(buffer)
        }
        hash.digest().joinToString("") { "%02x".format(it) }
    }
    private suspend fun checkedFile(route: String): File = withContext(Dispatchers.IO) {
        requireSnapshot()
        val current = _state.value
        check(current.pdfReviewUris[route] in current.openedPdfUris) { uiText(R.string.ui_report_pdf_workspace_003) }
        val file = checkNotNull(files[route])
        check(file.isFile && digest(file) == fileDigests[route]) { uiText(R.string.ui_report_local_actions_004) }
        file
    }
    private fun requireExport(draft: Boolean, routes: Set<String>) {
        requireSnapshot()
        if ("assignments" in routes) check(_state.value.isAdmin)
        if (!draft && routes != setOf("assignments")) check(exportPublished?.let(::matches) == true) { uiText(R.string.report_export_upload_required) }
    }
    override fun sharePdf() = shareExportPdf("general", true)
    override fun shareExportPdf(route: String, draft: Boolean) {
        if (busy) return
        _state.update { it.copy(sharingPdf = true, error = null) }
        fileJob = scope.launch {
            try {
                requireExport(draft, setOf(route))
                val file = checkedFile(if (draft) "draft:$route" else route)
                requireExport(draft, setOf(route))
                val name = ReportFileNames.pdf(reportDay, route, draft,
                    requireSnapshot().doctors.firstOrNull { route == "supervisor:${it.id}" }?.fullName, demo = true)
                _state.update { it.copy(pdfShareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file, name)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message) } }
            finally { _state.update { it.copy(sharingPdf = false) } }
        }
    }
    override fun consumePdfShare() { _state.update { it.copy(pdfShareUri = null) } }
    override fun exportLocally() = saveExportPdfs(setOf("general"), true)
    override fun exportSupervisorPdfs() = saveExportPdfs(_state.value.supervisorTargets.mapTo(linkedSetOf()) { key(it.doctorId) }, true)
    override fun saveExportPdfs(routes: Set<String>, draft: Boolean) {
        if (busy) return
        _state.update { it.copy(exportingLocalPdf = true, error = null) }
        fileJob = scope.launch {
            try {
                requireExport(draft, routes)
                val copies = routes.map { it to checkedFile(if (draft) "draft:$it" else it) }
                check(copies.isNotEmpty())
                for ((route, file) in copies) {
                    requireExport(draft, routes)
                    checkedFile(if (draft) "draft:$route" else route)
                    checkNotNull(saver.savePdf(context, file,
                        ReportFileNames.pdf(reportDay, route, draft,
                            requireSnapshot().doctors.firstOrNull { route == "supervisor:${it.id}" }?.fullName, demo = true), verifyContents = true)) {
                        uiText(R.string.report_pdf_save_failed)
                    }
                }
                _state.update { it.copy(snackbar = uiText(R.string.ui_demo_ward_view_model_011)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message) } }
            finally { _state.update { it.copy(exportingLocalPdf = false) } }
        }
    }
    override fun exportAssignments() = saveExportPdfs(setOf("assignments"), draft = false)
    override fun sendSelectedReports(includeGeneral: Boolean, supervisorIds: Set<String>, exportPdf: Boolean) {
        if (busy) return
        _state.update { it.copy(sending = true, error = null) }
        fileJob = scope.launch {
            try {
                val snapshot = requireSnapshot()
                val current = _state.value
                val targets = current.supervisorTargets.filter { it.doctorId in supervisorIds }
                check(targets.size == supervisorIds.size && (includeGeneral || targets.isNotEmpty()))
                if (includeGeneral && current.reportAsPdf) checkedFile("general")
                targets.forEach { checkedFile(key(it.doctorId)) }
                requireSnapshot()
                val destinations = buildList {
                    if (includeGeneral) add("general" to uiText(R.string.ui_report_preview_screen_097))
                    targets.forEach { add(key(it.doctorId) to it.doctorName) }
                }
                val receipts = destinations.map { (route, label) -> ReportDeliveryRecord(
                    UUID.randomUUID().toString(), snapshot.shiftId, route, "demo-${snapshot.revision}",
                    "DEMO · $label", if (route == "general" && !current.reportAsPdf) "TEXT" else "PDF",
                    snapshot.actorId, System.currentTimeMillis(), DeliveryStatus.SENT) }
                repository.receipts.update { it + receipts }
                repository.publish(snapshot)
                exportPublished = snapshot
                _state.update { it.copy(sending = false, exportUploadConfirmed = true, publicationOutcome = ReportStepOutcome.CONFIRMED,
                    deliveryOutcome = ReportStepOutcome.CONFIRMED, deliveryRecords = repository.receipts.value,
                    snackbar = uiText(R.string.demo_send_complete)) }
                if (exportPdf) {
                    val routes = buildSet {
                        if (includeGeneral && current.reportAsPdf) add("general")
                        targets.forEach { add(key(it.doctorId)) }
                    }
                    if (routes.isNotEmpty()) saveExportPdfs(routes, false)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message) } }
            finally { _state.update { it.copy(sending = false) } }
        }
    }
    override fun resolveDelivery(record: ReportDeliveryRecord, resolution: DeliveryResolution) {
        repository.receipts.update { rows -> rows.map { if (it.id != record.id) it else it.copy(status = when (resolution) {
            DeliveryResolution.RECEIVED -> DeliveryStatus.SENT
            DeliveryResolution.NOT_RECEIVED -> DeliveryStatus.NOT_SENT
            DeliveryResolution.ALLOW_COPY -> DeliveryStatus.UNKNOWN_COPY_ALLOWED
        }) } }
        _state.update { it.copy(deliveryRecords = repository.receipts.value) }
    }
    override fun retryLastAction(onReviewSend: () -> Unit) { onReviewSend() }
}
