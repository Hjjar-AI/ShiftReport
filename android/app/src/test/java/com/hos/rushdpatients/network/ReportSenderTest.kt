package com.hos.rushdpatients.network

import android.content.Context
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.report.*
import com.hos.rushdpatients.network.telegram.*
import com.hos.rushdpatients.network.telegram.dto.TgMessage
import com.hos.rushdpatients.testutil.*
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ReportSenderTest : DeliveryFixture() {
    private val directory = Files.createTempDirectory("sender_test_").toFile()
    private val context = mockk<Context> {
        every { cacheDir } returns directory
        every { getString(any()) } returns "synthetic route"
        every { getString(any(), *anyVararg()) } returns "synthetic route"
    }
    private val cipher = mockk<ProjectDataCipher> { every { enabled } returns false }
    private val telegram = mockk<TelegramClient>()
    private val topics = mockk<Topics> { every { chatId } returns -100; every { reportsChatId } returns -100;
        every { connectionUnchangedCheck() } returns { true }; every { threadId(Topic.REPORTS) } returns 1 }
    private val shifts = mockk<ShiftRepository>(relaxed = true)
    private val audit = mockk<AuditRepository>(relaxed = true)
    private val shift = Fixtures.shift()
    private fun sender() = ReportSender(context, cipher, telegram, topics, shifts, audit, ledger())
    @Before fun setup() {
        coEvery { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns TgMessage(10)
        coEvery { telegram.deleteMessage(any(), any()) } returns true
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } returns TgMessage(20)
    }
    @After fun cleanup() { directory.deleteRecursively() }
    @Test fun `successful text send persists exact route and blocks duplicate after sender recreation`() = runBlocking<Unit> {
        for ((chat, topic) in listOf(-100L to null, -100L to 1L, -300L to null, -300L to 7L)) {
            raw.value = null
            every { topics.reportsChatId } returns chat
            every { topics.threadId(Topic.REPORTS) } returns topic
            assertEquals(10L, sender().sendTextReport(shift, listOf("synthetic"), actor, "review-key").firstMessageId)
            assertEquals(DeliveryStatus.SENT, ledger().observe().first().single().status)
            coVerify(exactly = 1) { telegram.sendMessage(chat, "synthetic", any(), any(), any(), any(), topic) }
            assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking {
                sender().sendTextReport(shift, listOf("synthetic"), actor, "review-key")
            } }
        }
        coVerify(exactly = 4) { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `partial text delivery keeps UNKNOWN even after cleanup`() = runBlocking<Unit> {
        coEvery { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns TgMessage(10) andThenThrows IOException("ambiguous second send")
        assertThrows(ReportDeliveryUncertainException::class.java) { runBlocking {
            sender().sendTextReport(shift, listOf("first", "second"), actor, "review-key")
        } }
        coVerify { telegram.deleteMessage(-100, 10) }
        assertEquals(DeliveryStatus.UNKNOWN, ledger().observe().first().single().status)
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { sender().checkGeneralDelivery(shift, "changed-key", false) } }
        coVerify(exactly = 0) { shifts.markSent(any(), any(), any(), any()) }
    }
    @Test fun `cancelled send preserves durable UNKNOWN`() = runBlocking<Unit> {
        coEvery { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { sender().sendTextReport(shift, listOf("synthetic"), actor, "review-key") } }
        assertEquals(DeliveryStatus.UNKNOWN, ledger().observe().first().single().status)
    }
    @Test fun `encrypted text uses a document and removes temporary plaintext`() = runBlocking<Unit> {
        every { cipher.enabled } returns true
        var uploaded = ""
        coEvery { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) } answers {
            uploaded = secondArg<File>().readText(); TgMessage(20)
        }
        sender().sendTextReport(shift, listOf("first", "second"), actor, "review-key")
        assertEquals("first\n\nsecond", uploaded)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        coVerify(exactly = 0) { telegram.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
    }
    @Test fun `pdf route sends reviewed file and rejects missing file before delivery`() = runBlocking<Unit> {
        val file = File(directory, "synthetic.pdf").apply { writeText("synthetic fixture") }
        sender().sendPdfReport(shift, file, null, actor, "pdf-key")
        assertEquals(listOf(20L), ledger().observe().first().single().messageIds)
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            sender().sendPdfReport(shift, File(directory, "missing.pdf"), null, actor, "other")
        } }
        coVerify(exactly = 1) { telegram.sendDocument(any(), any(), any(), any(), any(), any(), any()) }
    }
}
