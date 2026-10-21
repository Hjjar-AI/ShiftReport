package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import com.hos.rushdpatients.domain.doctor.DoctorRegistryActionRequired
import com.hos.rushdpatients.config.ProjectConfigStore
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.WardMutationRepository
import com.hos.rushdpatients.data.repository.StaleShiftEditException
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.report.BuiltReport
import com.hos.rushdpatients.domain.report.ReportBuilder
import com.hos.rushdpatients.domain.report.ReportDeliveryLedger
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.domain.report.DeliveryResolution
import com.hos.rushdpatients.domain.report.ReportDeliveryBlockedException
import com.hos.rushdpatients.domain.report.ReportReview
import com.hos.rushdpatients.domain.report.ReportChangedSinceReviewException
import com.hos.rushdpatients.domain.report.ReportReadiness
import com.hos.rushdpatients.domain.report.TextReportBuilder
import com.hos.rushdpatients.network.ReportSender
import com.hos.rushdpatients.network.ReportDeliveryUncertainException
import com.hos.rushdpatients.network.telegram.Markdown
import com.hos.rushdpatients.pdf.MediaStoreSaver
import com.hos.rushdpatients.pdf.PdfCardsExporter
import com.hos.rushdpatients.pdf.PdfAssignmentExporter
import com.hos.rushdpatients.pdf.PdfExportOptions
import com.hos.rushdpatients.pdf.PdfReportExporter
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.util.ShiftDate
import com.hos.rushdpatients.util.NetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReportPreviewViewModel @Inject constructor(
    private val uiText: UiStrings,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val reportBuilder: ReportBuilder,
    private val textReportBuilder: TextReportBuilder,
    private val reportSender: ReportSender,
    private val classicPdfExporter: PdfReportExporter,
    private val cardsPdfExporter: PdfCardsExporter,
    private val assignmentPdfExporter: PdfAssignmentExporter,
    private val mediaStoreSaver: MediaStoreSaver,
    private val settingsRepository: SettingsRepository,
    private val doctorRepository: DoctorRepository,
    private val shiftRepository: ShiftRepository,
    private val wardMutations: WardMutationRepository,
    private val sessionManager: SessionManager,
    private val adminAuthorizer: AdminAuthorizer,
    private val syncService: SyncService,
    private val projectConfigStore: ProjectConfigStore,
    private val deliveryLedger: ReportDeliveryLedger
) : ViewModel() {

    private val shiftId: String = savedStateHandle.get<String>("shiftId").orEmpty()

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()
    private var reviewedReport: BuiltReport? = null
    private val pdfWorkspace = ReportPdfWorkspace(uiText, context, settingsRepository, projectConfigStore,
        classicPdfExporter, cardsPdfExporter, mediaStoreSaver)

    private val localActions = ReportLocalActions(uiText, _state, viewModelScope, shiftId,
        context, reportBuilder, settingsRepository, assignmentPdfExporter, mediaStoreSaver,
        adminAuthorizer, pdfWorkspace)

    init {
        viewModelScope.launch {
            deliveryLedger.observe().catch {
                _state.update { it.copy(error = uiText(R.string.ui_report_preview_view_model_001), retryAction = null) }
            }.collect { rows ->
                _state.update { it.copy(deliveryRecords = rows.filter { row -> row.shiftId == shiftId }) }
            }
        }
        viewModelScope.launch {
            combine(sessionManager.sessionFlow, doctorRepository.observeAll()) { session, doctors ->
                session?.isAdmin == true && doctors.any {
                    it.id == session.doctorId && it.isAdmin && !it.isDeleted
                }
            }.collect { isAdmin -> _state.update { it.copy(isAdmin = isAdmin) } }
        }
        load()
    }

    override fun onCleared() {
        pdfWorkspace.clearReviews()
        super.onCleared()
    }

    private fun clearPdfReviews() {
        pdfWorkspace.clearReviews()
        _state.update { it.copy(pdfReviewGeneration = it.pdfReviewGeneration + 1,
            pdfPreviewUri = null, pdfReviewUris = emptyMap()) }
    }

    fun load(reviewNotice: String? = null) {
        if (_state.value.sending || _state.value.savingDoctors || _state.value.savingPdfOptions) return
        clearPdfReviews()
        reviewedReport = null
        viewModelScope.launch {
            _state.update { it.copy(loading = true, summary = null, error = reviewNotice) }
            try {
                check(shiftId.isNotBlank()) { uiText(R.string.ui_report_preview_view_model_002) }
                val pdfOptions = loadPdfOptions()
                val pdfStyle = PdfStyle.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_STYLE))
                _state.update { it.copy(pdfOptions = pdfOptions, pdfStyle = pdfStyle) }
                // Keep the selector available even when an empty roster prevents report building.
                val shift = requireNotNull(shiftRepository.getById(shiftId)) { uiText(R.string.ui_ward_shift_operations_005) }
                val available = doctorRepository.getAll().filterNot { it.isDeleted }
                _state.update { it.copy(shift = shift, availableDoctors = available,
                    doctors = available.filter { doctor -> doctor.id in shift.doctorIds },
                    isReadOnly = shift.date != ShiftDate.current()) }
                if (_state.value.doctors.isEmpty()) {
                    _state.update { it.copy(loading = false, previewMarkdown = "", error = null, doctorRegistryAction = null) }
                    return@launch
                }
                val built = reportBuilder.build(shiftId)
                val asPdf = settingsRepository.isReportAsPdf()
                val supervisorTargets = buildSupervisorTargets(built)
                val changes = syncService.describeLocalChanges(shiftId)
                val briefing = buildList {
                    changes.added.forEach { add(uiText(R.string.ui_report_preview_view_model_003, it)) }
                    changes.changed.forEach { add(uiText(R.string.ui_report_preview_view_model_004, it)) }
                    changes.removed.forEach { add(uiText(R.string.ui_report_preview_view_model_005, it)) }
                }
                reviewedReport = built
                _state.update {
                    it.copy(
                        loading = false,
                        shift = built.shift,
                        isReadOnly = built.shift.date != ShiftDate.current(),
                        patients = built.patients,
                        doctors = built.doctors,
                        supervisorTargets = supervisorTargets,
                        summary = built.summary,
                        previewMarkdown = built.textChunks.joinToString("\n\n") {
                            Markdown.toPlainText(it)
                        },
                        readinessWarnings = ReportReadiness.warnings(built.patients),
                        changeBriefing = briefing,
                        reportAsPdf = asPdf
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: uiText(R.string.ui_report_preview_view_model_006))
                }
            }
        }
    }

    fun fetchDoctorsForReport() {
        val current = _state.value
        if (current.loading || current.savingDoctors || current.sending || current.previewingPdf ||
            current.savingPdfOptions || current.exportingLocalPdf || current.sharingPdf) return
        _state.update { it.copy(savingDoctors = true, error = null, doctorRegistryAction = null) }
        viewModelScope.launch {
            var fetched = false
            try {
                syncService.refreshDoctorsFromRemote().getOrThrow()
                clearPdfReviews()
                reviewedReport = null
                fetched = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: uiText(R.string.ui_report_preview_view_model_007),
                    doctorRegistryAction = (e as? DoctorRegistryActionRequired)?.action) }
            } finally { _state.update { it.copy(savingDoctors = false) } }
            if (fetched) load(uiText(R.string.ui_report_preview_view_model_008))
        }
    }

    fun setShiftDoctors(
        ids: List<String>, expectedRevision: Long,
        onStale: (Shift?) -> Unit, onSuccess: () -> Unit
    ) {
        val current = _state.value
        if (current.loading || current.savingPdfOptions || current.savingDoctors || current.sending || current.previewingPdf ||
            current.exportingLocalPdf || current.sharingPdf) return
        if (current.isReadOnly || current.shift == null) return
        _state.update { it.copy(savingDoctors = true, error = null, doctorRegistryAction = null) }
        viewModelScope.launch {
            try {
                activeSendingActor()
                wardMutations.setShiftDoctors(shiftId, ids, expectedRevision, sessionManager.current())
                reviewedReport = null
                _state.update { it.copy(savingDoctors = false) }
                onSuccess()
                load()
            } catch (e: StaleShiftEditException) {
                onStale(shiftRepository.getById(shiftId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_report_preview_view_model_009)) }
            } finally {
                _state.update { it.copy(savingDoctors = false) }
            }
        }
    }

    fun setReportAsPdf(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReportAsPdf(value)
            _state.update { it.copy(reportAsPdf = value) }
        }
    }

    fun setPdfAppearance(style: PdfStyle, options: PdfExportOptions) {
        val current = _state.value
        if (current.loading || current.sending || current.savingDoctors || current.previewingPdf ||
            current.exportingLocalPdf || current.sharingPdf || current.savingPdfOptions) return
        _state.update { it.copy(savingPdfOptions = true) }
        viewModelScope.launch {
            try {
                settingsRepository.putAll(mapOf(
                    AppConstants.SETTING_PDF_STYLE to style.name,
                    AppConstants.SETTING_PDF_ORIENTATION to options.orientation.name,
                    AppConstants.SETTING_PDF_PAPER_SIZE to options.paperSize.name,
                    AppConstants.SETTING_PDF_COLOR_PRESET to options.colorPreset.name,
                    AppConstants.SETTING_PDF_DARK_MODE to options.darkMode.toString(),
                    AppConstants.SETTING_PDF_RULE_WEIGHT to options.ruleWeight.name,
                    AppConstants.SETTING_PDF_RULE_CONTRAST to options.ruleContrast.name,
                    AppConstants.SETTING_PDF_ROW_SHADING to options.rowShading.name
                ))
                clearPdfReviews()
                _state.update { it.copy(pdfStyle = style, pdfOptions = options) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_report_preview_view_model_010)) }
            } finally {
                _state.update { it.copy(savingPdfOptions = false) }
            }
        }
    }

    private suspend fun prepareReviewedReport(reviewed: BuiltReport, forceLocal: Boolean): BuiltReport {
        activeSendingActor()
        val beforeSync = reportBuilder.build(shiftId)
        check(beforeSync.shift.date == ShiftDate.current()) {
            uiText(R.string.ui_report_preview_view_model_011)
        }
        ReportReview.requireMatches(reviewed, beforeSync)
        // Registry fetch/publication is explicit; publication validates current published identities.
        activeSendingActor()
        ReportReview.requireMatches(reviewed, reportBuilder.build(shiftId))
        val publication = syncService.prepareCsvPublication(shiftId, forceLocal)
        ReportReview.requireMatches(reviewed, reportBuilder.build(shiftId))
        _state.update { it.copy(publicationOutcome = ReportStepOutcome.IN_PROGRESS) }
        syncService.uploadReviewedCsv(publication, forceLocal).getOrThrow()
        _state.update { it.copy(publicationOutcome = ReportStepOutcome.CONFIRMED) }
        val accepted = reportBuilder.build(shiftId)
        ReportReview.requireMatches(reviewed, accepted)
        check(accepted.shift.date == ShiftDate.current()) { uiText(R.string.ui_report_preview_view_model_012) }
        return accepted
    }

    private suspend fun activeSendingActor(): com.hos.rushdpatients.data.model.Doctor {
        val session = sessionManager.current() ?: error(uiText(R.string.ui_report_preview_view_model_013))
        return doctorRepository.getActiveById(session.doctorId)
            ?: error(uiText(R.string.ui_report_preview_view_model_014))
    }

    private fun reloadChangedReport(error: ReportChangedSinceReviewException) {
        _state.update { it.copy(sending = false, retryAction = null, snackbar = null) }
        load(reviewNotice = error.message)
    }

    fun resolveDelivery(record: ReportDeliveryRecord, resolution: DeliveryResolution) {
        if (_state.value.sending) return
        viewModelScope.launch {
            try {
                deliveryLedger.resolve(record.id, record.status, resolution)
                _state.update { it.copy(snackbar = uiText(R.string.ui_report_preview_view_model_015), retryAction = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message ?: uiText(R.string.ui_report_preview_view_model_016), retryAction = null) } }
        }
    }

    private fun recordSendFailure(error: Exception, delivered: Int = 0, cancelled: Boolean = false) {
        _state.update { current ->
            val publication = if (current.publicationOutcome == ReportStepOutcome.IN_PROGRESS)
                ReportStepOutcome.UNCERTAIN else current.publicationOutcome
            val delivery = when {
                error is ReportDeliveryUncertainException || cancelled && current.deliveryOutcome == ReportStepOutcome.IN_PROGRESS -> ReportStepOutcome.UNCERTAIN
                delivered > 0 -> ReportStepOutcome.PARTIAL
                else -> ReportStepOutcome.NOT_STARTED
            }
            val next = when {
                delivery == ReportStepOutcome.UNCERTAIN -> uiText(R.string.ui_report_preview_view_model_017)
                publication == ReportStepOutcome.UNCERTAIN -> uiText(R.string.ui_report_preview_view_model_018)
                delivered > 0 -> uiText(R.string.ui_report_preview_view_model_019, delivered)
                publication == ReportStepOutcome.CONFIRMED -> uiText(R.string.ui_report_preview_view_model_020)
                else -> uiText(R.string.ui_report_preview_view_model_021)
            }
            current.copy(sending = false, publicationOutcome = publication, deliveryOutcome = delivery,
                doctorRegistryAction = (error as? DoctorRegistryActionRequired)?.action,
                error = if (error is DoctorRegistryActionRequired) error.message else next + "\n" + error.message.orEmpty(),
                retryAction = if (error is PdfReviewRequiredException && delivered == 0) ReportRetryAction.PREVIEW
                    else if (error is ReportDeliveryBlockedException || error is DoctorRegistryActionRequired) null
                    else if (!cancelled && delivered == 0 && publication != ReportStepOutcome.UNCERTAIN &&
                    delivery != ReportStepOutcome.UNCERTAIN) ReportRetryAction.SEND else null)
        }
    }

    fun sendSupervisorReports(supervisorIds: Set<String>, forceLocal: Boolean = false) {
        val current = _state.value
        val reviewed = reviewedReport ?: return
        if (current.loading || current.savingPdfOptions || current.savingDoctors || current.sending || current.previewingPdf || current.exportingLocalPdf ||
            current.sharingPdf || current.shift == null) return
        if (current.isReadOnly) {
            _state.update { it.copy(error = uiText(R.string.ui_report_preview_view_model_011)) }
            return
        }
        if (!NetworkStatus.isOnline(context)) {
            _state.update { it.copy(error = uiText(R.string.ui_report_preview_view_model_022), retryAction = null) }
            return
        }
        val selected = current.supervisorTargets.filter { it.doctorId in supervisorIds && it.chatId != null }
        if (selected.isEmpty()) {
            _state.update { it.copy(error = uiText(R.string.ui_report_preview_screen_004)) }
            return
        }
        _state.update { it.copy(sending = true, error = null, doctorRegistryAction = null, retryAction = null,
            publicationOutcome = ReportStepOutcome.NOT_STARTED, deliveryOutcome = ReportStepOutcome.NOT_STARTED) }
        viewModelScope.launch {
            var delivered = 0
            try {
                ReportReview.requireMatches(reviewed, reportBuilder.build(shiftId))
                for (target in selected) {
                    reportSender.checkSupervisorDelivery(reviewed.shift, ReportReview.supervisorFingerprint(reviewed, target.doctorId), requireNotNull(target.chatId))
                    checkedPdf(reviewed, target.doctorId)
                }
                val built = prepareReviewedReport(reviewed, forceLocal)
                val actor = activeSendingActor()
                val targets = buildSupervisorTargets(built).filter {
                    it.doctorId in supervisorIds && it.chatId != null
                }
                check(targets.size == selected.size) { uiText(R.string.ui_report_preview_view_model_023) }
                for (target in targets) {
                    _state.update { it.copy(deliveryOutcome = ReportStepOutcome.IN_PROGRESS) }
                    sendSupervisorPdf(built, target, actor)
                    delivered++
                }
                _state.update {
                    it.copy(sending = false, deliveryOutcome = ReportStepOutcome.CONFIRMED, snackbar = uiText(R.string.ui_report_preview_view_model_024, delivered),
                        lastOperation = uiText(R.string.ui_report_preview_view_model_025), retryAction = null)
                }
            } catch (e: ReportChangedSinceReviewException) {
                recordSendFailure(e, delivered)
                reloadChangedReport(e)
            } catch (e: CancellationException) {
                recordSendFailure(e, delivered, cancelled = true)
                throw e
            } catch (e: Exception) {
                recordSendFailure(e, delivered)
                _state.update { it.copy(retryAction = null) }
            }
        }
    }

    fun send(forceLocal: Boolean = false) {
        val current = _state.value
        val reviewed = reviewedReport ?: return
        if (current.loading || current.savingPdfOptions || current.savingDoctors || current.sending || current.previewingPdf || current.exportingLocalPdf ||
            current.sharingPdf || current.shift == null) return
        if (current.isReadOnly) {
            _state.update { it.copy(error = uiText(R.string.ui_report_preview_view_model_011)) }
            return
        }
        if (!NetworkStatus.isOnline(context)) {
            _state.update { it.copy(error = uiText(R.string.ui_ward_shift_operations_018), retryAction = ReportRetryAction.SEND) }
            return
        }
        _state.update { it.copy(sending = true, error = null, doctorRegistryAction = null, retryAction = null,
            publicationOutcome = ReportStepOutcome.NOT_STARTED, deliveryOutcome = ReportStepOutcome.NOT_STARTED) }
        viewModelScope.launch {
            try {
                reportSender.checkGeneralDelivery(reviewed.shift, ReportReview.fingerprint(reviewed), current.reportAsPdf)
                ReportReview.requireMatches(reviewed, reportBuilder.build(shiftId))
                if (current.reportAsPdf) checkedPdf(reviewed)
                val built = prepareReviewedReport(reviewed, forceLocal)
                val actor = activeSendingActor()
                _state.update { it.copy(deliveryOutcome = ReportStepOutcome.IN_PROGRESS) }
                if (current.reportAsPdf) sendPdf(built, actor) else sendText(built, actor)
                _state.update {
                    it.copy(sending = false, deliveryOutcome = ReportStepOutcome.CONFIRMED, snackbar = uiText(R.string.ui_report_preview_view_model_026),
                        lastOperation = uiText(R.string.ui_report_preview_view_model_027), retryAction = null)
                }
            } catch (e: ReportChangedSinceReviewException) {
                recordSendFailure(e)
                reloadChangedReport(e)
            } catch (e: CancellationException) {
                recordSendFailure(e, cancelled = true)
                throw e
            } catch (e: Exception) {
                recordSendFailure(e)
            }
        }
    }

    fun previewPdf() = localActions.previewPdf()

    fun previewSupervisorPdf(supervisorId: String) = localActions.previewSupervisorPdf(supervisorId)

    private suspend fun checkedPdf(built: BuiltReport, supervisorId: String? = null): File = pdfWorkspace.checkedPdf(built, supervisorId)

    fun consumePdfPreview() = localActions.consumePdfPreview()

    fun sharePdf() = localActions.sharePdf()

    fun consumePdfShare() = localActions.consumePdfShare()

    fun retryLastAction() {
        when (_state.value.retryAction) {
            ReportRetryAction.SEND -> Unit // The screen reopens destination-specific confirmation.
            ReportRetryAction.PREVIEW -> previewPdf()
            ReportRetryAction.SAVE -> exportLocally()
            ReportRetryAction.SHARE -> sharePdf()
            ReportRetryAction.ASSIGNMENTS -> exportAssignments()
            null -> Unit
        }
    }

    fun exportAssignments() = localActions.exportAssignments()

    fun exportLocally() = localActions.exportLocally()

    fun onPdfViewerUnavailable() = localActions.onPdfViewerUnavailable()

    private suspend fun sendText(built: BuiltReport, actor: com.hos.rushdpatients.data.model.Doctor?) {
        reportSender.sendTextReport(
            shift = built.shift,
            chunks = built.textChunks,
            actor = actor, contentKey = ReportReview.fingerprint(built)
        )
    }

    private suspend fun sendPdf(built: BuiltReport, actor: com.hos.rushdpatients.data.model.Doctor?) {
        withContext(Dispatchers.IO) {
            val tmp = checkedPdf(built)
            run {
                val separate = settingsRepository.getBoolean(
                    AppConstants.SETTING_PDF_SEPARATE_BY_SUPERVISOR,
                    false
                )
                if (separate) {
                    saveSupervisorPdfs(built, loadPdfOptions())
                } else {
                    val savedUri = mediaStoreSaver.savePdf(
                        context = context,
                        sourceFile = tmp,
                        displayName = reportFileName(built.shift.date)
                    )
                    checkNotNull(savedUri) { uiText(R.string.ui_report_preview_view_model_028) }
                }

                val caption = Markdown.escape(uiText(R.string.ui_report_preview_view_model_029, built.shift.date, built.doctors.size))
                reportSender.sendPdfReport(
                    shift = built.shift,
                    pdfFile = tmp,
                    caption = caption,
                    actor = actor, contentKey = ReportReview.fingerprint(built)
                )
            }
        }
    }

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    private suspend fun loadPdfOptions(): PdfExportOptions = pdfWorkspace.loadPdfOptions()

    private fun buildSupervisorTargets(built: BuiltReport): List<SupervisorReportTarget> =
        built.patients
            .groupBy { it.responsibleSpecialistId }
            .mapNotNull { (doctorId, patients) ->
                val id = doctorId ?: return@mapNotNull null
                val doctor = built.registryDoctors.firstOrNull { it.id == id && !it.isDeleted }
                    ?: return@mapNotNull null
                if (!doctor.clinicalRole.canBeSupervisor()) return@mapNotNull null
                SupervisorReportTarget(
                    doctorId = doctor.id,
                    doctorName = doctor.fullName,
                    chatId = doctor.supervisorGroupChatId,
                    patientCount = patients.size
                )
            }
            .sortedBy { it.doctorName }

    private suspend fun sendSupervisorPdf(
        built: BuiltReport, target: SupervisorReportTarget,
        actor: com.hos.rushdpatients.data.model.Doctor?
    ) {
        val supervisor = built.registryDoctors.firstOrNull { it.id == target.doctorId && !it.isDeleted }
            ?: error(uiText(R.string.ui_report_preview_view_model_030))
        val reviewedFile = checkedPdf(built, target.doctorId)
        reportSender.sendSupervisorPdfReport(shift = built.shift, pdfFile = reviewedFile,
            caption = textReportBuilder.buildTitle(built.shift, listOf(supervisor)), actor = actor,
            supervisor = supervisor, chatId = requireNotNull(target.chatId),
            contentKey = ReportReview.supervisorFingerprint(built, target.doctorId))
    }

    private suspend fun saveSupervisorPdfs(
        built: BuiltReport,
        options: PdfExportOptions
    ): Int = pdfWorkspace.saveSupervisorPdfs(built, options)

    private fun reportFileName(date: java.time.LocalDate, suffix: String? = null): String = pdfWorkspace.reportFileName(date, suffix)

}
