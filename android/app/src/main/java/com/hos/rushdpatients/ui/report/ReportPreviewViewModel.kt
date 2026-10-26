package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.ui.components.readinessWarnings
import com.hos.rushdpatients.ui.components.failureText
import com.hos.rushdpatients.pdf.PdfRuleWeight
import com.hos.rushdpatients.pdf.PdfRuleContrast
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.network.telegram.TelegramException
import com.hos.rushdpatients.data.model.Doctor
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

    private val assignmentsOnly = savedStateHandle.get<Boolean>("assignmentsOnly") == true
    private val shiftId: String = savedStateHandle.get<String>("shiftId").orEmpty()

    private val _state = MutableStateFlow(ReportUiState(loading = false))
    val state: StateFlow<ReportUiState> = _state.asStateFlow()
    private var confirmedExportKey: String? = null
    private var confirmedExportActor: String? = null
    private var confirmedExportTelegram: Long? = null
    private var confirmedExportProject: com.hos.rushdpatients.config.ProjectConfig? = null
    private var reviewedProject: com.hos.rushdpatients.config.ProjectConfig? = null
    private var reviewedReport: BuiltReport? = null
    private var reviewedActor: com.hos.rushdpatients.domain.auth.Session? = null
    private val pdfWorkspace = ReportPdfWorkspace(uiText, context, settingsRepository, projectConfigStore,
        classicPdfExporter, cardsPdfExporter, mediaStoreSaver)

    private val localActions = ReportLocalActions(uiText, _state, viewModelScope,
        context, settingsRepository, assignmentPdfExporter, mediaStoreSaver,
        adminAuthorizer, pdfWorkspace, { reviewedReport }, ::validateExport)

    init {
        viewModelScope.launch {
            deliveryLedger.observe().catch {
                _state.update { it.copy(error = uiText(R.string.delivery_log_unavailable), retryAction = null) }
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
            pdfPreviewUri = null, pdfReviewUris = emptyMap(), openedPdfUris = emptySet()) }
    }

    fun load(reviewNotice: String? = null) {
        if (_state.value.isBusy) return
        clearPdfReviews()
        reviewedReport = null
        _state.update { it.copy(loading = true, summary = null, error = null, reviewNotice = reviewNotice, exportUploadConfirmed = false) }
        viewModelScope.launch {
            try {
                check(shiftId.isNotBlank()) { uiText(R.string.report_shift_id_missing) }
                val pdfOptions = loadPdfOptions()
                val pdfStyle = PdfStyle.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_STYLE))
                _state.update { it.copy(pdfOptions = pdfOptions, pdfStyle = pdfStyle) }
                // Keep the selector available even when an empty roster prevents report building.
                val shift = requireNotNull(shiftRepository.getById(shiftId)) { uiText(R.string.ui_ward_shift_operations_005) }
                val available = doctorRepository.getAll().filterNot { it.isDeleted }
                _state.update { it.copy(shift = shift, availableDoctors = available,
                    doctors = available.filter { doctor -> doctor.id in shift.doctorIds },
                    error = null) }
                if (assignmentsOnly || _state.value.doctors.isEmpty()) {
                    val assignments = reportBuilder.buildAssignments(shiftId)
                    reviewedReport = assignments
                    reviewedActor = sessionManager.current()
                    reviewedProject = projectConfigStore.current()
                    _state.update { it.copy(loading = false, patients = assignments.patients, supervisorTargets = emptyList(),
                        previewMarkdown = "", error = null, doctorRegistryAction = null) }
                    return@launch
                }
                val built = reportBuilder.build(shiftId)
                val asPdf = settingsRepository.isReportAsPdf()
                val supervisorTargets = buildSupervisorTargets(built)
                val changes = syncService.describeLocalChanges(shiftId)
                val briefing = buildList {
                    changes.added.forEach { add(uiText(R.string.report_patient_added, it)) }
                    changes.changed.forEach { add(uiText(R.string.report_patient_changed, it)) }
                    changes.removed.forEach { add(uiText(R.string.report_patient_removed, it)) }
                    if (changes.settingsChanged) add(uiText(R.string.report_shift_settings_changed))
                }
                reviewedReport = built
                reviewedActor = sessionManager.current()
                reviewedProject = projectConfigStore.current()
                _state.update {
                    it.copy(
                        loading = false,
                        shift = built.shift,
                        patients = built.patients,
                        doctors = built.doctors,
                        supervisorTargets = supervisorTargets,
                        summary = built.summary,
                        previewMarkdown = built.textChunks.joinToString("\n\n") {
                            Markdown.toPlainText(it)
                        },
                        readinessWarnings = readinessWarnings(uiText, built.patients),
                        readinessIssues = ReportReadiness.issues(built.patients),
                        changeBriefing = briefing,
                        registryChangeSummary = built.registryChangeSummary?.summary?.let { summary ->
                            summary + "\n" + built.registryDoctors.filterNot { it.isDeleted }.joinToString("\n") {
                                it.fullName + " · " + it.clinicalRole.arabicLabel
                            }
                        },
                        reportAsPdf = asPdf,
                        exportUploadConfirmed = confirmedExportKey == ReportReview.exportFingerprint(built) &&
                            confirmedExportActor == reviewedActor?.doctorId && confirmedExportTelegram == reviewedActor?.telegramId &&
                            confirmedExportProject == reviewedProject
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(loading = false) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = failureText(uiText, e) ?: uiText(R.string.report_build_failed))
                }
            }
        }
    }

    fun fetchDoctorsForReport() {
        val current = _state.value
        if (current.isBusy) return
        _state.update { it.copy(fetchingDoctors = true, error = null, doctorRegistryAction = null) }
        viewModelScope.launch {
            var fetched = false
            try {
                syncService.refreshDoctorsFromRemote().getOrThrow()
                clearPdfReviews()
                reviewedReport = null
                fetched = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.update { it.copy(error = failureText(uiText, e) ?: uiText(R.string.report_doctors_fetch_failed),
                    doctorRegistryAction = (e as? DoctorRegistryActionRequired)?.action) }
            } finally { _state.update { it.copy(fetchingDoctors = false) } }
            if (fetched) load(uiText(R.string.report_doctors_fetched_review_again))
        }
    }

    fun setShiftDoctors(
        ids: List<String>, onSuccess: () -> Unit
    ) {
        val current = _state.value
        if (current.isBusy) return
        if (current.shift == null) return
        _state.update { it.copy(savingDoctors = true, error = null, doctorRegistryAction = null) }
        viewModelScope.launch {
            try {
                activeSendingActor()
                wardMutations.setShiftDoctors(shiftId, ids, sessionManager.current())
                reviewedReport = null
                _state.update { it.copy(savingDoctors = false) }
                onSuccess()
                load()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = failureText(uiText, e) ?: uiText(R.string.report_roster_save_failed)) }
            } finally {
                _state.update { it.copy(savingDoctors = false) }
            }
        }
    }

    fun setReportAsPdf(value: Boolean) {
        val current = _state.value
        if (current.isBusy) return
        _state.update { it.copy(savingPdfOptions = true) }
        viewModelScope.launch {
            try {
                settingsRepository.setReportAsPdf(value)
                _state.update { it.copy(reportAsPdf = value) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = failureText(uiText, e) ?: uiText(R.string.report_appearance_save_failed)) }
            } finally {
                _state.update { it.copy(savingPdfOptions = false) }
            }
        }
    }

    fun setPdfAppearance(style: PdfStyle, requested: PdfExportOptions) {
        val current = _state.value
        if (current.isBusy) return
        val options = requested.copy(orientation = PdfOrientation.PORTRAIT, darkMode = false, ruleWeight = PdfRuleWeight.MEDIUM, ruleContrast = PdfRuleContrast.MEDIUM)
        _state.update { it.copy(savingPdfOptions = true) }
        viewModelScope.launch {
            try {
                settingsRepository.putAll(mapOf(
                    AppConstants.SETTING_PDF_STYLE to style.name,
                    AppConstants.SETTING_PDF_PAPER_SIZE to options.paperSize.name,
                    AppConstants.SETTING_PDF_COLOR_PRESET to options.colorPreset.name,
                    AppConstants.SETTING_PDF_ROW_SHADING to options.rowShading.name
                ))
                clearPdfReviews()
                _state.update { it.copy(pdfStyle = style, pdfOptions = options) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = failureText(uiText, e) ?: uiText(R.string.report_appearance_save_failed)) }
            } finally {
                _state.update { it.copy(savingPdfOptions = false) }
            }
        }
    }

    private suspend fun publishReviewedSnapshot(reviewed: BuiltReport) {
        val actor = activeSendingActor()
        val reviewer = reviewedActor
        require(reviewer != null && actor.id == reviewer.doctorId && actor.telegramId == reviewer.telegramId) { "تغير حساب المراجع؛ افتح التقرير بالحساب الذي راجعه" }
        val publicationProject = projectConfigStore.current()
        check(publicationProject == reviewedProject) { uiText(R.string.report_export_changed) }
        confirmedExportKey = null
        _state.update { it.copy(exportUploadConfirmed = false) }
        val publication = syncService.prepareCsvPublication(shiftId, reviewed)
        _state.update { it.copy(publicationOutcome = ReportStepOutcome.IN_PROGRESS) }
        val uploaded = syncService.uploadReviewedCsv(publication).getOrThrow()
        _state.update { it.copy(publicationWarning = if (uploaded.localBookkeepingFailed) uiText(R.string.sync_publication_bookkeeping_warning) else null) }
        confirmedExportKey = ReportReview.exportFingerprint(reviewed)
        confirmedExportActor = actor.id
        confirmedExportTelegram = actor.telegramId
        confirmedExportProject = publicationProject
        _state.update { it.copy(publicationOutcome = ReportStepOutcome.CONFIRMED, exportUploadConfirmed = true) }
    }

    /** A publication outcome alone never authorizes a different report, user or project. */
    private suspend fun validateExport(built: BuiltReport, draft: Boolean) {
        val actor = activeSendingActor()
        val valid = reviewedActor?.doctorId == actor.id && reviewedActor?.telegramId == actor.telegramId &&
            reviewedProject == projectConfigStore.current() &&
            ReportReview.exportFingerprint(built) == ReportReview.exportFingerprint(
                reportBuilder.buildAssignments(shiftId, reportDate = built.summary.shiftDate))
        if (!valid) {
            _state.update { it.copy(exportUploadConfirmed = false) }
            error(uiText(R.string.report_export_changed))
        }
        if (!draft) {
            val published = confirmedExportKey == ReportReview.exportFingerprint(built) &&
                confirmedExportActor == actor.id && confirmedExportTelegram == actor.telegramId &&
                confirmedExportProject == projectConfigStore.current()
            _state.update { it.copy(exportUploadConfirmed = published) }
            check(published) { uiText(R.string.report_export_upload_required) }
        }
    }

    fun previewExportPdf(route: String, draft: Boolean) = localActions.previewExportPdf(route, draft)
    fun saveExportPdfs(routes: Set<String>, draft: Boolean) = localActions.saveExportPdfs(routes, draft)
    fun shareExportPdf(route: String, draft: Boolean) = localActions.shareExportPdf(route, draft)

    private suspend fun activeSendingActor(): Doctor {
        val session = sessionManager.current() ?: error(uiText(R.string.report_login_required))
        return doctorRepository.getActiveById(session.doctorId)
            ?: error(uiText(R.string.report_actor_inactive))
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
                _state.update { it.copy(snackbar = uiText(R.string.delivery_resolution_saved), retryAction = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = failureText(uiText, e) ?: uiText(R.string.delivery_resolution_failed), retryAction = null) } }
        }
    }

    private fun recordSendFailure(error: Exception, delivered: Int = 0, cancelled: Boolean = false) {
        _state.update { current ->
            val failure = reportSendFailure(current.publicationOutcome, current.deliveryOutcome, delivered,
                definiteRejection = error is TelegramException && error.isDefiniteRejection,
                uncertainDelivery = error is ReportDeliveryUncertainException, cancelled = cancelled)
            val publication = failure.publication
            val delivery = failure.delivery
            val next = when {
                delivery == ReportStepOutcome.UNCERTAIN -> uiText(R.string.report_delivery_uncertain_help)
                publication == ReportStepOutcome.UNCERTAIN -> uiText(R.string.report_publication_uncertain_help)
                delivered > 0 -> uiText(R.string.report_partial_delivery_help, delivered)
                publication == ReportStepOutcome.CONFIRMED -> uiText(R.string.report_published_delivery_not_started)
                else -> uiText(R.string.report_delivery_not_started)
            }
            current.copy(sending = false, publicationOutcome = publication, deliveryOutcome = delivery,
                doctorRegistryAction = (error as? DoctorRegistryActionRequired)?.action,
                error = if (error is DoctorRegistryActionRequired) error.message else next + "\n" + failureText(uiText, error).orEmpty(),
                retryAction = if (error is PdfReviewRequiredException && delivered == 0) ReportRetryAction.PREVIEW
                    else if (error is ReportDeliveryBlockedException || error is DoctorRegistryActionRequired) null
                    else if (failure.canRetry) ReportRetryAction.SEND else null)
        }
    }

    fun sendSupervisorReports(supervisorIds: Set<String>) =
        sendSelectedReports(false, supervisorIds)

    fun send() = sendSelectedReports(true, emptySet())

    fun sendSelectedReports(includeGeneral: Boolean, supervisorIds: Set<String>, exportPdf: Boolean = false) {
        val current = _state.value
        val reviewed = reviewedReport ?: return
        if (current.isBusy || current.shift == null) return
        val selected = current.supervisorTargets.filter { it.doctorId in supervisorIds && it.chatId != null }
        if ((!includeGeneral && selected.isEmpty()) || selected.size != supervisorIds.size) {
            _state.update { it.copy(error = uiText(R.string.ui_report_no_destination)) }
            return
        }
        if (!NetworkStatus.isOnline(context)) {
            _state.update { it.copy(error = uiText(R.string.report_offline_retained), retryAction = ReportRetryAction.SEND) }
            return
        }
        _state.update { it.copy(sending = true, error = null, reviewNotice = null, doctorRegistryAction = null, retryAction = null,
            publicationOutcome = ReportStepOutcome.NOT_STARTED, deliveryOutcome = ReportStepOutcome.NOT_STARTED) }
        viewModelScope.launch {
            var delivered = 0
            var deliveryConfirmed = false
            try {
                // Check every selected route/artifact before publishing or sending any copy.
                if (includeGeneral) {
                    reportSender.checkGeneralDelivery(reviewed.shift, ReportReview.fingerprint(reviewed), current.reportAsPdf, ReportReview.legacyFingerprint(reviewed))
                    if (current.reportAsPdf) checkedPdf(reviewed)
                }
                for (target in selected) {
                    reportSender.checkSupervisorDelivery(reviewed.shift, ReportReview.supervisorFingerprint(reviewed, target.doctorId), requireNotNull(target.chatId), ReportReview.legacySupervisorFingerprint(reviewed, target.doctorId))
                    checkedPdf(reviewed, target.doctorId)
                }
                publishReviewedSnapshot(reviewed)
                val built = reviewed
                val actor = activeSendingActor()
                val targets = selected
                for (target in targets) {
                    val live = doctorRepository.getActiveById(target.doctorId)
                    require(live != null && live.supervisorGroupChatId == target.chatId && live.clinicalRole.canBeSupervisor()) {
                        uiText(R.string.report_supervisor_destination_changed)
                    }
                }
                if (includeGeneral) {
                    _state.update { it.copy(deliveryOutcome = ReportStepOutcome.IN_PROGRESS) }
                    if (current.reportAsPdf) sendPdf(built, actor) else sendText(built, actor)
                    delivered++
                }
                for (target in targets) {
                    _state.update { it.copy(deliveryOutcome = ReportStepOutcome.IN_PROGRESS) }
                    sendSupervisorPdf(built, target, actor)
                    delivered++
                }
                deliveryConfirmed = true
                _state.update {
                    it.copy(sending = exportPdf, deliveryOutcome = ReportStepOutcome.CONFIRMED,
                        snackbar = uiText(R.string.ui_report_destinations_delivered, delivered),
                        lastOperation = uiText(R.string.ui_report_destinations_delivered, delivered), retryAction = null)
                }
            } catch (e: ReportChangedSinceReviewException) {
                recordSendFailure(e, delivered)
                reloadChangedReport(e)
            } catch (e: CancellationException) {
                recordSendFailure(e, delivered, cancelled = true)
                throw e
            } catch (e: Exception) {
                recordSendFailure(e, delivered)
            }
            // Local-copy failure cannot change a confirmed delivery into a send retry.
            if (deliveryConfirmed && exportPdf) {
                try {
                    savePublishedPdfs(reviewed, includeGeneral && current.reportAsPdf, selected.map { it.doctorId })
                } catch (e: CancellationException) {
                    _state.update { it.copy(error = uiText(R.string.report_delivered_local_save_failed), retryAction = null) }
                    throw e
                } catch (_: Exception) {
                    _state.update { it.copy(error = uiText(R.string.report_delivered_local_save_failed), retryAction = null) }
                } finally { _state.update { it.copy(sending = false) } }
            }
        }
    }

    fun previewPdf() = localActions.previewPdf()

    fun previewSupervisorPdf(supervisorId: String) = localActions.previewSupervisorPdf(supervisorId)

    private suspend fun checkedPdf(built: BuiltReport, supervisorId: String? = null): File = pdfWorkspace.checkedPdf(built, supervisorId)

    fun onPdfPreviewOpened(uri: android.net.Uri) = localActions.onPdfPreviewOpened(uri)

    fun sharePdf() = localActions.sharePdf()

    fun consumePdfShare() = localActions.consumePdfShare()

    fun retryLastAction(onReviewSend: () -> Unit) {
        when (_state.value.retryAction) {
            ReportRetryAction.SEND -> onReviewSend()
            ReportRetryAction.PREVIEW -> previewPdf()
            ReportRetryAction.SAVE -> exportLocally()
            ReportRetryAction.SAVE_SUPERVISORS -> exportSupervisorPdfs()
            ReportRetryAction.SHARE -> sharePdf()
            ReportRetryAction.ASSIGNMENTS -> exportAssignments()
            null -> Unit
        }
    }

    fun exportAssignments() = localActions.exportAssignments()

    fun exportLocally() = localActions.exportLocally()

    fun exportSupervisorPdfs() = localActions.exportSupervisorPdfs()

    fun onPdfViewerUnavailable() = localActions.onPdfViewerUnavailable()

    private suspend fun sendText(built: BuiltReport, actor: Doctor?) {
        reportSender.sendTextReport(
            shift = built.shift,
            chunks = built.textChunks,
            actor = actor, contentKey = ReportReview.fingerprint(built), legacyContentKey = ReportReview.legacyFingerprint(built)
        )
    }

    private suspend fun savePublishedPdfs(built: BuiltReport, includeGeneral: Boolean, supervisors: List<String>) {
        withContext(Dispatchers.IO) {
            val files = buildList {
                if (includeGeneral) add(checkedPdf(built) to reportFileName(built.summary.shiftDate))
                supervisors.forEach { id ->
                    val name = built.registryDoctors.firstOrNull { it.id == id }?.fullName
                    add(checkedPdf(built, id) to pdfWorkspace.reportFileName(built.summary.shiftDate,
                        "supervisor:$id", supervisorName = name))
                }
            }
            files.forEach { (file, name) ->
                checkNotNull(mediaStoreSaver.savePdf(context, file, name, verifyContents = true)) {
                    uiText(R.string.report_pdf_save_failed)
                }
            }
        }
    }

    private suspend fun sendPdf(built: BuiltReport, actor: Doctor?) {
        val tmp = checkedPdf(built)
        val caption = Markdown.escape(uiText(R.string.report_pdf_caption, built.summary.shiftDate,
            built.doctors.joinToString(" / ") { it.fullName }))
        reportSender.sendPdfReport(shift = built.shift, pdfFile = tmp,
            fileName = reportFileName(built.summary.shiftDate), caption = caption, actor = actor,
            contentKey = ReportReview.fingerprint(built), legacyContentKey = ReportReview.legacyFingerprint(built))
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
        actor: Doctor?
    ) {
        val supervisor = built.registryDoctors.firstOrNull { it.id == target.doctorId && !it.isDeleted }
            ?: error(uiText(R.string.report_supervisor_missing))
        val reviewedFile = checkedPdf(built, target.doctorId)
        reportSender.sendSupervisorPdfReport(shift = built.shift, pdfFile = reviewedFile,
            caption = Markdown.escape(uiText(R.string.report_pdf_caption, built.summary.shiftDate,
                built.doctors.joinToString(" / ") { it.fullName }) + "\n" +
                uiText(R.string.report_pdf_supervisor_scope, supervisor.fullName)), actor = actor,
            fileName = pdfWorkspace.reportFileName(built.summary.shiftDate, "supervisor:${supervisor.id}",
                supervisorName = supervisor.fullName),
            supervisor = supervisor, chatId = requireNotNull(target.chatId),
            contentKey = ReportReview.supervisorFingerprint(built, target.doctorId),
            legacyContentKey = ReportReview.legacySupervisorFingerprint(built, target.doctorId))
    }

    private fun reportFileName(date: java.time.LocalDate): String = pdfWorkspace.reportFileName(date)

}
