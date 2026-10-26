package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.dto.*
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TelegramLoginManagerTest {
    private val telegram = mockk<TelegramClient>()
    private val manager = TelegramLoginManager(telegram)
    @Test fun `nonce uses fixed unambiguous alphabet`() {
        repeat(100) {
            val nonce = manager.generateNonce()
            assertEquals(AppConstants.TELEGRAM_LOGIN_NONCE_LENGTH, nonce.length)
            assertTrue(nonce.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        }
    }
    @Test fun `drained old updates advance offset and only matching human authenticates`() = runBlocking<Unit> {
        coEvery { telegram.getUpdates(null, 1, 0) } returns listOf(TgUpdate(20))
        coEvery { telegram.getUpdates(21, 100, 0) } returns listOf(
            TgUpdate(21, message = TgMessage(1, from = TgUser(123, true), text = "NONCE")),
            TgUpdate(22, message = TgMessage(2, from = TgUser(123), text = "unrelated")),
            TgUpdate(23, message = TgMessage(3, from = TgUser(123), text = "nonce")))
        assertEquals(LoginVerificationResult.Success(123), manager.awaitVerification("NONCE", 123))
    }
    @Test fun `another human cannot authenticate selected doctor`() = runBlocking<Unit> {
        coEvery { telegram.getUpdates(null, 1, 0) } returns emptyList()
        coEvery { telegram.getUpdates(0, 100, 0) } returns listOf(TgUpdate(1, message = TgMessage(1, from = TgUser(456), text = "NONCE")))
        assertEquals(LoginVerificationResult.WrongUser(456), manager.awaitVerification("NONCE", 123))
    }
    @Test fun `drain and polling connection errors produce visible failures`() = runBlocking<Unit> {
        coEvery { telegram.getUpdates(null, 1, 0) } throws IOException("offline")
        assertEquals(LoginVerificationResult.Error("offline"), manager.awaitVerification("NONCE", 123))
        coEvery { telegram.getUpdates(null, 1, 0) } returns emptyList()
        coEvery { telegram.getUpdates(0, 100, 0) } throws IOException("poll failure")
        assertEquals(LoginVerificationResult.Error("poll failure"), manager.awaitVerification("NONCE", 123))
    }
    @Test fun `cancellation propagates during initial drain and polling`() = runBlocking<Unit> {
        coEvery { telegram.getUpdates(null, 1, 0) } throws CancellationException("drain cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { manager.awaitVerification("NONCE", 123) } }
        coEvery { telegram.getUpdates(null, 1, 0) } returns emptyList()
        coEvery { telegram.getUpdates(0, 100, 0) } throws CancellationException("poll cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { manager.awaitVerification("NONCE", 123) } }
    }
}
