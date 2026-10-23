package com.hos.rushdpatients.domain.announcement

import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.domain.report.ReportDeliveryBlockedException
import com.hos.rushdpatients.network.telegram.*
import com.hos.rushdpatients.network.telegram.dto.TgMessage
import com.hos.rushdpatients.sync.*
import com.hos.rushdpatients.testutil.DeliveryFixture
import com.hos.rushdpatients.util.DispatcherProvider
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AnnouncementPublisherTest : DeliveryFixture() {
    private val telegram = mockk<TelegramClient>()
    private val projects = mockk<ProjectConfigStore>()
    private val topics = mockk<Topics> { every { chatId } returns -100; every { threadId(Topic.ANNOUNCEMENTS) } returns 2 }
    private val cipher = mockk<ProjectDataCipher>()
    private val remote = mockk<RemoteSyncStateStore>()
    private val sync = mockk<DoctorSyncCoordinator>()
    private val audit = mockk<AuditRepository>(relaxed = true)
    private val dispatchers = mockk<DispatcherProvider> { every { io } returns Dispatchers.Unconfined }
    private var source = SyncState()
    private var config = ProjectConfig(chatId = -100, announcementsTopicId = 2)
    private fun publisher() = AnnouncementPublisher(telegram, topics, projects, cipher, remote, sync, ledger(), audit, dispatchers)
    @Before fun setup() {
        every { projects.current() } answers { config }
        coEvery { remote.readState() } answers { source }
        coEvery { remote.updateExistingState(any(), any()) } coAnswers { secondArg<(SyncState) -> SyncState>().invoke(source).also { source = it } }
        coEvery { sync.requirePublishedAdmin(any(), any()) } returns actor
        coEvery { cipher.encodeText(any()) } answers { firstArg() }
        coEvery { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns TgMessage(10)
        coEvery { telegram.editMessageText(any(), any(), any(), any()) } returns TgMessage(10)
    }
    @Test fun `reviewed announcement publishes once and persists confirmed index`() = runBlocking<Unit> {
        val p = publisher()
        val review = p.prepare("إعلان تجريبي")
        coVerify(exactly = 0) { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(10L, p.publish(review))
        assertEquals(10L, source.announcementMessageId)
        assertEquals("SENT", ledger().observe().first().single().status)
        assertThrows(IllegalStateException::class.java) { runBlocking { p.publish(review) } }
    }
    @Test fun `changed connection index or admin invalidates review`() = runBlocking<Unit> {
        val p = publisher()
        var review = p.prepare("إعلان تجريبي")
        config = config.copy(announcementsTopicId = 99)
        assertThrows(IllegalStateException::class.java) { runBlocking { p.publish(review) } }
        review = p.prepare("إعلان تجريبي")
        source = source.copy(announcementUpdatedAt = 1)
        assertThrows(IllegalStateException::class.java) { runBlocking { p.publish(review) } }
        review = p.prepare("إعلان تجريبي")
        coEvery { sync.requirePublishedAdmin(any(), any()) } returns actor.copy(id = "other")
        assertThrows(IllegalStateException::class.java) { runBlocking { p.publish(review) } }
        coVerify(exactly = 0) { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `index failure after send retains UNKNOWN and blocks retry`() = runBlocking<Unit> {
        val p = publisher()
        val review = p.prepare("إعلان تجريبي")
        coEvery { remote.updateExistingState(any(), any()) } throws IOException("index uncertain")
        assertThrows(IOException::class.java) { runBlocking { p.publish(review) } }
        assertEquals("UNKNOWN", ledger().observe().first().single().status)
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { publisher().prepare("changed announcement") } }
    }
    @Test fun `existing unchanged announcement accepts Telegram not modified`() = runBlocking<Unit> {
        source = source.copy(announcementMessageId = 10)
        coEvery { telegram.editMessageText(-100, 10, any(), null) } throws TelegramException(400, "message is not modified")
        val p = publisher()
        assertEquals(10L, p.publish(p.prepare("إعلان تجريبي")))
        coVerify(exactly = 0) { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `empty and oversized announcements never register delivery`() = runBlocking<Unit> {
        for (value in listOf(" ", "a".repeat(4097))) assertThrows(IllegalArgumentException::class.java) { runBlocking { publisher().prepare(value) } }
        assertNull(raw.value)
    }
}
