package com.hos.rushdpatients.ui.settings

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
                    snackbar = "لا يوجد اتصال بالإنترنت — أعد المحاولة لاحقاً",
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
                            ?: "فشل جلب سجل الأطباء",
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
                            snackbar = "تم جلب البيانات: جديد ${r.inserted}، محدّث ${r.updated}",
                            lastOperation = "آخر عملية ناجحة: تنزيل بيانات المرضى",
                            retryAction = null,
                            lastCsvSync = format(Instant.now().toEpochMilli())
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            snackbar = e.message ?: "فشل جلب البيانات",
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
                _state.update { it.copy(snackbar = "استُعيدت البيانات المحلية قبل التنزيل؛ راجعها قبل النشر") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: "تعذر التراجع عن التنزيل") }
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
                check(NetworkStatus.isOnline(context)) { "لا يوجد اتصال بالإنترنت؛ بقيت البيانات محفوظة محلياً" }
                val shiftId = shiftRepository.getByDate(ShiftDate.current())?.id ?: error("لا توجد مناوبة حالية")
                val review = syncService.prepareCsvPublication(shiftId, forceLocal = true)
                publicationReview = review
                _state.update { it.copy(publicationReviewText = review.text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: "تعذر إعداد مراجعة النشر", retryAction = SettingsRetryAction.UPLOAD) }
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
                check(NetworkStatus.isOnline(context)) { "لا يوجد اتصال بالإنترنت؛ أعد مراجعة النشر عند الاتصال" }
                val result = syncService.uploadReviewedCsv(review, forceLocal = true).getOrThrow()
                _state.update { it.copy(snackbar = "نُشرت النسخة المراجعة (${result.patientCount} مريض)",
                    lastOperation = "آخر عملية: نشر النسخة الكاملة المراجعة") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: "تعذر تأكيد النشر؛ افحص النسخة المنشورة قبل الإعادة",
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
                            snackbar = "تم حفظ ${result.patientCount} مريض في ${result.fileName}",
                            lastOperation = "آخر عملية ناجحة: حفظ ${result.fileName}"
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
                        snackbar = e.message ?: "فشل تصدير CSV",
                        retryAction = SettingsRetryAction.EXPORT
                    )
                }
            }
        }
    }
}
