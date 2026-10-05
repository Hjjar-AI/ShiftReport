package com.hos.rushdpatients.ui.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.Topic
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.sync.SyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val syncService: SyncService,
    private val auditRepository: AuditRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnnouncementUiState())
    val state: StateFlow<AnnouncementUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val id = syncService.announcementMessageId()
            _state.update { it.copy(loading = false, lastMessageId = id) }
        }
    }

    fun onTemplateChange(v: String) = _state.update { it.copy(template = v) }

    fun send() {
        if (_state.value.sending) return
        val template = _state.value.template
        if (template.isBlank()) {
            _state.update { it.copy(snackbar = "النص فارغ") }
            return
        }
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val existing = _state.value.lastMessageId
                val message = if (existing != null) {
                    telegram.editMessageText(
                        chatId = topics.chatId,
                        messageId = existing,
                        text = template,
                        parseMode = com.hos.rushdpatients.network.telegram.ParseMode.MARKDOWN_V2
                    )
                } else {
                    telegram.sendMessage(
                        chatId = topics.chatId,
                        text = template,
                        parseMode = com.hos.rushdpatients.network.telegram.ParseMode.MARKDOWN_V2,
                        messageThreadId = topics.threadId(Topic.ANNOUNCEMENTS)
                    )
                }

                // Remember the announcement without replacing the dedicated sync-state pin.
                syncService.recordAnnouncement(message.messageId)

                auditRepository.record(
                    actorDoctorId = null,
                    actorName = null,
                    action = AppConstants.AUDIT_ANNOUNCEMENT_UPDATED,
                    detail = "messageId=${message.messageId}"
                )

                _state.update {
                    it.copy(
                        sending = false,
                        lastMessageId = message.messageId,
                        snackbar = "تم نشر الإعلان"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(sending = false, snackbar = e.message ?: "فشل النشر")
                }
            }
        }
    }

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }
}
