package com.hos.rushdpatients.domain.auth

import android.os.SystemClock
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.network.telegram.TelegramClient
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.IOException

/** Existing login cases aligned from retired queue polling to private challenge input. */
class TelegramLoginManagerTest {
    private val telegram = mockk<TelegramClient>()
    private var connected = true
    private val topics = mockk<Topics> { every { connectionUnchangedCheck() } returns { connected } }
    private val manager = TelegramLoginManager(telegram, topics)
    private val code = slot<String>()
    @Before fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1000L
        coEvery { telegram.sendPrivateLoginCode(123, capture(code)) } returns com.hos.rushdpatients.network.telegram.dto.TgMessage(1)
    }
    @After fun cleanup() { unmockkStatic(SystemClock::class) }
    @Test fun `private challenge uses six digits and is consumed once`() = runBlocking<Unit> {
        manager.beginVerification("cedar", 123)
        assertTrue(code.captured.matches(Regex("[0-9]{6}")))
        assertEquals(LoginVerificationResult.Success(123), manager.verify("cedar", 123, code.captured))
        assertEquals(LoginVerificationResult.Timeout, manager.verify("cedar", 123, code.captured))
        coVerify(exactly = 0) { telegram.getUpdates(any(), any(), any()) }
    }
    @Test fun `only matching account and challenge authenticate with bounded attempts`() = runBlocking<Unit> {
        manager.beginVerification("cedar", 123)
        assertEquals(LoginVerificationResult.Timeout, manager.verify("other", 123, code.captured))
        assertEquals(LoginVerificationResult.Timeout, manager.verify("cedar", 456, code.captured))
        repeat(4) { assertEquals(LoginVerificationResult.Incorrect, manager.verify("cedar", 123, "wrong")) }
        assertEquals(LoginVerificationResult.Exhausted, manager.verify("cedar", 123, "wrong"))
        assertEquals(LoginVerificationResult.Exhausted, manager.verify("cedar", 123, code.captured))
    }
    @Test fun `changed project or expired challenge cannot authenticate`() = runBlocking<Unit> {
        manager.beginVerification("cedar", 123)
        connected = false
        assertEquals(LoginVerificationResult.Timeout, manager.verify("cedar", 123, code.captured))
        connected = true
        every { SystemClock.elapsedRealtime() } returns 301000L
        assertEquals(0, manager.remainingSeconds("cedar"))
        assertEquals(LoginVerificationResult.Timeout, manager.verify("cedar", 123, code.captured))
    }
    @Test fun `uncertain send errors remain visible and reuse the challenge`() = runBlocking<Unit> {
        coEvery { telegram.sendPrivateLoginCode(123, capture(code)) } throws IOException("offline")
        assertThrows(IOException::class.java) { runBlocking { manager.beginVerification("cedar", 123) } }
        manager.beginVerification("cedar", 123)
        coVerify(exactly = 1) { telegram.sendPrivateLoginCode(any(), any()) }
        assertEquals(LoginVerificationResult.Success(123), manager.verify("cedar", 123, code.captured))
    }
    @Test fun `cancellation propagates and retry does not drain shared updates`() = runBlocking<Unit> {
        coEvery { telegram.sendPrivateLoginCode(123, capture(code)) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { manager.beginVerification("cedar", 123) } }
        manager.beginVerification("cedar", 123)
        coVerify(exactly = 1) { telegram.sendPrivateLoginCode(any(), any()) }
        coVerify(exactly = 0) { telegram.getUpdates(any(), any(), any()) }
    }
}
