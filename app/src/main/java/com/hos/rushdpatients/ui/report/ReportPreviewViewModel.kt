package com.hos.rushdpatients.ui.report

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
import com.hos.rushdpatients.domain.report.BuiltReport
import com.hos.rushdpatients.domain.report.ReportBuilder
import com.hos.rushdpatients.domain.report.ReportReview
import com.hos.rushdpatients.domain.report.ReportChangedSinceReviewException
import com.hos.rushdpatients.domain.report.ReportReadiness
import com.hos.rushdpatients.domain.report.TextReportBuilder
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import com.hos.rushdpatients.network.ReportSender
import com.hos.rushdpatients.network.telegram.Markdown
import com.hos.rushdpatients.pdf.MediaStoreSaver
import com.hos.rushdpatients.pdf.PdfCardsExporter
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.Locale
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
    private val mediaStoreSaver: MediaStoreSaver,
    private val settingsRepository: SettingsRepository,
    private val doctorRepository: DoctorRepository,
    private val shiftRepository: ShiftRepository,
    private val wardMutations: WardMutationRepository,
    private val sessionManager: SessionManager,
    private val syncService: SyncService
) : ViewModel() {

    private val shiftId: String = savedStateHandle.get<String>("shiftId").orEmpty()

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()
    private var reviewedReport: BuiltReport? = null

    init {
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
        reviewedReport = null
        viewModelScope.launch {
            _state.update { it.copy(loading = true, summary = null, error = reviewNotice) }
            try {
                check(shiftId.isNotBlank()) { "معرف الوردية غير موجود" }
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
        syncService.uploadCsv(shiftId).getOrThrow()
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
        _state.update { it.copy(sending = true, error = null, retryAction = null) }
        viewModelScope.launch {
            var delivered = 0
            try {
                val built = prepareReviewedReport(reviewed)
                val actor = activeSendingActor()
                val targets = buildSupervisorTargets(built).filter {
                    it.doctorId in supervisorIds && it.chatId != null
                }
                check(targets.size == selected.size) { "تغيّرت مجموعات المشرفين؛ أعد مراجعة الاختيار" }
                val options = loadPdfOptions()
                for (target in targets) {
                    sendSupervisorPdf(built, target, options, actor)
                    delivered++
                }
                _state.update {
                    it.copy(sending = false, snackbar = "تم إرسال $delivered تقرير PDF ونشر بيانات CSV الكاملة",
                        lastOperation = "آخر عملية ناجحة: إرسال تقارير المشرفين", retryAction = null)
                }
            } catch (e: ReportChangedSinceReviewException) {
                reloadChangedReport(e)
            } catch (e: CancellationException) {
                _state.update { it.copy(sending = false) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(sending = false,
                        error = (if (delivered > 0) "تم إرسال $delivered تقرير؛ توقف الإرسال. راجع المجموعات المستلمة قبل إعادة المحاولة: "
                            else "تعذر إرسال تقارير المشرفين: ") + e.message.orEmpty(),
                        retryAction = null)
                }
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
        _state.update { it.copy(sending = true, error = null, retryAction = null) }
        viewModelScope.launch {
            try {
                val built = prepareReviewedReport(reviewed)
                val actor = activeSendingActor()
                if (current.reportAsPdf) sendPdf(built, actor) else sendText(built, actor)
                _state.update {
                    it.copy(sending = false, snackbar = "تم إرسال التقرير ونشر أحدث بيانات المرضى",
                        lastOperation = "آخر عملية ناجحة: إرسال التقرير ونشر CSV", retryAction = null)
                }
            } catch (e: ReportChangedSinceReviewException) {
                reloadChangedReport(e)
            } catch (e: CancellationException) {
                _state.update { it.copy(sending = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, error = e.message ?: "فشل الإرسال", retryAction = ReportRetryAction.SEND) }
            }
        }
    }

    fun previewPdf() {
        val current = _state.value
        if (current.savingDoctors || current.previewingPdf || current.exportingLocalPdf || current.sharingPdf || current.sending || current.shift == null) return
        _state.update { it.copy(previewingPdf = true, error = null, retryAction = null) }
        viewModelScope.launch {
            try {
                val built = reportBuilder.build(shiftId)
                val names = built.doctorNames
                val previewDir = File(context.cacheDir, "pdf_previews").apply { mkdirs() }
                previewDir.listFiles()?.forEach { it.delete() }
                val previewFile = File.createTempFile("ward_report_preview_", ".pdf", previewDir)
                renderPdf(
                    patients = built.patients,
                    doctors = built.doctors,
                    summary = built.summary,
                    residentNames = names,
                    supervisorNames = names,
                    options = loadPdfOptions(),
                    outputFile = previewFile
                )
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    previewFile
                )
                _state.update { it.copy(previewingPdf = false, pdfPreviewUri = uri) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        previewingPdf = false,
                        error = e.message ?: "تعذر إنشاء معاينة PDF",
                        retryAction = ReportRetryAction.PREVIEW
                    )
                }
            }
        }
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
            null -> Unit
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

    fun onPdfViewerUnavailable() = _state.update {
        it.copy(pdfPreviewUri = null, error = "لا يوجد تطبيق مثبت لعرض ملفات PDF")
    }

    private suspend fun sendText(built: BuiltReport, actor: com.hos.rushdpatients.data.model.Doctor?) {
        reportSender.sendTextReport(
            shift = built.shift,
            chunks = built.textChunks,
            actor = actor
        )
    }

    private suspend fun sendPdf(built: BuiltReport, actor: com.hos.rushdpatients.data.model.Doctor?) {
        withContext(Dispatchers.IO) {
            val names = built.doctorNames

            val tmp = File(context.cacheDir, reportFileName(built.shift.date))
            try {
                renderPdf(
                    patients = built.patients,
                    doctors = built.doctors,
                    summary = built.summary,
                    residentNames = names,
                    supervisorNames = names,
                    options = loadPdfOptions(),
                    outputFile = tmp
                )

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
                    actor = actor
                )
            } finally {
                tmp.delete()
            }
        }
    }

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    private suspend fun loadPdfOptions(): PdfExportOptions = PdfExportOptions(
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
        outputFile: File
    ) {
        val style = PdfStyle.fromSetting(
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
        built: BuiltReport,
        target: SupervisorReportTarget,
        options: PdfExportOptions,
        actor: com.hos.rushdpatients.data.model.Doctor?
    ) {
        withContext(Dispatchers.IO) {
            val chatId = requireNotNull(target.chatId) { "لا توجد مجموعة للمشرف ${target.doctorName}" }
            val supervisor = built.registryDoctors.firstOrNull { it.id == target.doctorId && !it.isDeleted }
                ?: error("المشرف ${target.doctorName} غير موجود")
            val patients = built.patients.filter {
                it.responsibleSpecialistId == target.doctorId
            }
            require(patients.isNotEmpty()) { "لا يوجد مرضى للمشرف ${target.doctorName}" }
            val summary = built.summary.copy(
                patientCount = patients.size,
                psychoCount = patients.count {
                    it.diagnosisType == DiagnosisType.PSYCHIATRIC ||
                            it.diagnosisType == DiagnosisType.DUAL
                },
                escortCount = patients.count { it.hasCompanion },
                doctors = listOf(supervisor)
            )
            val tmp = File.createTempFile("rushd_group_report_", ".pdf", context.cacheDir)
            try {
                renderPdf(
                    patients = patients,
                    doctors = listOf(supervisor),
                    summary = summary,
                    residentNames = built.doctorNames,
                    supervisorNames = built.doctorNames,
                    options = options,
                    outputFile = tmp
                )
                reportSender.sendSupervisorPdfReport(
                    shift = built.shift,
                    pdfFile = tmp,
                    caption = textReportBuilder.buildTitle(built.shift, listOf(supervisor)),
                    actor = actor,
                    supervisor = supervisor,
                    chatId = chatId
                )
            } finally {
                tmp.delete()
            }
        }
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
