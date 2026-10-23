package com.hos.rushdpatients.sync

import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.TelegramException
import com.hos.rushdpatients.network.telegram.dto.TgChat
import com.hos.rushdpatients.network.telegram.dto.TgMessage
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Models observed reads and writes only; does not claim cross-device atomicity. */
class RemoteSyncStateStoreTest {
    private val telegram = mockk<TelegramClient>()
    private val topics = mockk<Topics> { every { chatId } returns -100 }
    private val json = Json { ignoreUnknownKeys = true }
    private val store = RemoteSyncStateStore(telegram, topics, json)
    private var pinned: TgMessage? = null
    private fun chat(message: TgMessage? = pinned) = TgChat(-100, "supergroup", pinnedMessage = message)
    private fun message(state: SyncState) = TgMessage(1, text = json.encodeToString(state))
    @Before fun setup() {
        pinned = message(SyncState())
        coEvery { telegram.getChat(-100) } answers { chat() }
        coEvery { telegram.editMessageText(-100, 1, any(), null) } answers {
            TgMessage(1, text = thirdArg<String>()).also { pinned = it }
        }
        coEvery { telegram.sendMessage(-100, any(), null, null, true, false, null) } answers {
            TgMessage(2, text = secondArg<String>()).also { pinned = it }
        }
        coEvery { telegram.pinMessage(-100, 2, true) } returns true
    }
    @Test fun `read ignores absent nontext malformed and unsupported pinned state`() = runBlocking<Unit> {
        for (value in listOf(null, TgMessage(1), TgMessage(1, text = "not json"),
            message(SyncState(kind = "other")), message(SyncState(v = SyncState.CURRENT_VERSION + 1)))) {
            pinned = value
            assertNull(store.readState())
        }
    }
    @Test fun `existing update edits recognized pin and upgrades protocol`() = runBlocking<Unit> {
        pinned = message(SyncState(v = 1))
        val result = store.updateExistingState { it.copy(announcementMessageId = 9, announcementUpdatedAt = 10) }
        assertEquals(SyncState.CURRENT_VERSION, result.v)
        assertEquals(9L, result.announcementMessageId)
        coVerify(exactly = 0) { telegram.pinMessage(any(), any(), any()) }
    }
    @Test fun `required missing index never creates or pins replacement`() = runBlocking<Unit> {
        pinned = null
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.updateExistingState { it } } }
        coVerify(exactly = 0) { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { telegram.pinMessage(any(), any(), any()) }
    }
    @Test fun `index disappearing between read and write blocks publication`() = runBlocking<Unit> {
        coEvery { telegram.getChat(-100) } returnsMany listOf(chat(), chat(null))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.updateExistingState { it } } }
        coVerify(exactly = 0) { telegram.editMessageText(any(), any(), any(), any()) }
        coVerify(exactly = 0) { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `ordinary initialization sends and pins a recognized index`() = runBlocking<Unit> {
        pinned = null
        val result = store.updateState { it.copy(doctorsFileId = "registry", doctorsMessageId = 10) }
        assertEquals("registry", result.doctorsFileId)
        coVerify(exactly = 1) { telegram.pinMessage(-100, 2, true) }
        coVerify(exactly = 0) { telegram.editMessageText(any(), any(), any(), any()) }
    }
    @Test fun `newer remote pointers survive a stale transform`() = runBlocking<Unit> {
        pinned = message(SyncState(csvFileId = "remote", csvMessageId = 30,
            previousCsvFileId = "previous", previousCsvMessageId = 20,
            doctorsFileId = "doctors-new", doctorsMessageId = 50, doctorsUpdatedAt = 1,
            announcementMessageId = 99, announcementUpdatedAt = 100))
        val result = store.updateExistingState { SyncState(csvFileId = "stale", csvMessageId = 10,
            doctorsFileId = "doctors-old", doctorsMessageId = 40, doctorsUpdatedAt = 999,
            announcementMessageId = 100, announcementUpdatedAt = 90) }
        assertEquals(30L, result.csvMessageId)
        assertEquals(20L, result.previousCsvMessageId)
        assertEquals("doctors-new", result.doctorsFileId)
        assertEquals(99L, result.announcementMessageId)
    }
    @Test fun `explicit history replacement drops recovery and old journal`() = runBlocking<Unit> {
        pinned = message(SyncState(csvFileId = "old", csvMessageId = 30,
            previousCsvFileId = "previous", previousCsvMessageId = 20,
            publicationJournal = listOf(PublicationJournalEntry("old", fileId = "old", messageId = 30, at = 1, deviceId = "synthetic"))))
        val result = store.updateExistingState(replaceCsvHistory = true) {
            SyncState(csvFileId = "replacement", csvMessageId = 10)
        }
        assertEquals(10L, result.csvMessageId)
        assertNull(result.previousCsvSnapshot())
        assertTrue(result.publicationJournal.isEmpty())
    }
    @Test fun `publication journal deduplicates and retains newest twelve by message id`() = runBlocking<Unit> {
        fun entry(id: Long) = PublicationJournalEntry("snapshot-$id", fileId = "f-$id", messageId = id,
            at = 100 - id, deviceId = "synthetic")
        pinned = message(SyncState(publicationJournal = (1L..10L).map(::entry)))
        val result = store.updateExistingState { it.copy(publicationJournal = (5L..20L).map(::entry)) }
        assertEquals((20L downTo 9L).toList(), result.publicationJournal.map { it.messageId })
    }
    @Test fun `telegram not modified response retains confirmed state`() = runBlocking<Unit> {
        coEvery { telegram.editMessageText(-100, 1, any(), null) } throws TelegramException(400, "message is not modified")
        assertEquals(SyncState(), store.updateExistingState { it })
    }
    @Test fun `other telegram failures and cancellation propagate`() = runBlocking<Unit> {
        coEvery { telegram.editMessageText(-100, 1, any(), null) } throws TelegramException(403, "forbidden")
        assertThrows(TelegramException::class.java) { runBlocking { store.updateExistingState { it } } }
        coEvery { telegram.getChat(-100) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { store.readState() } }
    }
    @Test fun `missing confirmation after write is an explicit failure`() = runBlocking<Unit> {
        coEvery { telegram.getChat(-100) } returnsMany listOf(chat(), chat(), chat(null))
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            store.updateExistingState { it.copy(announcementMessageId = 9, announcementUpdatedAt = 1) }
        } }
        coVerify(exactly = 1) { telegram.editMessageText(-100, 1, any(), null) }
    }
}
