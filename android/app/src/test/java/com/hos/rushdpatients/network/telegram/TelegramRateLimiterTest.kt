package com.hos.rushdpatients.network.telegram

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramRateLimiterTest {
    private val interval = TelegramConfig.MIN_CALL_INTERVAL_MS

    @Test fun `first call runs immediately and returns result`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        assertEquals("ok", limiter.withRateLimit(1) { "ok" })
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun `same chat waits full interval after previous call completes`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        val release = CompletableDeferred<Unit>()
        val first = async { limiter.withRateLimit(1) { release.await() } }
        runCurrent()
        val second = async { limiter.withRateLimit(1) { testScheduler.currentTime } }
        advanceTimeBy(500)
        runCurrent()
        assertFalse(second.isCompleted)
        release.complete(Unit)
        runCurrent()
        advanceTimeBy(interval - 1)
        runCurrent()
        assertFalse(second.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(500 + interval, second.await())
        first.await()
    }

    @Test fun `different chats proceed while first chat is occupied`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        val release = CompletableDeferred<Unit>()
        val first = async { limiter.withRateLimit(1) { release.await() } }
        runCurrent()
        assertEquals("independent", limiter.withRateLimit(2) { "independent" })
        assertFalse(first.isCompleted)
        release.complete(Unit)
        first.await()
    }

    @Test fun `cancelled mutex waiter never enters or blocks later callers`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        val release = CompletableDeferred<Unit>()
        val first = async { limiter.withRateLimit(1) { release.await() } }
        runCurrent()
        var cancelledEntered = false
        val cancelled = async { limiter.withRateLimit(1) { cancelledEntered = true } }
        runCurrent()
        cancelled.cancelAndJoin()
        release.complete(Unit)
        first.await()
        assertEquals("later", limiter.withRateLimit(1) { "later" })
        assertFalse(cancelledEntered)
        assertEquals(interval, testScheduler.currentTime)
    }

    @Test fun `cancellation during cooldown releases mutex without executing request`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        limiter.withRateLimit(1) { Unit }
        var entered = false
        val waiting = async { limiter.withRateLimit(1) { entered = true } }
        runCurrent()
        advanceTimeBy(interval / 2)
        waiting.cancelAndJoin()
        limiter.withRateLimit(1) { Unit }
        assertFalse(entered)
        assertEquals(interval, testScheduler.currentTime)
    }

    @Test fun `request failure propagates and next request still observes cooldown`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        val failure = IllegalStateException("synthetic")
        val result = runCatching { limiter.withRateLimit<Unit>(1) { throw failure } }
        assertSame(failure, result.exceptionOrNull())
        assertEquals("recovered", limiter.withRateLimit(1) { "recovered" })
        assertEquals(interval, testScheduler.currentTime)
    }

    @Test fun `cancelling active request preserves cooldown and releases mutex`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        val first = async { limiter.withRateLimit(1) { CompletableDeferred<Unit>().await() } }
        runCurrent()
        advanceTimeBy(200)
        first.cancelAndJoin()
        limiter.withRateLimit(1) { Unit }
        assertEquals(200 + interval, testScheduler.currentTime)
    }

    @Test fun `reset affects only selected chat`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        limiter.withRateLimit(1) { Unit }
        limiter.withRateLimit(2) { Unit }
        limiter.reset(1)
        limiter.withRateLimit(1) { Unit }
        assertEquals(0L, testScheduler.currentTime)
        limiter.withRateLimit(2) { Unit }
        assertEquals(interval, testScheduler.currentTime)
    }

    @Test fun `elapsed cooldown permits immediate request`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        limiter.withRateLimit(1) { Unit }
        advanceTimeBy(interval + 1)
        val before = testScheduler.currentTime
        limiter.withRateLimit(1) { Unit }
        assertEquals(before, testScheduler.currentTime)
    }

    @Test fun `twelve queued calls never overlap and each receives its own result`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        var active = 0
        var maximum = 0
        val starts = mutableListOf<Long>()
        val results = (1..12).map { value -> async {
            limiter.withRateLimit(1) {
                active++
                maximum = maxOf(maximum, active)
                starts += testScheduler.currentTime
                try { delay(10); value } finally { active-- }
            }
        } }.awaitAll()
        assertEquals((1..12).toList(), results)
        assertEquals(1, maximum)
        assertEquals(0, active)
        assertTrue(starts.zipWithNext().all { (first, next) -> next - first >= interval + 10 })
    }

    @Test fun `reset during an active request cannot unlock its waiting caller`() = runTest {
        val limiter = TelegramRateLimiter { testScheduler.currentTime }
        val release = CompletableDeferred<Unit>()
        val first = async { limiter.withRateLimit(1) { release.await() } }
        runCurrent()
        limiter.reset(1)
        val next = async { limiter.withRateLimit(1) { "next" } }
        runCurrent()
        assertFalse(next.isCompleted)
        release.complete(Unit)
        first.await()
        assertEquals("next", next.await())
        assertEquals(interval, testScheduler.currentTime)
    }
}
