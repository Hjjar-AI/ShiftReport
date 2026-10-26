package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import android.content.Context
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.domain.export.PatientCsvExporter
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.sync.CsvPublicationReview
import com.hos.rushdpatients.util.ShiftDate
import com.hos.rushdpatients.util.NetworkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Shares the screen state and ViewModel scope; does not create a second lifecycle. */
internal class SettingsDataOperations(
    private val uiText: UiStrings,
    private val _state: MutableStateFlow<SettingsUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val context: Context, private val syncService: SyncService,
    private val shiftRepository: ShiftRepository, private val csvExporter: PatientCsvExporter,
    private val format: (Long) -> String
) {
    private var rollbackReview: com.hos.rushdpatients.sync.CsvRollbackReview? = null
    private var publicationReview: CsvPublicationReview? = null

    fun invalidateReview() { publicationReview = null }

    private fun transferFailure(error: Throwable?, fallback: Int, retry: SettingsRetryAction) {
        if (error is CancellationException) throw error
        val recovery = when (error) {
            is com.hos.rushdpatients.domain.doctor.DoctorRegistryActionRequired -> SettingsSyncRecovery.DOCTORS
            is com.hos.rushdpatients.sync.LocalPatientChangesPendingException -> SettingsSyncRecovery.PATIENTS
            else -> null
        }
        _state.update { it.copy(fetchChoiceRequired = error is com.hos.rushdpatients.sync.LocalPatientChangesPendingException, olderRemoteConfirmation = error is com.hos.rushdpatients.sync.OlderRemoteSnapshotException, syncIssue = error?.message ?: uiText(fallback),
            syncRecovery = recovery, syncRetryAction = retry, retryAction = retry) }
    }

    fun bringLatest(replaceLocal: Boolean = false, allowOlder: Boolean = false) {
        if (_state.value.syncing || _state.value.backupBusy || _state.value.exportingCsv) return
        if (!NetworkStatus.isOnline(context)) {
            _state.update {
                it.copy(
                    snackbar = uiText(R.string.ui_settings_data_operations_001),
                    syncIssue = uiText(R.string.ui_settings_data_operations_001), syncRecovery = null,
                    syncRetryAction = SettingsRetryAction.FETCH,
                    retryAction = SettingsRetryAction.FETCH
                )
            }
            return
        }
        _state.update { it.copy(syncing = true, snackbar = null, retryAction = null,
            syncIssue = null, syncRecovery = null, syncRetryAction = null) }
        viewModelScope.launch {
            var fallback = R.string.ui_ward_shift_operations_019
            try {
                // Explicit fetch accepts the authoritative registry through its existing guards.
                syncService.refreshDoctorsForPatientFetch().getOrThrow()
                fallback = R.string.ui_ward_shift_operations_024
                val result = syncService.fetchLatestCsv(replaceLocal, allowOlder).getOrThrow()
                _state.update { it.copy(
                    snackbar = uiText(R.string.ui_settings_data_operations_002, result.inserted, result.updated),
                    lastOperation = uiText(R.string.ui_settings_data_operations_003),
                    syncIssue = null, syncRecovery = null, syncRetryAction = null, retryAction = null,
                    lastCsvSync = format(Instant.now().toEpochMilli())
                ) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transferFailure(e, fallback, SettingsRetryAction.FETCH)
                _state.update { it.copy(snackbar = com.hos.rushdpatients.ui.components.failureText(uiText, e) ?: uiText(fallback)) }
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    /** Restore only through the existing guarded download-undo service. */

    fun undoLastDownload() {
        val current = _state.value
        if (current.syncing || current.backupBusy || current.exportingCsv) return
        _state.update { it.copy(syncing = true, retryAction = null) }
        viewModelScope.launch {
            try {
                syncService.undoLastDownload().getOrThrow()
                _state.update { it.copy(snackbar = uiText(R.string.ui_ward_shift_operations_025)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = com.hos.rushdpatients.ui.components.failureText(uiText, e) ?: uiText(R.string.ui_ward_shift_operations_026)) }
            } finally { _state.update { it.copy(syncing = false) } }
        }
    }

    fun dismissFetchChoice() = _state.update { it.copy(fetchChoiceRequired = false) }
    fun dismissOlderRemote() = _state.update { it.copy(olderRemoteConfirmation = false) }
    fun prepareRollback() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            try {
                val review = syncService.prepareRollback()
                rollbackReview = review
                _state.update { it.copy(rollbackReviewText = review.text) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(snackbar = e.message) } }
            finally { _state.update { it.copy(syncing = false) } }
        }
    }
    fun dismissRollback() { rollbackReview = null; _state.update { it.copy(rollbackReviewText = null) } }
    fun confirmRollback() {
        val review = rollbackReview ?: return
        if (_state.value.syncing) return
        dismissRollback()
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            try {
                syncService.publishRollback(review).getOrThrow()
                _state.update { it.copy(snackbar = uiText(R.string.sync_rollback_done)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(snackbar = e.message) } }
            finally { _state.update { it.copy(syncing = false) } }
        }
    }

    /** Prepare only: no publication occurs until the reviewed snapshot is confirmed. */

    fun uploadCurrent() {
        if (_state.value.syncing || _state.value.backupBusy || _state.value.exportingCsv) return
        publicationReview = null
        _state.update { it.copy(syncing = true, publicationReviewText = null, snackbar = null, retryAction = null,
            syncIssue = null, syncRecovery = null, syncRetryAction = null) }
        viewModelScope.launch {
            try {
                check(NetworkStatus.isOnline(context)) { uiText(R.string.ui_settings_data_operations_004) }
                val shiftId = shiftRepository.getLatest()?.id ?: error(uiText(R.string.ui_settings_data_operations_005))
                val review = syncService.prepareCsvPublication(shiftId)
                publicationReview = review
                _state.update { it.copy(publicationReviewText = review.text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transferFailure(e, R.string.ui_settings_data_operations_006, SettingsRetryAction.UPLOAD)
                _state.update { it.copy(snackbar = com.hos.rushdpatients.ui.components.failureText(uiText, e) ?: uiText(R.string.ui_settings_data_operations_006), retryAction = SettingsRetryAction.UPLOAD) }
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    fun cancelPublicationReview() {
        if (_state.value.syncing) return
        publicationReview = null
        _state.update { it.copy(publicationReviewText = null) }
    }

    fun confirmPublication() {
        if (_state.value.syncing || _state.value.backupBusy || _state.value.exportingCsv) return
        val review = publicationReview ?: return
        publicationReview = null
        _state.update { it.copy(syncing = true, publicationReviewText = null, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            try {
                check(NetworkStatus.isOnline(context)) { uiText(R.string.ui_settings_data_operations_007) }
                val result = syncService.uploadReviewedCsv(review).getOrThrow()
                _state.update { it.copy(snackbar = if (result.localBookkeepingFailed) uiText(R.string.sync_publication_bookkeeping_warning) else uiText(R.string.ui_settings_data_operations_008, result.patientCount),
                    syncIssue = null, syncRecovery = null, syncRetryAction = null,
                    lastOperation = uiText(R.string.ui_settings_data_operations_009)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transferFailure(e, R.string.ui_settings_data_operations_010, SettingsRetryAction.UPLOAD)
                _state.update { it.copy(snackbar = com.hos.rushdpatients.ui.components.failureText(uiText, e) ?: uiText(R.string.ui_settings_data_operations_010),
                    retryAction = SettingsRetryAction.UPLOAD) }
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    fun exportCsv() {
        if (_state.value.exportingCsv || _state.value.syncing || _state.value.backupBusy) return
        _state.update { it.copy(exportingCsv = true, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            try {
                when (val result = csvExporter.exportCurrentShift()) {
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
                            retryAction = SettingsRetryAction.EXPORT
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
                        retryAction = SettingsRetryAction.EXPORT
                    )
                }
            }
        }
    }
}
