package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.Instant

/** Uses the owning ViewModel state/scope; repository guards remain unchanged. */
internal class WardShiftOperations(
    private val uiText: UiStrings,
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
        } else uiText(R.string.ui_ward_shift_operations_001)
        return uiText(R.string.ui_ward_shift_operations_002, source.date, downloaded) +
            uiText(R.string.ui_ward_shift_operations_003)
    }

    fun offerPreviousShift() {
        val shiftId = saveGuard.editableShiftId() ?: return
        if (_state.value.saving || _state.value.syncing) return
        viewModelScope.launch {
            try {
                require(patientRepository.countForShift(shiftId) == 0) { uiText(R.string.ui_ward_shift_operations_004) }
                val shift = shiftRepository.getById(shiftId) ?: error(uiText(R.string.ui_ward_shift_operations_005))
                val previous = shiftRepository.getRecent(12).firstOrNull { it.date < shift.date }
                val candidates = previous?.let { patientRepository.getForShift(it.id) }.orEmpty()
                require(candidates.isNotEmpty()) { uiText(R.string.ui_ward_shift_operations_006) }
                val sourceLabel = rolloverSourceLabel(requireNotNull(previous))
                _state.update { it.copy(rolloverPatients = candidates, rolloverTarget = shift, rolloverSourceLabel = sourceLabel) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_ward_shift_operations_007)) } }
        }
    }

    fun dismissRollover() = _state.update { it.copy(rolloverPatients = emptyList(), rolloverTarget = null, rolloverSourceLabel = "") }

    fun applyRollover(keepAssignments: Boolean) {
        val shiftId = saveGuard.editableShiftId() ?: return
        if (_state.value.saving || _state.value.syncing) return
        // Enter the shared guard immediately so it owns both busy state and duplicate protection.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saveGuard.saveAction(onSuccess = {}) {
                val expected = requireNotNull(_state.value.rolloverTarget) { uiText(R.string.ui_ward_shift_operations_008) }
                require(expected.id == shiftId) { uiText(R.string.ui_ward_shift_operations_009) }
                val candidates = _state.value.rolloverPatients
                require(candidates.isNotEmpty()) { uiText(R.string.ui_ward_shift_operations_010) }
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
                    snackbar = uiText(R.string.ui_ward_shift_operations_011, uiNumber(selected.size))) }
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
                    it.copy(snackbar = uiText(R.string.ui_ward_shift_operations_012))
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
                snackbar = uiText(R.string.ui_ward_shift_operations_013, selected.date)
            )
        }
    }

    fun exportCsv() {
        if (_state.value.exportingCsv) return
        val shiftId = _state.value.shift?.id
        if (shiftId == null) {
            _state.update { it.copy(snackbar = uiText(R.string.ui_ward_shift_operations_014)) }
            return
        }
        _state.update { it.copy(exportingCsv = true, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            try {
                when (val result = csvExporter.exportShift(shiftId)) {
                    is PatientCsvExporter.Result.Success -> _state.update {
                        it.copy(
                            exportingCsv = false,
                            snackbar = uiText(R.string.ui_ward_shift_operations_015, result.patientCount, result.fileName),
                            lastOperation = uiText(R.string.ui_ward_shift_operations_016, result.fileName)
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
                        snackbar = e.message ?: uiText(R.string.ui_ward_shift_operations_017),
                        retryAction = WardRetryAction.EXPORT_CSV
                    )
                }
            }
        }
    }

    private fun showDownloadIssue(error: Throwable, retry: WardRetryAction) {
        val recovery = when (error) {
            is com.hos.rushdpatients.domain.doctor.DoctorRegistryActionRequired ->
                if (error.action == com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.PUBLISH)
                    WardSyncRecovery.PUBLISH_DOCTORS else WardSyncRecovery.FETCH_DOCTORS
            is com.hos.rushdpatients.sync.LocalPatientChangesPendingException -> WardSyncRecovery.PUBLISH_PATIENTS
            else -> null
        }
        _state.update { it.copy(syncing = false, snackbar = null,
            syncIssue = error.message ?: uiText(R.string.ui_ward_shift_operations_024),
            syncRecovery = recovery, retryAction = retry) }
    }

    private fun fetchRemoteData(previous: Boolean) {
        if (_state.value.syncing) return
        val retry = if (previous) WardRetryAction.FETCH_PREVIOUS
        else WardRetryAction.FETCH_LATEST
        if (!NetworkStatus.isOnline(context)) {
            _state.update {
                it.copy(
                    snackbar = null,
                    syncIssue = uiText(R.string.ui_ward_shift_operations_018), syncRecovery = null,
                    retryAction = retry
                )
            }
            return
        }
        _state.update {
            it.copy(
                syncing = true,
                snackbar = null, syncIssue = null, syncRecovery = null,
                retryAction = null
            )
        }
        viewModelScope.launch {
            // Refresh the complete registry; pending local changes require explicit handling.
            val doctorsResult = syncService.refreshDoctorsFromRemote()
            if (doctorsResult.isFailure) {
                showDownloadIssue(doctorsResult.exceptionOrNull()
                    ?: IllegalStateException(uiText(R.string.ui_ward_shift_operations_019)), retry)
                return@launch
            }

            val result = if (previous) {
                syncService.fetchPreviousCsv()
            } else {
                syncService.fetchLatestCsv()
            }
            result.onSuccess { fetched ->
                observeShift(fetched.shiftId)
                val source = if (previous) uiText(R.string.ui_ward_shift_operations_020) else uiText(R.string.ui_ward_shift_operations_021)
                _state.update {
                    it.copy(
                        syncing = false,
                        snackbar = uiText(R.string.ui_ward_shift_operations_022, source),
                        lastOperation = uiText(R.string.ui_ward_shift_operations_023, source),
                        retryAction = null
                    )
                }
            }.onFailure { error -> showDownloadIssue(error, retry) }
        }
    }

    fun undoLastDownload() {
        if (_state.value.syncing || _state.value.saving) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            try {
                syncService.undoLastDownload().onSuccess { id ->
                    observeShift(id)
                    _state.update { it.copy(snackbar = uiText(R.string.ui_ward_shift_operations_025)) }
                }.onFailure { error -> saveGuard.showError(error.message ?: uiText(R.string.ui_ward_shift_operations_026)) }
            } finally { _state.update { it.copy(syncing = false) } }
        }
    }
}
