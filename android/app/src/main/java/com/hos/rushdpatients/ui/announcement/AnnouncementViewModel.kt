package com.hos.rushdpatients.ui.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.domain.announcement.AnnouncementPublisher
import com.hos.rushdpatients.domain.announcement.AnnouncementReview
import com.hos.rushdpatients.domain.report.ReportDeliveryLedger
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    private val publisher: AnnouncementPublisher,
    ledger: ReportDeliveryLedger
) : ViewModel() {
    private val _state = MutableStateFlow(AnnouncementUiState())
    val state: StateFlow<AnnouncementUiState> = _state.asStateFlow()
    private var reviewed: AnnouncementReview? = null
    private fun busy() = _state.value.let { it.sending || it.reviewing || it.resolving }

    init {
        refresh()
        viewModelScope.launch {
            ledger.observe().catch {
                _state.update { it.copy(ledgerUnavailable = true, snackbar = "تعذر فتح سجل التسليم؛ لن يبدأ إرسال جديد") }
            }.collect { records ->
                _state.update { it.copy(ledgerUnavailable = false,
                    deliveryRecords = records.filter { row -> row.shiftId == AnnouncementPublisher.SCOPE && row.format == AnnouncementPublisher.SCOPE && row.status == "UNKNOWN" }) }
            }
        }
    }

    fun refresh() {
        if (busy() || _state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val messageId = publisher.currentMessageId()
                _state.update { it.copy(lastMessageId = messageId, loading = false) }
            }
            catch (e: CancellationException) { _state.update { it.copy(loading = false) }; throw e }
            catch (e: Exception) { _state.update { it.copy(loading = false, snackbar = e.message ?: "تعذر قراءة فهرس الإعلان") } }
        }
    }

    fun onTemplateChange(value: String) {
        if (busy()) return
        reviewed = null
        _state.update { it.copy(template = value, reviewText = null) }
    }

    fun review() {
        if (busy() || _state.value.loading || _state.value.ledgerUnavailable) return
        val text = _state.value.template
        _state.update { it.copy(reviewing = true, reviewText = null) }
        viewModelScope.launch {
            try {
                val prepared = publisher.prepare(text)
                reviewed = prepared
                _state.update { it.copy(reviewing = false, reviewText = prepared.text,
                    reviewExisting = prepared.existingMessageId != null, lastMessageId = prepared.existingMessageId) }
            } catch (e: CancellationException) { _state.update { it.copy(reviewing = false) }; throw e }
            catch (e: Exception) { _state.update { it.copy(reviewing = false, snackbar = e.message ?: "تعذرت مراجعة الإعلان") } }
        }
    }

    fun dismissReview() {
        if (busy()) return
        reviewed = null
        _state.update { it.copy(reviewText = null) }
    }

    fun confirmSend() {
        if (busy()) return
        val review = reviewed ?: return
        if (_state.value.reviewText != review.text || _state.value.template != review.text) return
        reviewed = null
        _state.update { it.copy(sending = true, reviewText = null) }
        viewModelScope.launch {
            try {
                val messageId = publisher.publish(review)
                _state.update { it.copy(sending = false, lastMessageId = messageId, snackbar = "تم نشر الإعلان وتأكيد الفهرس") }
            } catch (e: CancellationException) { _state.update { it.copy(sending = false) }; throw e }
            catch (e: Exception) { _state.update { it.copy(sending = false,
                snackbar = (e.message ?: "لم يتأكد النشر") + "؛ راجع سجل التسليم قبل الإعادة") } }
        }
    }

    fun resolve(record: ReportDeliveryRecord, messageId: Long?) {
        if (busy()) return
        _state.update { it.copy(resolving = true) }
        viewModelScope.launch {
            try {
                publisher.resolve(record, messageId)
                _state.update { it.copy(resolving = false, snackbar = "حُسمت العملية؛ أعد مراجعة النص قبل أي نشر جديد") }
                refresh()
            } catch (e: CancellationException) { _state.update { it.copy(resolving = false) }; throw e }
            catch (e: Exception) { _state.update { it.copy(resolving = false, snackbar = e.message ?: "تعذر حسم التسليم") } }
        }
    }

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }
}
