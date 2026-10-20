package com.hos.rushdpatients.sync

import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.TelegramException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes pinned-state updates within this process; Telegram remains last-write-wins across devices. */
@Singleton
class RemoteSyncStateStore @Inject constructor(
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val json: Json
) {
    private val remoteStateMutex = Mutex()

    suspend fun updateState(transform: (SyncState) -> SyncState): SyncState =
        remoteStateMutex.withLock {
            val updated = transform(readState() ?: SyncState()).copy(v = SyncState.CURRENT_VERSION)
            writeState(updated)
            readState() ?: updated
        }

    suspend fun readState(): SyncState? {
        val chat = telegram.getChat(topics.chatId)
        val pinned = chat.pinnedMessage ?: return null
        val text = pinned.text ?: return null
        return runCatching { json.decodeFromString<SyncState>(text) }
            .getOrNull()
            ?.takeIf { it.isRecognized }
    }

    suspend fun updateExistingState(replaceCsvHistory: Boolean = false, transform: (SyncState) -> SyncState): SyncState =
        remoteStateMutex.withLock {
            val current = requireNotNull(readState()) { "فهرس المشروع غير متاح؛ أعد الجلب" }
            writeState(transform(current).copy(v = SyncState.CURRENT_VERSION), requireExisting = true, replaceCsvHistory = replaceCsvHistory)
            requireNotNull(readState()) { "تعذر تأكيد فهرس المشروع" }
        }

    private suspend fun writeState(state: SyncState, requireExisting: Boolean = false, replaceCsvHistory: Boolean = false) {
        val chat = telegram.getChat(topics.chatId)
        val existing = chat.pinnedMessage?.takeIf { message ->
            val text = message.text ?: return@takeIf false
            runCatching { json.decodeFromString<SyncState>(text) }
                .getOrNull()
                ?.isRecognized == true
        }

        require(!requireExisting || existing != null) { "اختفى فهرس المشروع؛ لن يُنشأ تثبيت بديل" }
        val existingState = existing?.text?.let { text ->
            runCatching { json.decodeFromString<SyncState>(text) }.getOrNull()
        }
        val stateToWrite = if (existingState == null) state else {
            val snapshots = listOfNotNull(
                state.currentCsvSnapshot(), state.previousCsvSnapshot(),
                existingState.currentCsvSnapshot(), existingState.previousCsvSnapshot()
            ).distinctBy { it.messageId }.sortedByDescending { it.messageId }
            val latest = if (replaceCsvHistory) state.currentCsvSnapshot() else snapshots.firstOrNull()
            val previous = if (replaceCsvHistory) null else snapshots.getOrNull(1)
            val doctorsState = listOf(state, existingState).maxWithOrNull(
                compareBy<SyncState> { it.doctorsMessageId ?: Long.MIN_VALUE }
                    .thenBy { it.doctorsUpdatedAt }
            ) ?: state
            val announcementState = listOf(state, existingState).maxWithOrNull(
                compareBy<SyncState> { it.announcementUpdatedAt }
                    .thenBy { it.announcementMessageId ?: Long.MIN_VALUE }
            ) ?: state
            state.copy(
                csvFileId = latest?.fileId,
                csvMessageId = latest?.messageId,
                csvDate = latest?.date,
                csvUpdatedAt = latest?.updatedAt ?: 0L,
                previousCsvFileId = previous?.fileId,
                previousCsvMessageId = previous?.messageId,
                previousCsvDate = previous?.date,
                previousCsvUpdatedAt = previous?.updatedAt ?: 0L,
                publicationJournal = (state.publicationJournal + if (replaceCsvHistory) emptyList() else existingState.publicationJournal)
                    .distinctBy { it.messageId }
                    .sortedByDescending { it.messageId }
                    .take(12),
                doctorsData = doctorsState.doctorsData,
                doctorsFileId = doctorsState.doctorsFileId,
                doctorsMessageId = doctorsState.doctorsMessageId,
                doctorsUpdatedAt = doctorsState.doctorsUpdatedAt,
                announcementMessageId = announcementState.announcementMessageId,
                announcementUpdatedAt = announcementState.announcementUpdatedAt
            )
        }
        val encoded = json.encodeToString(SyncState.serializer(), stateToWrite)
        if (existing == null) {
            val msg = telegram.sendMessage(
                chatId = topics.chatId,
                text = encoded,
                parseMode = null,
                disableNotification = true
            )
            telegram.pinMessage(topics.chatId, msg.messageId, disableNotification = true)
        } else {
            try {
                telegram.editMessageText(
                    chatId = topics.chatId,
                    messageId = existing.messageId,
                    text = encoded,
                    parseMode = null
                )
            } catch (e: TelegramException) {
                if (!e.message.contains("not modified", ignoreCase = true)) throw e
            }
        }
    }
}
