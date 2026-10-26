package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.testutil.DeliveryFixture
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.Role
import org.junit.Assert.*
import org.junit.Test
import com.hos.rushdpatients.domain.auth.Session
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant

class ReportDeliveryLedgerTest : DeliveryFixture(actor = Fixtures.doctor()) {
    private val ledger = ledger()
    private suspend fun begin(block: suspend (ReportDeliveryRecord) -> Unit = {}) =
        ledger.deliver("shift", -100, "content", "TEXT", "synthetic", "doc-1", block = block)
    @Test fun `UNKNOWN persists before transport and blocks all content on same route`() = runBlocking<Unit> {
        begin { record ->
            assertEquals(DeliveryStatus.UNKNOWN, ledger.observe().first().single().status)
            assertTrue(raw.value.orEmpty().contains(record.id))
        }
        assertThrows(ReportDeliveryBlockedException::class.java) {
            runBlocking { ledger.check("shift", -100, "different", "PDF") }
        }
        ledger.check("shift", -200, "content", "TEXT")
    }
    @Test fun `transport failure preserves UNKNOWN until explicit resolution`() = runBlocking<Unit> {
        try { begin { throw IllegalStateException("synthetic network failure") }; fail("Expected failure") }
        catch (_: IllegalStateException) { }
        val record = ledger.observe().first().single()
        assertEquals(DeliveryStatus.UNKNOWN, record.status)
        ledger.resolve(record.id, DeliveryStatus.UNKNOWN, DeliveryResolution.NOT_RECEIVED)
        ledger.check("shift", -100, "content", "TEXT")
    }
    @Test fun `confirmed content requires explicit one-use repeat permission`() = runBlocking<Unit> {
        begin { ledger.confirm(it.id, listOf(1)) }
        val sent = ledger.observe().first().single()
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { begin() } }
        ledger.resolve(sent.id, DeliveryStatus.SENT, DeliveryResolution.ALLOW_COPY)
        begin { ledger.confirm(it.id, listOf(2)) }
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { begin() } }
        assertEquals(listOf(DeliveryStatus.SENT, DeliveryStatus.SENT), ledger.observe().first().map { it.status })
    }
    @Test fun `active request cannot be resolved and invalid receipt cannot confirm`() = runBlocking<Unit> {
        begin { record ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { ledger.resolve(record.id, DeliveryStatus.UNKNOWN, DeliveryResolution.NOT_RECEIVED) }
            }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.confirm(record.id, emptyList()) } }
        }
        val record = ledger.observe().first().single()
        ledger.requireResolvable(record.id, DeliveryStatus.UNKNOWN)
    }
    @Test fun `resolution requires sender or live admin and current expected status`() = runBlocking<Unit> {
        begin()
        val record = ledger.observe().first().single()
        every { sessions.current() } returns Session("other", "بلوط", null, Role.ADMIN, Instant.EPOCH)
        coEvery { doctors.getActiveById("other") } returns Fixtures.doctor(id = "other", rank = 0)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.resolve(record.id, DeliveryStatus.UNKNOWN, DeliveryResolution.RECEIVED) }
        }
        coEvery { doctors.getActiveById("other") } returns Fixtures.doctor(id = "other", rank = 1)
        ledger.resolve(record.id, DeliveryStatus.UNKNOWN, DeliveryResolution.RECEIVED)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.resolve(record.id, DeliveryStatus.UNKNOWN, DeliveryResolution.NOT_RECEIVED) }
        }
    }
    @Test fun `changed actor cannot start delivery`() = runBlocking<Unit> {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.deliver("shift", -100, "content", "TEXT", "synthetic", "stale") { } }
        }
        assertNull(raw.value)
    }
}
