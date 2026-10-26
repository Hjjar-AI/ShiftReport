package com.hos.rushdpatients.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.hos.rushdpatients.R
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
    @ApplicationContext private val context: Context,
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val json: Json
) {
    private val remoteStateMutex = Mutex()

    sealed interface PinnedState {
        object Absent : PinnedState
        object Unrecognized : PinnedState
        data class Valid(val messageId: Long, val state: SyncState) : PinnedState
    }

    suspend fun readPinnedState(): PinnedState {
        val pinned = telegram.getChat(topics.chatId).pinnedMessage ?: return PinnedState.Absent
        val state = pinned.text?.let { text -> runCatching { json.decodeFromString<SyncState>(text) }.getOrNull() }
        return if (state?.isRecognized == true) PinnedState.Valid(pinned.messageId, state)
            else PinnedState.Unrecognized
    }

    private fun PinnedState.recognizedOrNull(): SyncState? = when (this) {
        PinnedState.Absent -> null
        PinnedState.Unrecognized -> error(context.getString(R.string.sync_index_unrecognized))
        is PinnedState.Valid -> state
    }

    suspend fun readState(): SyncState? = readPinnedState().recognizedOrNull()

    suspend fun updateState(transform: (SyncState) -> SyncState): SyncState =
        update(requireExisting = false, replaceCsvHistory = false, transform = transform)

    suspend fun updateExistingState(replaceCsvHistory: Boolean = false, transform: (SyncState) -> SyncState): SyncState =
        update(requireExisting = true, replaceCsvHistory = replaceCsvHistory, transform = transform)

    private suspend fun update(requireExisting: Boolean, replaceCsvHistory: Boolean,
                               transform: (SyncState) -> SyncState): SyncState = remoteStateMutex.withLock {
        val pinned = readPinnedState()
        val current = pinned.recognizedOrNull()
        require(!requireExisting || current != null) { context.getString(R.string.sync_index_unavailable_fetch) }
        val updated = transform(current ?: SyncState()).copy(v = SyncState.CURRENT_VERSION)
        writeState(updated, pinned as? PinnedState.Valid, replaceCsvHistory)
        requireNotNull(readState()) { context.getString(R.string.sync_index_confirmation_failed) }
    }

    private suspend fun writeState(state: SyncState, existing: PinnedState.Valid?, replaceCsvHistory: Boolean) {
        val existingState = existing?.state
        val stateToWrite = if (existingState == null) state else {
            val snapshots = listOfNotNull(
                state.currentCsvSnapshot(), state.previousCsvSnapshot(),
                existingState.currentCsvSnapshot(), existingState.previousCsvSnapshot()
            ).distinctBy { it.messageId }.sortedByDescending { it.messageId }
            val doctorsState = listOf(state, existingState).maxWithOrNull(
                compareBy<SyncState> { it.doctorsMessageId ?: Long.MIN_VALUE }
                    .thenBy { it.doctorsUpdatedAt }
            ) ?: state
            val announcementState = listOf(state, existingState).maxWithOrNull(
                compareBy<SyncState> { it.announcementUpdatedAt }
                    .thenBy { it.announcementMessageId ?: Long.MIN_VALUE }
            ) ?: state
            state.withCsvHistory(if (replaceCsvHistory) listOfNotNull(state.currentCsvSnapshot()) else snapshots).copy(
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
