package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.domain.task.PatientTasks
import android.content.Context
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.WardMutationRepository
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.export.PatientCsvExporter
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.util.NetworkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.Instant

/** Uses the owning ViewModel state/scope; repository guards remain unchanged. */
internal class WardShiftOperations(
    private val _state: MutableStateFlow<WardUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val context: Context,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val wardMutations: WardMutationRepository,
    private val syncService: SyncService,
    private val settingsRepository: SettingsRepository,
    private val csvExporter: PatientCsvExporter,
    private val sessionManager: SessionManager,
    private val saveGuard: WardSaveGuard,
    private val observeShift: (String) -> Unit
) {

    fun bringLatestData() = fetchRemoteData(previous = false)

    suspend fun rolloverSourceLabel(source: com.hos.rushdpatients.data.model.Shift): String {
        val at = settingsRepository.get(AppConstants.SETTING_PATIENT_DOWNLOAD_AT_PREFIX + source.id)?.toLongOrNull()
        val message = settingsRepository.get(AppConstants.SETTING_PATIENT_DOWNLOAD_MESSAGE_PREFIX + source.id)?.toLongOrNull()
        val downloaded = if (at != null && message != null && source.csvMessageId == message) {
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
                .format(java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()))
        } else "غير معروف؛ قد تكون نسخة محلية لم تُنزّل على هذا الجهاز"
        return "المصدر: مناوبة ${source.date} المحفوظة على هذا الجهاز\nآخر تنزيل لهذه الحزمة: $downloaded\n" +
            "لم يُفحص تليجرام الآن؛ قد توجد نسخة أحدث. ألغِ واجلب أحدث البيانات أولاً إذا أردت تحديث المصدر."
    }

    fun offerPreviousShift() {
        val shiftId = saveGuard.editableShiftId() ?: return
        if (_state.value.saving || _state.value.syncing) return
        viewModelScope.launch {
            try {
                require(patientRepository.countForShift(shiftId) == 0) { "مناوبة اليوم تحتوي مرضى؛ لن تُستبدل" }
                val shift = shiftRepository.getById(shiftId) ?: error("المناوبة غير موجودة")
                val previous = shiftRepository.getRecent(12).firstOrNull { it.date < shift.date }
                val candidates = previous?.let { patientRepository.getForShift(it.id) }.orEmpty()
                require(candidates.isNotEmpty()) { "لا توجد مناوبة سابقة تحتوي مرضى لنسخهم؛ يمكنك جلب البيانات من تليجرام أولاً" }
                val sourceLabel = rolloverSourceLabel(requireNotNull(previous))
                _state.update { it.copy(rolloverPatients = candidates, rolloverTarget = shift, rolloverSourceLabel = sourceLabel) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(snackbar = e.message ?: "تعذر تحضير المناوبة الجديدة") } }
        }
    }

    fun dismissRollover() = _state.update { it.copy(rolloverPatients = emptyList(), rolloverTarget = null, rolloverSourceLabel = "") }

    fun applyRollover(keepAssignments: Boolean) {
        val shiftId = saveGuard.editableShiftId() ?: return
        if (_state.value.saving || _state.value.syncing) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            saveGuard.saveAction(onSuccess = {}) {
                val expected = requireNotNull(_state.value.rolloverTarget) { "أعد فتح نسخ المناوبة" }
                require(expected.id == shiftId) { "تغيرت المناوبة؛ أعد فتح النسخ" }
                val candidates = _state.value.rolloverPatients
                require(candidates.isNotEmpty()) { "لا توجد سجلات لنسخها" }
                val selected = candidates.mapIndexed { index, patient ->
                    patient.copy(
                        id = UUID.randomUUID().toString(),
                        tasks = PatientTasks.carryForward(patient.tasks, clearOwners = !keepAssignments),
                        sortOrder = index + 1, revision = 1, updatedAt = Instant.now(),
                        lastEditedByDoctorId = sessionManager.current()?.doctorId,
                        lastEditedByName = sessionManager.current()?.doctorName,
                        responsibleResidentId = patient.responsibleResidentId.takeIf { keepAssignments },
                        responsibleSpecialistId = patient.responsibleSpecialistId.takeIf { keepAssignments }
                    )
                }
                wardMutations.rollover(selected, shiftId, expected.revision,
                    candidates.map { it to if (keepAssignments) "continue" else "reassign" }, sessionManager.current())
                _state.update { it.copy(rolloverPatients = emptyList(), rolloverTarget = null, rolloverSourceLabel = "",
                    snackbar = "نُسخ جميع المرضى (${uiNumber(selected.size)}) محلياً؛ حدد أطباء المناوبة ثم راجع وانشر") }
            }
        }
    }

    fun bringPreviousData() = fetchRemoteData(previous = true)

    fun showDownloadedSnapshots() {
        if (_state.value.syncing) return
        viewModelScope.launch {
            val downloaded = shiftRepository.getRecent(12)
                .filter { it.csvMessageId != null }
            val latestBundleMessageId = downloaded.maxOfOrNull { it.csvMessageId ?: Long.MIN_VALUE }
            val choices = downloaded
                .filter { it.csvMessageId == latestBundleMessageId }
                .sortedByDescending { it.date }
                .take(AppConstants.CSV_BUNDLE_SHIFT_COUNT)
                .map { shift ->
                    ShiftSnapshotOption(
                        shiftId = shift.id,
                        date = shift.date,
                        patientCount = patientRepository.countForShift(shift.id)
                    )
                }
            if (choices.isEmpty()) {
                _state.update {
                    it.copy(snackbar = "نزّل أحدث ملف أولاً لعرض المناوبات المحفوظة فيه")
                }
            } else {
                _state.update { it.copy(snapshotChoices = choices, showSnapshotPicker = true) }
            }
        }
    }

    fun dismissSnapshotPicker() {
        _state.update { it.copy(showSnapshotPicker = false) }
    }

    fun selectDownloadedSnapshot(shiftId: String) {
        val selected = _state.value.snapshotChoices.firstOrNull { it.shiftId == shiftId } ?: return
        observeShift(shiftId)
        _state.update {
            it.copy(
                showSnapshotPicker = false,
                snackbar = "تم عرض مناوبة ${selected.date}"
            )
        }
    }

    fun exportCsv() {
        if (_state.value.exportingCsv) return
        val shiftId = _state.value.shift?.id
        if (shiftId == null) {
            _state.update { it.copy(snackbar = "لا توجد وردية للتصدير") }
            return
        }
        _state.update { it.copy(exportingCsv = true, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            try {
                when (val result = csvExporter.exportShift(shiftId)) {
                    is PatientCsvExporter.Result.Success -> _state.update {
                        it.copy(
                            exportingCsv = false,
                            snackbar = "تم حفظ ${result.patientCount} مريض في ${result.fileName}",
                            lastOperation = "آخر عملية ناجحة: حفظ ${result.fileName}"
                        )
                    }
                    is PatientCsvExporter.Result.Failure -> _state.update {
                        it.copy(
                            exportingCsv = false,
                            snackbar = result.message,
                            retryAction = WardRetryAction.EXPORT_CSV
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        exportingCsv = false,
                        snackbar = e.message ?: "فشل تصدير CSV",
                        retryAction = WardRetryAction.EXPORT_CSV
                    )
                }
            }
        }
    }

    private fun fetchRemoteData(previous: Boolean) {
        if (_state.value.syncing) return
        val retry = if (previous) WardRetryAction.FETCH_PREVIOUS
        else WardRetryAction.FETCH_LATEST
        if (!NetworkStatus.isOnline(context)) {
            _state.update {
                it.copy(
                    snackbar = "لا يوجد اتصال بالإنترنت — أعد المحاولة عند توفر الشبكة",
                    retryAction = retry
                )
            }
            return
        }
        _state.update {
            it.copy(
                syncing = true,
                snackbar = null,
                retryAction = null
            )
        }
        viewModelScope.launch {
            // Refresh the complete registry; pending local changes require explicit handling.
            val doctorsResult = syncService.refreshDoctorsFromRemote()
            if (doctorsResult.isFailure) {
                _state.update {
                    it.copy(
                        syncing = false,
                        snackbar = doctorsResult.exceptionOrNull()?.message
                            ?: "فشل جلب سجل الأطباء",
                        retryAction = retry
                    )
                }
                return@launch
            }

            val result = if (previous) {
                syncService.fetchPreviousCsv()
            } else {
                syncService.fetchLatestCsv()
            }
            result.onSuccess { fetched ->
                observeShift(fetched.shiftId)
                val source = if (previous) "النسخة السابقة" else "أحدث نسخة"
                _state.update {
                    it.copy(
                        syncing = false,
                        snackbar = "تم استبدال البيانات المحلية بـ$source",
                        lastOperation = "آخر عملية ناجحة: جلب $source",
                        retryAction = null
                    )
                }
            }.onFailure { error ->
                _state.update {
                    val message = error.message ?: "فشل جلب البيانات"
                    it.copy(
                        syncing = false,
                        snackbar = message,
                        retryAction = retry
                    )
                }
            }
        }
    }

    fun undoLastDownload() {
        if (_state.value.syncing || _state.value.saving) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            try {
                syncService.undoLastDownload().onSuccess { id ->
                    observeShift(id)
                    _state.update { it.copy(snackbar = "استُعيدت البيانات المحلية قبل التنزيل؛ راجعها قبل النشر") }
                }.onFailure { error -> saveGuard.showError(error.message ?: "تعذر التراجع عن التنزيل") }
            } finally { _state.update { it.copy(syncing = false) } }
        }
    }
}
