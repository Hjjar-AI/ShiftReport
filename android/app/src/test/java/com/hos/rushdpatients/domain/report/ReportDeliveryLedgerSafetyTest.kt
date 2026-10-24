package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.testutil.DeliveryFixture
import com.hos.rushdpatients.testutil.Fixtures
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ReportDeliveryLedgerSafetyTest : DeliveryFixture(actor = Fixtures.doctor()) {
    private var ledger = ledger()

    private suspend fun attempt(block: suspend (ReportDeliveryRecord) -> Unit = {}) =
        ledger.deliver("shift", -100, "content", "TEXT", "synthetic", actor.id, block = block)
    private suspend fun record() = ledger.observe().first().single()
    private fun recreated() = ledger()

    @Test fun `simultaneous same-route requests admit exactly one transport`() = runBlocking<Unit> {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val first = async(Dispatchers.Default) {
                attempt { calls.incrementAndGet(); entered.complete(Unit); release.await() }
            }
            try {
                entered.await()
                val outcomes = (1..12).map {
                    async(Dispatchers.Default) {
                        try { attempt { calls.incrementAndGet() }; false }
                        catch (_: ReportDeliveryBlockedException) { true }
                    }
                }.awaitAll()
                assertTrue(outcomes.all { it })
                assertEquals(1, calls.get())
                assertEquals(1, ledger.observe().first().size)
            } finally { release.complete(Unit); first.await() }
        }
    }

    @Test fun `cancelled transport retains durable UNKNOWN and releases active guard`() = runBlocking<Unit> {
        withTimeout(5_000) {
            val entered = CompletableDeferred<ReportDeliveryRecord>()
            val job = launch(Dispatchers.Default) { attempt { entered.complete(it); awaitCancellation() } }
            val row = entered.await()
            job.cancelAndJoin()
            assertEquals("UNKNOWN", record().status)
            ledger.requireResolvable(row.id, "UNKNOWN")
            ledger.resolve(row.id, "UNKNOWN", DeliveryResolution.NOT_RECEIVED)
            ledger.check("shift", -100, "content", "TEXT")
        }
    }

    @Test fun `registration write failure prevents transport entirely`() = runBlocking<Unit> {
        coEvery { settings.put("report_delivery_ledger", any()) } throws IllegalStateException("synthetic storage failure")
        var sent = false
        try { attempt { sent = true }; fail("Expected storage failure") }
        catch (e: IllegalStateException) { assertEquals("synthetic storage failure", e.message) }
        assertFalse(sent)
        assertNull(raw.value)
    }

    @Test fun `receipt write failure preserves uncertain delivery and blocks retry`() = runBlocking<Unit> {
        try {
            attempt { row ->
                coEvery { settings.put("report_delivery_ledger", any()) } throws IllegalStateException("receipt storage failure")
                ledger.confirm(row.id, listOf(12))
            }
            fail("Expected receipt write failure")
        } catch (e: IllegalStateException) { assertEquals("receipt storage failure", e.message) }
        assertEquals("UNKNOWN", record().status)
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { attempt() } }
        ledger.requireResolvable(record().id, "UNKNOWN")
    }

    @Test fun `corrupt ledger fails closed instead of starting new history`() = runBlocking<Unit> {
        raw.value = "{broken"
        var sent = false
        try { attempt { sent = true }; fail("Expected corrupt ledger rejection") }
        catch (_: kotlinx.serialization.SerializationException) { }
        assertFalse(sent)
        assertEquals("{broken", raw.value)
    }

    @Test fun `recreated ledger retains uncertain route block across format and content changes`() = runBlocking<Unit> {
        attempt()
        ledger = recreated()
        assertThrows(ReportDeliveryBlockedException::class.java) {
            runBlocking { ledger.check("shift", -100, "new-content", "PDF") }
        }
        ledger.check("other-shift", -100, "content", "TEXT")
        ledger.check("shift", -200, "content", "TEXT")
    }

    @Test fun `recreated ledger retains confirmed duplicate block`() = runBlocking<Unit> {
        attempt { ledger.confirm(it.id, listOf(1, 2)) }
        ledger = recreated()
        assertEquals(listOf(1L, 2L), record().messageIds)
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { attempt() } }
        ledger.check("shift", -100, "different-content", "TEXT")
        ledger.check("shift", -100, "content", "PDF")
    }

    @Test fun `copy permission survives recreation but failed repeat consumes it`() = runBlocking<Unit> {
        attempt { ledger.confirm(it.id, listOf(1)) }
        ledger.resolve(record().id, "SENT", DeliveryResolution.ALLOW_COPY)
        ledger = recreated()
        try { attempt { throw IllegalStateException("ambiguous transport") }; fail("Expected failure") }
        catch (_: IllegalStateException) { }
        val unknown = ledger.observe().first().single { it.status == "UNKNOWN" }
        ledger.resolve(unknown.id, "UNKNOWN", DeliveryResolution.NOT_RECEIVED)
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { attempt() } }
    }

    @Test fun `manual received resolution blocks identical resend after recreation`() = runBlocking<Unit> {
        attempt()
        ledger.resolve(record().id, "UNKNOWN", DeliveryResolution.RECEIVED)
        ledger = recreated()
        assertEquals("SENT", record().status)
        assertTrue(record().messageIds.isEmpty())
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { attempt() } }
    }

    @Test fun `manual not-received resolution permits fresh attempt without deleting history`() = runBlocking<Unit> {
        attempt()
        val first = record()
        ledger.resolve(first.id, "UNKNOWN", DeliveryResolution.NOT_RECEIVED)
        attempt { ledger.confirm(it.id, listOf(2)) }
        val rows = ledger.observe().first()
        assertEquals(listOf("NOT_SENT", "SENT"), rows.map { it.status })
        assertEquals(first.id, rows.first().id)
        assertNotEquals(rows.first().id, rows.last().id)
    }

    @Test fun `invalid receipt variants never change uncertain record`() = runBlocking<Unit> {
        attempt()
        val before = raw.value
        for (ids in listOf(emptyList(), listOf(0L), listOf(-1L), listOf(1L, 0L))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.confirm(record().id, ids) } }
            assertEquals(before, raw.value)
        }
    }

    @Test fun `unknown and already confirmed receipt identities cannot mutate ledger`() = runBlocking<Unit> {
        attempt { ledger.confirm(it.id, listOf(1)) }
        val before = raw.value
        for (id in listOf("missing", record().id)) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.confirm(id, listOf(2)) } }
        }
        assertEquals(before, raw.value)
    }

    @Test fun `signed out or deleted sender cannot start transport`() = runBlocking<Unit> {
        every { sessions.current() } returns null
        assertThrows(IllegalStateException::class.java) { runBlocking { attempt { fail("Unexpected transport") } } }
        every { sessions.current() } returns Session(actor.id, actor.fullName, null, actor.role, Fixtures.timestamp)
        coEvery { doctors.getActiveById(actor.id) } returns null
        assertThrows(IllegalStateException::class.java) { runBlocking { attempt { fail("Unexpected transport") } } }
        assertNull(raw.value)
    }

    @Test fun `sender deactivation prevents resolution without losing uncertainty`() = runBlocking<Unit> {
        attempt()
        val row = record()
        coEvery { doctors.getActiveById(actor.id) } returns null
        assertThrows(IllegalStateException::class.java) {
            runBlocking { ledger.resolve(row.id, "UNKNOWN", DeliveryResolution.NOT_RECEIVED) }
        }
        assertEquals("UNKNOWN", record().status)
    }

    @Test fun `copy permission cannot resolve uncertain record or alter status`() = runBlocking<Unit> {
        attempt()
        val before = raw.value
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.resolve(record().id, "UNKNOWN", DeliveryResolution.ALLOW_COPY) }
        }
        assertEquals(before, raw.value)
    }

    @Test fun `publication metadata and transport result survive ledger wrapper`() = runBlocking<Unit> {
        val result = ledger.deliver("shift", -100, "content", "TEXT", "synthetic", actor.id,
            metadata = mapOf("publicationMessageId" to 42L)) { row ->
            assertEquals(mapOf("publicationMessageId" to 42L), row.metadata)
            ledger.confirm(row.id, listOf(8))
            "transport-result"
        }
        assertEquals("transport-result", result)
        assertEquals(mapOf("publicationMessageId" to 42L), recreated().observe().first().single().metadata)
    }

    @Test fun `failed manual resolution preserves uncertainty until a successful retry`() = runBlocking<Unit> {
        attempt()
        val row = record()
        val before = raw.value
        coEvery { settings.put("report_delivery_ledger", any()) } throws IllegalStateException("resolution storage failure")
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { ledger.resolve(row.id, "UNKNOWN", DeliveryResolution.NOT_RECEIVED) }
        }
        assertEquals("resolution storage failure", failure.message)
        assertEquals(before, raw.value)
        ledger = recreated()
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { attempt { fail("Unexpected transport") } } }
        acceptLedgerWrites()
        ledger.resolve(row.id, "UNKNOWN", DeliveryResolution.NOT_RECEIVED)
        attempt { ledger.confirm(it.id, listOf(21)) }
        assertEquals(listOf("NOT_SENT", "SENT"), ledger.observe().first().map { it.status })
    }

    @Test fun `failed copy permission write cannot authorize another delivery`() = runBlocking<Unit> {
        attempt { ledger.confirm(it.id, listOf(1)) }
        val row = record()
        val before = raw.value
        coEvery { settings.put("report_delivery_ledger", any()) } throws IllegalStateException("permission storage failure")
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { ledger.resolve(row.id, "SENT", DeliveryResolution.ALLOW_COPY) }
        }
        assertEquals("permission storage failure", failure.message)
        assertEquals(before, raw.value)
        acceptLedgerWrites()
        ledger = recreated()
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { attempt { fail("Unexpected transport") } } }
        assertEquals(before, raw.value)
    }

    @Test fun `failed repeat registration retains its durable one-use permission`() = runBlocking<Unit> {
        attempt { ledger.confirm(it.id, listOf(1)) }
        ledger.resolve(record().id, "SENT", DeliveryResolution.ALLOW_COPY)
        val before = raw.value
        coEvery { settings.put("report_delivery_ledger", any()) } throws IllegalStateException("registration storage failure")
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { attempt { fail("Unexpected transport") } }
        }
        assertEquals("registration storage failure", failure.message)
        assertEquals(before, raw.value)
        acceptLedgerWrites()
        ledger = recreated()
        attempt { ledger.confirm(it.id, listOf(2)) }
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { attempt { fail("Unexpected transport") } } }
        assertEquals(listOf(listOf(1L), listOf(2L)), ledger.observe().first().map { it.messageIds })
    }

    @Test fun `another destination can finish while the first transport remains active`() = runBlocking<Unit> {
        withTimeout(5_000) {
            val entered = CompletableDeferred<ReportDeliveryRecord>()
            val release = CompletableDeferred<Unit>()
            val first = async(Dispatchers.Default) {
                attempt { entered.complete(it); release.await() }
            }
            try {
                val firstRow = entered.await()
                ledger.deliver("shift", -200, "content", "TEXT", "synthetic", actor.id) {
                    ledger.confirm(it.id, listOf(22))
                }
                val rows = ledger.observe().first()
                assertEquals("UNKNOWN", rows.single { it.id == firstRow.id }.status)
                assertEquals(listOf(22L), rows.single { it.id != firstRow.id }.messageIds)
                assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { attempt { fail("Unexpected transport") } } }
            } finally { release.complete(Unit); first.await() }
        }
    }

    @Test fun `admin demotion after opening resolution rejects the write without changing history`() = runBlocking<Unit> {
        attempt()
        val row = record()
        val admin = Fixtures.doctor(id = "other-admin", fullName = "بلوط", rank = 1)
        every { sessions.current() } returns Session(admin.id, admin.fullName, null, admin.role, Fixtures.timestamp)
        coEvery { doctors.getActiveById(admin.id) } returns admin
        ledger.requireResolvable(row.id, "UNKNOWN")
        val before = raw.value
        coEvery { doctors.getActiveById(admin.id) } returns admin.copy(rank = 0)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.resolve(row.id, "UNKNOWN", DeliveryResolution.RECEIVED) }
        }
        assertEquals(before, raw.value)
        assertEquals("UNKNOWN", recreated().observe().first().single().status)
    }

    @Test fun `non-admin sender can resolve their own delivery after ledger recreation`() = runBlocking<Unit> {
        assertFalse(actor.isAdmin)
        attempt()
        val row = record()
        ledger = recreated()
        ledger.requireResolvable(row.id, "UNKNOWN")
        ledger.resolve(row.id, "UNKNOWN", DeliveryResolution.RECEIVED)
        assertEquals("SENT", record().status)
        assertThrows(ReportDeliveryBlockedException::class.java) { runBlocking { attempt { fail("Unexpected transport") } } }
    }
}
