package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import android.content.Context
import com.hos.rushdpatients.domain.export.PatientCsvExporter
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.util.NetworkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Uses the owning ViewModel state/scope; repository guards remain unchanged. */
internal class WardShiftOperations(
    private val uiText: UiStrings,
    private val _state: MutableStateFlow<WardUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val context: Context,
    private val syncService: SyncService,
    private val csvExporter: PatientCsvExporter,
    private val saveGuard: WardSaveGuard,
    private val observeShift: (String) -> Unit
) {

    private var fetchPublication: com.hos.rushdpatients.sync.CsvPublicationReview? = null
    fun replaceAndFetch(allowOlder: Boolean = false) = fetchRemoteData(replaceLocal = true, allowOlder = allowOlder)
    fun dismissFetchReview() {
        fetchPublication = null
        _state.update { it.copy(fetchReviewText = null, olderRemoteConfirmation = false, fetchChoiceRequired = false) }
    }
    fun reviewPendingPublication() {
        val shift = _state.value.shift ?: return
        if (_state.value.syncing || _state.value.saving) return
        _state.update { it.copy(syncing = true, syncProgressRes = R.string.ui_progress_prepare_publication) }
        viewModelScope.launch {
            try {
                val review = syncService.prepareCsvPublication(shift.id)
                fetchPublication = review
                _state.update { it.copy(fetchReviewText = review.text) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showDownloadIssue(e, WardRetryAction.FETCH_LATEST) }
            finally { _state.update { it.copy(syncing = false) } }
        }
    }
    fun confirmPendingPublication() {
        val review = fetchPublication ?: return
        if (_state.value.syncing || _state.value.saving) return
        dismissFetchReview()
        _state.update { it.copy(syncing = true, syncProgressRes = R.string.ui_progress_publish_patients) }
        viewModelScope.launch {
            try {
                syncService.uploadReviewedCsv(review).fold(
                    onSuccess = { uploaded -> _state.update { it.copy(snackbar = uiText(if (uploaded.localBookkeepingFailed) R.string.sync_publication_bookkeeping_warning else R.string.sync_publication_complete), syncIssue = null, syncRecovery = null, retryAction = null) } },
                    onFailure = { showDownloadIssue(it, WardRetryAction.FETCH_LATEST) })
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showDownloadIssue(e, WardRetryAction.FETCH_LATEST) }
            finally { _state.update { it.copy(syncing = false) } }
        }
    }

    fun bringLatestData() = fetchRemoteData()

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
                        snackbar = com.hos.rushdpatients.ui.components.failureText(uiText, e) ?: uiText(R.string.ui_ward_shift_operations_017),
                        retryAction = WardRetryAction.EXPORT_CSV
                    )
                }
            } finally { _state.update { it.copy(exportingCsv = false) } }
        }
    }

    private fun showDownloadIssue(error: Throwable, retry: WardRetryAction) {
        if (error is CancellationException) throw error
        val recovery = when (error) {
            is com.hos.rushdpatients.domain.doctor.DoctorRegistryActionRequired ->
                if (error.action == com.hos.rushdpatients.domain.doctor.DoctorRegistryAction.PUBLISH)
                    WardSyncRecovery.PUBLISH_DOCTORS else WardSyncRecovery.FETCH_DOCTORS
            is com.hos.rushdpatients.sync.LocalPatientChangesPendingException -> WardSyncRecovery.PUBLISH_PATIENTS
            else -> null
        }
        _state.update { it.copy(syncing = false, snackbar = null,
            syncIssue = com.hos.rushdpatients.ui.components.failureText(uiText, error) ?: uiText(R.string.ui_ward_shift_operations_024),
            olderRemoteConfirmation = error is com.hos.rushdpatients.sync.OlderRemoteSnapshotException,
            fetchChoiceRequired = error is com.hos.rushdpatients.sync.LocalPatientChangesPendingException,
            syncRecovery = recovery, retryAction = retry) }
    }

    private fun fetchRemoteData(replaceLocal: Boolean = false, allowOlder: Boolean = false) {
        if (_state.value.syncing || _state.value.saving) return
        val retry = WardRetryAction.FETCH_LATEST
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
                syncing = true, syncProgressRes = R.string.ui_progress_fetch_patients,
                snackbar = null, syncIssue = null, syncRecovery = null,
                retryAction = null
            )
        }
        viewModelScope.launch {
            try {
                // Refresh the complete registry; pending local changes require explicit handling.
                val doctorsResult = syncService.refreshDoctorsForPatientFetch()
                if (doctorsResult.isFailure) {
                    showDownloadIssue(doctorsResult.exceptionOrNull()
                        ?: IllegalStateException(uiText(R.string.ui_ward_shift_operations_019)), retry)
                    return@launch
                }

                val result = syncService.fetchLatestCsv(replaceLocal, allowOlder)
                result.onSuccess { fetched ->
                    observeShift(fetched.shiftId)
                    val source = uiText(R.string.ui_ward_shift_operations_021)
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = uiText(R.string.ui_ward_shift_operations_022, source),
                            lastOperation = uiText(R.string.ui_ward_shift_operations_023, source),
                            retryAction = null
                        )
                    }
                }.onFailure { error -> showDownloadIssue(error, retry) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showDownloadIssue(e, retry) }
            finally { _state.update { it.copy(syncing = false) } }
        }
    }

    fun undoLastDownload() {
        if (_state.value.syncing || _state.value.saving) return
        _state.update { it.copy(syncing = true, syncProgressRes = R.string.ui_progress_undo_fetch) }
        viewModelScope.launch {
            try {
                syncService.undoLastDownload().onSuccess { id ->
                    observeShift(id)
                    _state.update { it.copy(snackbar = uiText(R.string.ui_ward_shift_operations_025)) }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    saveGuard.showError(error.message ?: uiText(R.string.ui_ward_shift_operations_026))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                saveGuard.showError(com.hos.rushdpatients.ui.components.failureText(uiText, e)
                    ?: uiText(R.string.ui_ward_shift_operations_026))
            } finally { _state.update { it.copy(syncing = false) } }
        }
    }
}
