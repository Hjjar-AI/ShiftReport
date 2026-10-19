package com.hos.rushdpatients.ui.report

import com.hos.rushdpatients.config.ProjectConfigStore
import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.DiagnosisType
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
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.network.ReportSender
import com.hos.rushdpatients.network.ReportDeliveryUncertainException
import com.hos.rushdpatients.network.telegram.Markdown
import com.hos.rushdpatients.pdf.MediaStoreSaver
import com.hos.rushdpatients.pdf.PdfCardsExporter
import com.hos.rushdpatients.pdf.PdfAssignmentExporter
import com.hos.rushdpatients.pdf.PdfColorPreset
import com.hos.rushdpatients.pdf.PdfExportOptions
import com.hos.rushdpatients.pdf.PdfOrientation
import com.hos.rushdpatients.pdf.PdfPaperSize
import com.hos.rushdpatients.pdf.PdfReportExporter
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.sync.ConflictChoice
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
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ReportPreviewViewModel @Inject constructor(
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
    private data class ReviewedPdf(val file: File, val digest: String, val reportKey: String, val options: PdfExportOptions, val style: PdfStyle, val reviewed: BuiltReport)
    private val reviewedPdfs = java.util.concurrent.ConcurrentHashMap<String, ReviewedPdf>()
    private class PdfReviewRequiredException : IllegalStateException("عاين PDF لهذه الوجهة أولاً؛ تغيّر الملف أو البيانات أو خيارات PDF يتطلب معاينة جديدة")


    init {
        viewModelScope.launch {
            deliveryLedger.observe().catch {
                _state.update { it.copy(error = "تعذر فتح سجل التسليم؛ لن يبدأ إرسال جديد حتى إصلاح السجل", retryAction = null) }
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
        viewModelScope.launch {
            syncService.patientConflicts.collect { conflicts ->
                _state.update { it.copy(mergeConflicts = conflicts) }
            }
        }
    }

    fun resolveMergeConflicts(choices: Map<String, ConflictChoice>) {
        _state.update { it.copy(resolvingConflicts = true) }
        viewModelScope.launch {
            syncService.resolvePatientConflicts(choices)
                .onSuccess {
                    _state.update { state ->
                        state.copy(
                            resolvingConflicts = false,
                            snackbar = "تم الدمج والنشر. راجع التقرير ثم أرسله.",
                            retryAction = null
                        )
                    }
                    load()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(resolvingConflicts = false, error = error.message ?: "تعذر حل التعارض")
                    }
                }
        }
    }

    fun dismissMergeConflicts() = syncService.dismissPatientConflicts()

    fun load(reviewNotice: String? = null) {
        if (_state.value.sending || _state.value.savingDoctors) return
        reviewedPdfs.clear()
        reviewedReport = null
        viewModelScope.launch {
            _state.update { it.copy(loading = true, summary = null, error = reviewNotice) }
            try {
                check(shiftId.isNotBlank()) { "معرف المناوبة غير موجود" }
                // Keep the selector available even when an empty roster prevents report building.
                val shift = requireNotNull(shiftRepository.getById(shiftId)) { "المناوبة غير موجودة" }
                val available = doctorRepository.getAll().filterNot { it.isDeleted }
                _state.update { it.copy(shift = shift, availableDoctors = available,
                    doctors = available.filter { doctor -> doctor.id in shift.doctorIds },
                    isReadOnly = shift.date != ShiftDate.current()) }
                if (_state.value.doctors.isEmpty()) {
                    _state.update { it.copy(loading = false, previewMarkdown = "", error = null) }
                    return@launch
                }
                val built = reportBuilder.build(shiftId)
                val asPdf = settingsRepository.isReportAsPdf()
                val supervisorTargets = buildSupervisorTargets(built)
                val changes = syncService.describeLocalChanges(shiftId)
                val briefing = buildList {
                    changes.added.forEach { add("مريض جديد: $it") }
                    changes.changed.forEach { add("تم تعديل: $it") }
                    changes.removed.forEach { add("تمت إزالة: $it") }
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
                    it.copy(loading = false, error = e.message ?: "خطأ في بناء التقرير")
                }
            }
        }
    }

    fun setShiftDoctors(
        ids: List<String>, expectedRevision: Long,
        onStale: (Shift?) -> Unit, onSuccess: () -> Unit
    ) {
        val current = _state.value
        if (current.loading || current.savingDoctors || current.sending || current.previewingPdf ||
            current.exportingLocalPdf || current.sharingPdf || current.resolvingConflicts) return
        if (current.isReadOnly || current.shift == null) return
        _state.update { it.copy(savingDoctors = true, error = null) }
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
                _state.update { it.copy(snackbar = e.message ?: "تعذر حفظ أطباء المناوبة") }
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

    private suspend fun prepareReviewedReport(reviewed: BuiltReport): BuiltReport {
        activeSendingActor()
        val beforeSync = reportBuilder.build(shiftId)
        check(beforeSync.shift.date == ShiftDate.current()) {
            "لا يمكن إرسال أو نشر تقرير مناوبة محفوظة"
        }
        ReportReview.requireMatches(reviewed, beforeSync)
        // Synchronize doctor identities and destinations before publishing patient assignments.
        syncService.synchronizeDoctors().getOrThrow()
        activeSendingActor()
        ReportReview.requireMatches(reviewed, reportBuilder.build(shiftId))
        _state.update { it.copy(publicationOutcome = ReportStepOutcome.IN_PROGRESS) }
        syncService.uploadCsv(shiftId).getOrThrow()
        _state.update { it.copy(publicationOutcome = ReportStepOutcome.CONFIRMED) }
        val accepted = reportBuilder.build(shiftId)
        ReportReview.requireMatches(reviewed, accepted)
        check(accepted.shift.date == ShiftDate.current()) { "انتهت المناوبة؛ أعد مراجعة التقرير" }
        return accepted
    }

    private suspend fun activeSendingActor(): com.hos.rushdpatients.data.model.Doctor {
        val session = sessionManager.current() ?: error("يجب تسجيل الدخول قبل إرسال التقرير")
        return doctorRepository.getActiveById(session.doctorId)
            ?: error("المستخدم الحالي لم يعد نشطاً؛ أعد تسجيل الدخول")
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
                _state.update { it.copy(snackbar = "حُفظ قرار التسليم؛ أي إرسال جديد يمر بالمراجعة والتأكيد", retryAction = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message ?: "تعذر حسم التسليم", retryAction = null) } }
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
                delivery == ReportStepOutcome.UNCERTAIN -> "افحص وصول التقرير في تليجرام قبل إعادة الإرسال."
                publication == ReportStepOutcome.UNCERTAIN -> "لم يتأكد نشر البيانات؛ اجلب الحالة الحالية وراجعها قبل الإعادة."
                delivered > 0 -> "تم تأكيد $delivered تقارير؛ راجع الوجهات المستلمة ولا تُعد إرسالها."
                publication == ReportStepOutcome.CONFIRMED -> "البيانات منشورة؛ لم يبدأ تسليم التقرير. عالج السبب ثم راجع وأرسل."
                else -> "لم يبدأ تسليم التقرير؛ عالج السبب ثم راجع العملية."
            }
            current.copy(sending = false, publicationOutcome = publication, deliveryOutcome = delivery,
                error = (if (delivered > 0) "تم تأكيد $delivered تقارير. " else "") + next + "\n" + error.message.orEmpty(),
                retryAction = if (error is PdfReviewRequiredException && delivered == 0) ReportRetryAction.PREVIEW
                    else if (error is ReportDeliveryBlockedException) null
                    else if (!cancelled && delivered == 0 && publication != ReportStepOutcome.UNCERTAIN &&
                    delivery != ReportStepOutcome.UNCERTAIN) ReportRetryAction.SEND else null)
        }
    }

    fun sendSupervisorReports(supervisorIds: Set<String>) {
        val current = _state.value
        val reviewed = reviewedReport ?: return
        if (current.loading || current.savingDoctors || current.sending || current.previewingPdf || current.exportingLocalPdf ||
            current.sharingPdf || current.shift == null) return
        if (current.isReadOnly) {
            _state.update { it.copy(error = "لا يمكن إرسال أو نشر تقرير مناوبة محفوظة") }
            return
        }
        if (!NetworkStatus.isOnline(context)) {
            _state.update { it.copy(error = "لا يوجد اتصال بالإنترنت — بقي التقرير محفوظاً محلياً", retryAction = null) }
            return
        }
        val selected = current.supervisorTargets.filter { it.doctorId in supervisorIds && it.chatId != null }
        if (selected.isEmpty()) {
            _state.update { it.copy(error = "اختر مجموعة مشرف واحدة على الأقل") }
            return
        }
        _state.update { it.copy(sending = true, error = null, retryAction = null,
            publicationOutcome = ReportStepOutcome.NOT_STARTED, deliveryOutcome = ReportStepOutcome.NOT_STARTED) }
        viewModelScope.launch {
            var delivered = 0
            try {
                ReportReview.requireMatches(reviewed, reportBuilder.build(shiftId))
                for (target in selected) {
                    reportSender.checkSupervisorDelivery(reviewed.shift, ReportReview.supervisorFingerprint(reviewed, target.doctorId), requireNotNull(target.chatId))
                    checkedPdf(reviewed, target.doctorId)
                }
                val built = prepareReviewedReport(reviewed)
                val actor = activeSendingActor()
                val targets = buildSupervisorTargets(built).filter {
                    it.doctorId in supervisorIds && it.chatId != null
                }
                check(targets.size == selected.size) { "تغيّرت مجموعات المشرفين؛ أعد مراجعة الاختيار" }
                for (target in targets) {
                    _state.update { it.copy(deliveryOutcome = ReportStepOutcome.IN_PROGRESS) }
                    sendSupervisorPdf(built, target, actor)
                    delivered++
                }
                _state.update {
                    it.copy(sending = false, deliveryOutcome = ReportStepOutcome.CONFIRMED, snackbar = "تم إرسال $delivered تقرير PDF ونشر بيانات CSV الكاملة",
                        lastOperation = "آخر عملية ناجحة: إرسال تقارير المشرفين", retryAction = null)
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

    fun send() {
        val current = _state.value
        val reviewed = reviewedReport ?: return
        if (current.loading || current.savingDoctors || current.sending || current.previewingPdf || current.exportingLocalPdf ||
            current.sharingPdf || current.shift == null) return
        if (current.isReadOnly) {
            _state.update { it.copy(error = "لا يمكن إرسال أو نشر تقرير مناوبة محفوظة") }
            return
        }
        if (!NetworkStatus.isOnline(context)) {
            _state.update { it.copy(error = "لا يوجد اتصال بالإنترنت — أعد المحاولة عند توفر الشبكة", retryAction = ReportRetryAction.SEND) }
            return
        }
        _state.update { it.copy(sending = true, error = null, retryAction = null,
            publicationOutcome = ReportStepOutcome.NOT_STARTED, deliveryOutcome = ReportStepOutcome.NOT_STARTED) }
        viewModelScope.launch {
            try {
                reportSender.checkGeneralDelivery(reviewed.shift, ReportReview.fingerprint(reviewed), current.reportAsPdf)
                ReportReview.requireMatches(reviewed, reportBuilder.build(shiftId))
                if (current.reportAsPdf) checkedPdf(reviewed)
                val built = prepareReviewedReport(reviewed)
                val actor = activeSendingActor()
                _state.update { it.copy(deliveryOutcome = ReportStepOutcome.IN_PROGRESS) }
                if (current.reportAsPdf) sendPdf(built, actor) else sendText(built, actor)
                _state.update {
                    it.copy(sending = false, deliveryOutcome = ReportStepOutcome.CONFIRMED, snackbar = "تم إرسال التقرير ونشر أحدث بيانات المرضى",
                        lastOperation = "آخر عملية ناجحة: إرسال التقرير ونشر CSV", retryAction = null)
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

    fun previewPdf() = previewPdfFor(null)

    fun previewSupervisorPdf(supervisorId: String) = previewPdfFor(supervisorId)

    private fun previewPdfFor(supervisorId: String?) {
        val current = _state.value
        if (current.savingDoctors || current.previewingPdf || current.exportingLocalPdf || current.sharingPdf || current.sending || current.shift == null) return
        _state.update { it.copy(previewingPdf = true, error = null, retryAction = null) }
        viewModelScope.launch {
            try {
                val built = reportBuilder.build(shiftId)
                val prepared = withContext(Dispatchers.IO) {
                    val selected = if (supervisorId == null) built else supervisorPdfReport(built, supervisorId)
                    val options = loadPdfOptions()
                    val style = PdfStyle.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_STYLE))
                    val dir = File(context.cacheDir, "pdf_previews").apply { mkdirs() }
                    val file = File.createTempFile("reviewed_report_", ".pdf", dir)
                    try {
                        renderPdf(selected.patients, selected.doctors, selected.summary,
                            selected.doctorNames, selected.doctorNames, options, file, style)
                        ReviewedPdf(file, pdfDigest(file), ReportReview.fingerprint(built), options, style, built)
                    } catch (e: Exception) { file.delete(); throw e }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", prepared.file)
                reviewedPdfs[pdfKey(supervisorId)] = prepared
                _state.update { it.copy(previewingPdf = false, pdfPreviewUri = uri) }
            } catch (e: CancellationException) {
                reviewedPdfs.remove(pdfKey(supervisorId))
                _state.update { it.copy(previewingPdf = false) }; throw e
            } catch (e: Exception) {
                reviewedPdfs.remove(pdfKey(supervisorId))
                _state.update { it.copy(previewingPdf = false, error = e.message ?: "تعذر إنشاء معاينة PDF",
                    retryAction = if (supervisorId == null) ReportRetryAction.PREVIEW else null) }
            }
        }
    }

    private fun pdfKey(supervisorId: String?) = supervisorId?.let { "supervisor:$it" } ?: "general"

    private fun pdfDigest(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private suspend fun checkedPdf(built: BuiltReport, supervisorId: String? = null): File = withContext(Dispatchers.IO) {
        val artifact = reviewedPdfs[pdfKey(supervisorId)] ?: throw PdfReviewRequiredException()
        ReportReview.requireMatches(artifact.reviewed, built)
        if (artifact.style != PdfStyle.fromSetting(settingsRepository.get(AppConstants.SETTING_PDF_STYLE)) ||
            artifact.reportKey != ReportReview.fingerprint(built) ||
            artifact.options.copy(generatedAtEpochMillis = 0) != loadPdfOptions().copy(generatedAtEpochMillis = 0) ||
            !artifact.file.isFile || pdfDigest(artifact.file) != artifact.digest) throw PdfReviewRequiredException()
        artifact.file
    }

    private fun supervisorPdfReport(built: BuiltReport, supervisorId: String): BuiltReport {
        val supervisor = built.registryDoctors.firstOrNull { it.id == supervisorId && !it.isDeleted }
            ?: error("المشرف غير موجود")
        val patients = built.patients.filter { it.responsibleSpecialistId == supervisorId }
        require(patients.isNotEmpty()) { "لا يوجد مرضى لهذا المشرف" }
        return built.copy(patients = patients, doctors = listOf(supervisor), summary = built.summary.copy(
            patientCount = patients.size, psychoCount = patients.count { it.diagnosisType == DiagnosisType.PSYCHIATRIC || it.diagnosisType == DiagnosisType.DUAL },
            escortCount = patients.count { it.hasCompanion }, doctors = listOf(supervisor)))
    }

    fun consumePdfPreview() = _state.update { it.copy(pdfPreviewUri = null) }

    fun sharePdf() {
        val current = _state.value
        if (current.savingDoctors || current.sharingPdf || current.exportingLocalPdf || current.previewingPdf ||
            current.sending || current.shift == null
        ) return
        _state.update { it.copy(sharingPdf = true, error = null, retryAction = null) }
        viewModelScope.launch {
            try {
                val built = reportBuilder.build(shiftId)
                val shareDir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
                shareDir.listFiles()?.forEach { it.delete() }
                val file = File(shareDir, reportFileName(built.shift.date))
                renderPdf(
                    patients = built.patients,
                    doctors = built.doctors,
                    summary = built.summary,
                    residentNames = built.doctorNames,
                    supervisorNames = built.doctorNames,
                    options = loadPdfOptions(),
                    outputFile = file
                )
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                _state.update {
                    it.copy(
                        sharingPdf = false,
                        pdfShareUri = uri,
                        lastOperation = "آخر عملية ناجحة: تجهيز PDF للمشاركة",
                        retryAction = null
                    )
                }
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

    fun retryLastAction() {
        when (_state.value.retryAction) {
            ReportRetryAction.SEND -> send()
            ReportRetryAction.PREVIEW -> previewPdf()
            ReportRetryAction.SAVE -> exportLocally()
            ReportRetryAction.SHARE -> sharePdf()
            ReportRetryAction.ASSIGNMENTS -> exportAssignments()
            null -> Unit
        }
    }

    fun exportAssignments() {
        val current = _state.value
        if (current.loading || current.savingDoctors || current.exportingLocalPdf || current.previewingPdf ||
            current.sharingPdf || current.sending || current.resolvingConflicts || current.shift == null) return
        _state.update { it.copy(exportingLocalPdf = true, error = null, retryAction = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val actorId = adminAuthorizer.requireAdmin().id
                    val built = reportBuilder.buildAssignments(shiftId)
                    val tmp = File.createTempFile("rushd_assignments_", ".pdf", context.cacheDir)
                    try {
                        assignmentPdfExporter.export(built.patients, built.doctorNames, built.registryDoctors, built.summary,
                            loadPdfOptions(), tmp)
                        require(adminAuthorizer.requireAdmin().id == actorId) { "تغيّر المستخدم؛ أعد تصدير توزيع المرضى" }
                        checkNotNull(mediaStoreSaver.savePdf(context, tmp, reportFileName(built.shift.date, "Names"),
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
        if (current.savingDoctors || current.exportingLocalPdf || current.previewingPdf || current.sharingPdf || current.sending || current.shift == null) return
        _state.update { it.copy(exportingLocalPdf = true, error = null, retryAction = null) }
        viewModelScope.launch {
            try {
                val built = reportBuilder.build(shiftId)
                val options = loadPdfOptions()
                val separate = settingsRepository.getBoolean(
                    AppConstants.SETTING_PDF_SEPARATE_BY_SUPERVISOR,
                    false
                )
                val count = if (separate) {
                    saveSupervisorPdfs(built, options)
                } else {
                    saveCombinedPdf(built, options)
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
        reviewedPdfs.clear()
        _state.update { it.copy(pdfPreviewUri = null, error = "لا يوجد تطبيق مثبت لعرض ملفات PDF؛ لم تعتمد المعاينة") }
    }

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
                    checkNotNull(savedUri) { "تعذر حفظ ملف PDF" }
                }

                val caption = Markdown.escape("تقرير المناوبة: ${built.shift.date} · ${built.doctors.size} أطباء؛ الأسماء داخل الملف")
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

    private suspend fun loadPdfOptions(): PdfExportOptions = PdfExportOptions(
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
        patientCardStyle = PatientCardStyle.fromSetting(
            settingsRepository.get(AppConstants.SETTING_PDF_PATIENT_CARD_STYLE)
        )
    )

    private suspend fun renderPdf(
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
            ?: error("المشرف غير موجود")
        val reviewedFile = checkedPdf(built, target.doctorId)
        reportSender.sendSupervisorPdfReport(shift = built.shift, pdfFile = reviewedFile,
            caption = textReportBuilder.buildTitle(built.shift, listOf(supervisor)), actor = actor,
            supervisor = supervisor, chatId = requireNotNull(target.chatId),
            contentKey = ReportReview.supervisorFingerprint(built, target.doctorId))
    }

    private suspend fun saveCombinedPdf(built: BuiltReport, options: PdfExportOptions) {
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

    private suspend fun saveSupervisorPdfs(
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

    private fun reportFileName(date: java.time.LocalDate, suffix: String? = null): String {
        val created = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val extra = suffix?.takeIf { it.isNotBlank() }?.let { "_$it" }.orEmpty()
        return "Rushd_Report_${date}_${created}$extra.pdf"
    }
}
