package com.hos.rushdpatients.network.telegram

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramRetryTest {
    @Test fun `rate limit uses retry-after with virtual time`() = runTest {
        var calls = 0
        val start = testScheduler.currentTime
        val result = TelegramRetry.withRetry(maxAttempts = 3) {
            calls++
            if (calls == 1) throw TelegramException(429, "synthetic", retryAfterSeconds = 7)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, calls)
        assertEquals(7000L, testScheduler.currentTime - start)
    }
    @Test fun `server failures stop at limit with increasing backoff`() = runTest {
        var calls = 0
        val failure = TelegramException(503, "synthetic")
        try { TelegramRetry.withRetry<Unit>(maxAttempts = 3) { calls++; throw failure }; fail("Expected failure") }
        catch (e: TelegramException) { assertSame(failure, e) }
        assertEquals(3, calls)
        assertEquals(6000L, testScheduler.currentTime)
    }
    @Test fun `ambiguous delivery authorization and forbidden server retries are never repeated`() = runTest {
        for (code in listOf(-1, 400, 401, 403, 404, 503)) {
            var calls = 0
            try { TelegramRetry.withRetry<Unit>(allowServerRetry = false) {
                calls++; throw TelegramException(code, "synthetic")
            }; fail("Expected failure") } catch (_: TelegramException) { }
            assertEquals(1, calls)
        }
    }
    @Test fun `cancellation and ordinary failures propagate without retry`() = runTest {
        for (failure in listOf(CancellationException("cancel"), IllegalStateException("synthetic"))) {
            var calls = 0
            try { TelegramRetry.withRetry<Unit> { calls++; throw failure }; fail("Expected failure") }
            catch (e: Exception) { assertSame(failure, e) }
            assertEquals(1, calls)
        }
    }
}
