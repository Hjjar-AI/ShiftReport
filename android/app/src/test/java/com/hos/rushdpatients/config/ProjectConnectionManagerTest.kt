package com.hos.rushdpatients.config

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

import android.net.Uri
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.util.DispatcherProvider
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before

class ProjectConnectionManagerTest {
    private val projects = mockk<ProjectConfigStore>()
    private val provisioning = mockk<ProjectProvisioningManager>()
    private val telegram = mockk<TelegramClient>()
    private val admins = mockk<AdminAuthorizer>()
    private val dispatchers = mockk<DispatcherProvider> { every { io } returns Dispatchers.Unconfined }
    private val manager = ProjectConnectionManager(projects, provisioning, telegram, admins, dispatchers)
    // Synthetic format-valid strings; never an actual bot credential.
    private val original = "12345:" + "a".repeat(20)
    private val replacement = "12345:" + "b".repeat(20)
    private val current = ProjectConfig(initialized = true, botToken = original, chatId = -100,
        reportsTopicId = 1, csvTopicId = 3, doctorsTopicId = 4)
    private val uri = mockk<Uri>()
    @Before fun setUp() {
        every { projects.current() } returns current
        coEvery { admins.requireAdmin() } returns Fixtures.doctor(rank = 1)
        coEvery { telegram.validateReplacementToken(replacement, -100, 12345) } just Runs
        every { projects.replaceBotToken(any(), any(), any(), any()) } just Runs
    }
    @Test fun `manual replacement validates then rechecks live authority at apply`() = runBlocking<Unit> {
        val change = manager.prepareToken(replacement)
        verify(exactly = 0) { projects.replaceBotToken(any(), any(), any(), any()) }
        manager.apply(change)
        coVerify(exactly = 2) { telegram.validateReplacementToken(replacement, -100, 12345) }
        coVerify(exactly = 3) { admins.requireAdmin() }
        verify(exactly = 1) { projects.replaceBotToken(current, replacement, 0L, 0L) }
    }
    @Test fun `changed configuration or actor blocks credential mutation`() = runBlocking<Unit> {
        val change = manager.prepareToken(replacement)
        every { projects.current() } returns current.copy(csvTopicId = 99)
        assertThrows(IllegalStateException::class.java) { runBlocking { manager.apply(change) } }
        every { projects.current() } returns current
        coEvery { admins.requireAdmin() } returns Fixtures.doctor(id = "other", rank = 1)
        assertThrows(IllegalStateException::class.java) { runBlocking { manager.apply(change) } }
        verify(exactly = 0) { projects.replaceBotToken(any(), any(), any(), any()) }
    }
    @Test fun `join reconnect rejects other projects keys and topics and wipes password`() = runBlocking<Unit> {
        val changes = listOf(current.copy(chatId = -200), current.copy(botToken = "54321:" + "c".repeat(20)),
            current.copy(telegramDataKey = "different-key"), current.copy(csvTopicId = 99),
            current.copy(reportsTopicId = 99), current.copy(doctorsTopicId = 99))
        changes.forEach { imported ->
            coEvery { provisioning.importConfig(uri, any()) } returns imported
            val password = "synthetic-password".toCharArray()
            assertThrows(IllegalArgumentException::class.java) { runBlocking { manager.prepareFile(uri, password) } }
            assertTrue(password.all { it == '\u0000' })
        }
        coVerify(exactly = 0) { telegram.validateReplacementToken(any(), any(), any()) }
        verify(exactly = 0) { projects.replaceBotToken(any(), any(), any(), any()) }
    }
    @Test fun `same project file reconnect applies optional residents routing without admin session`() = runBlocking<Unit> {
        coEvery { telegram.validateReportDestination(any(), any(), any()) } just Runs
        assertEquals(0L to 0L, ResidentsDestination.parse("", ""))
        assertThrows(IllegalArgumentException::class.java) { ResidentsDestination.parse("", "7") }
        assertThrows(IllegalStateException::class.java) { ResidentsDestination.parse("100", "") }
        for ((chat, topic) in listOf(0L to 0L, -300L to 0L, -300L to 7L)) {
            coEvery { provisioning.importConfig(uri, any()) } returns current.copy(botToken = replacement,
                residentsChatId = chat, residentsTopicId = topic)
            val password = "synthetic-password".toCharArray()
            val change = manager.prepareFile(uri, password)
            assertNull(change.adminActorId)
            manager.apply(change)
            assertTrue(password.all { it == '\u0000' })
            verify(exactly = 1) { projects.replaceBotToken(current, replacement, chat, topic) }
            if (chat != 0L) coVerify(exactly = 2) { telegram.validateReportDestination(chat, topic, replacement) }
        }
        coVerify(exactly = 0) { admins.requireAdmin() }
    }
    @Test fun protectedFilePreparationExplicitlyMarksRecoveryEligibleOrigin() = runBlocking<Unit> {
        coEvery { provisioning.importConfig(uri, any()) } returns current.copy(botToken = replacement)
        val change = manager.prepareFile(uri, "synthetic-password".toCharArray())
        assertTrue(change.fromProtectedFile)
        assertNull(change.adminActorId)
        verify(exactly = 0) { projects.replaceBotToken(any(), any(), any(), any()) }
    }
    @Test fun manualPreparationNeverMarksProtectedFileOrigin() = runBlocking<Unit> {
        val change = manager.prepareToken("  $replacement  ")
        assertFalse(change.fromProtectedFile)
        assertEquals(replacement, change.token)
        assertEquals(Fixtures.doctor(rank = 1).id, change.adminActorId)
    }
    @Test fun importFailureStillWipesCallerPasswordWithoutNetworkValidation() = runBlocking<Unit> {
        coEvery { provisioning.importConfig(uri, any()) } throws java.io.IOException("synthetic unreadable file")
        val pass = "synthetic-password".toCharArray()
        assertThrows(java.io.IOException::class.java) { runBlocking { manager.prepareFile(uri, pass) } }
        assertTrue(pass.all { it == '\u0000' })
        coVerify(exactly = 0) { telegram.validateReplacementToken(any(), any(), any()) }
    }
    @Test fun cancelledImportWipesCallerPasswordAndDoesNotBecomeChange() = runBlocking<Unit> {
        coEvery { provisioning.importConfig(uri, any()) } throws kotlinx.coroutines.CancellationException("synthetic cancellation")
        val pass = "synthetic-password".toCharArray()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking { manager.prepareFile(uri, pass) } }
        assertTrue(pass.all { it == '\u0000' })
        verify(exactly = 0) { projects.replaceBotToken(any(), any(), any(), any()) }
    }
    @Test fun authorityChangedDuringNetworkValidationCannotApplyReplacement() = runBlocking<Unit> {
        val admin = Fixtures.doctor(rank = 1)
        coEvery { admins.requireAdmin() } returnsMany listOf(admin, admin, admin.copy(id = "another-admin"))
        val change = manager.prepareToken(replacement)
        assertThrows(IllegalStateException::class.java) { runBlocking { manager.apply(change) } }
        coVerify(exactly = 2) { telegram.validateReplacementToken(replacement, -100, 12345) }
        verify(exactly = 0) { projects.replaceBotToken(any(), any(), any(), any()) }
    }
    @Test fun projectChangedDuringTokenPreparationInvalidatesReview() = runBlocking<Unit> {
        coEvery { telegram.validateReplacementToken(replacement, -100, 12345) } coAnswers {
            every { projects.current() } returns current.copy(doctorsTopicId = 88)
            Unit
        }
        assertThrows(IllegalStateException::class.java) { runBlocking { manager.prepareToken(replacement) } }
        verify(exactly = 0) { projects.replaceBotToken(any(), any(), any(), any()) }
    }
}
