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
    private var publicationReview: CsvPublicationReview? = null

    fun invalidateReview() { publicationReview = null }

    fun bringLatest() {
        if (_state.value.syncing) return
        if (!NetworkStatus.isOnline(context)) {
            _state.update {
                it.copy(
                    snackbar = uiText(R.string.ui_settings_data_operations_001),
                    retryAction = SettingsRetryAction.FETCH
                )
            }
            return
        }
        _state.update { it.copy(syncing = true, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            // An explicit fetch is allowed to accept the authoritative remote doctor registry.
            // Background sync remains conservative and will not overwrite a newer registry.
            val doctorsResult = syncService.refreshDoctorsFromRemote()
            if (doctorsResult.isFailure) {
                _state.update {
                    it.copy(
                        syncing = false,
                        snackbar = doctorsResult.exceptionOrNull()?.message
                            ?: uiText(R.string.ui_ward_shift_operations_019),
                        retryAction = SettingsRetryAction.FETCH
                    )
                }
                return@launch
            }
            syncService.fetchLatestCsv()
                .onSuccess { r ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = uiText(R.string.ui_settings_data_operations_002, r.inserted, r.updated),
                            lastOperation = uiText(R.string.ui_settings_data_operations_003),
                            retryAction = null,
                            lastCsvSync = format(Instant.now().toEpochMilli())
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = e.message ?: uiText(R.string.ui_ward_shift_operations_024),
                            retryAction = SettingsRetryAction.FETCH
                        )
                    }
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
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_ward_shift_operations_026)) }
            } finally { _state.update { it.copy(syncing = false) } }
        }
    }

    /** Prepare only: no publication occurs until the reviewed snapshot is confirmed. */

    fun uploadCurrent() {
        if (_state.value.syncing) return
        publicationReview = null
        _state.update { it.copy(syncing = true, publicationReviewText = null, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            try {
                check(NetworkStatus.isOnline(context)) { uiText(R.string.ui_settings_data_operations_004) }
                val shiftId = shiftRepository.getByDate(ShiftDate.current())?.id ?: error(uiText(R.string.ui_settings_data_operations_005))
                val review = syncService.prepareCsvPublication(shiftId, forceLocal = true)
                publicationReview = review
                _state.update { it.copy(publicationReviewText = review.text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_settings_data_operations_006), retryAction = SettingsRetryAction.UPLOAD) }
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
        if (_state.value.syncing) return
        val review = publicationReview ?: return
        publicationReview = null
        _state.update { it.copy(syncing = true, publicationReviewText = null, snackbar = null, retryAction = null) }
        viewModelScope.launch {
            try {
                check(NetworkStatus.isOnline(context)) { uiText(R.string.ui_settings_data_operations_007) }
                val result = syncService.uploadReviewedCsv(review, forceLocal = true).getOrThrow()
                _state.update { it.copy(snackbar = uiText(R.string.ui_settings_data_operations_008, result.patientCount),
                    lastOperation = uiText(R.string.ui_settings_data_operations_009)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_settings_data_operations_010),
                    retryAction = SettingsRetryAction.UPLOAD) }
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    fun exportCsv() {
        if (_state.value.exportingCsv) return
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
                        snackbar = e.message ?: uiText(R.string.ui_ward_shift_operations_017),
                        retryAction = SettingsRetryAction.EXPORT
                    )
                }
            }
        }
    }
}
