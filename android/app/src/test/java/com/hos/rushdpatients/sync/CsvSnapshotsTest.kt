package com.hos.rushdpatients.sync

import org.junit.Assert.*
import org.junit.Test

class CsvSnapshotsTest {
    private val old = CsvSnapshot("old", 10, "2026-01-01", 900)
    private val current = CsvSnapshot("current", 20, "2026-01-02", 800)
    private val uploaded = CsvSnapshot("new", 30, "2026-01-03", 700)
    private val state = SyncState(csvFileId = current.fileId, csvMessageId = current.messageId,
        csvDate = current.date, csvUpdatedAt = current.updatedAt, previousCsvFileId = old.fileId,
        previousCsvMessageId = old.messageId, previousCsvDate = old.date, previousCsvUpdatedAt = old.updatedAt,
        doctorsFileId = "registry", announcementMessageId = 7)

    @Test fun `incomplete pointers are unavailable`() {
        assertNull(SyncState(csvFileId = "f").currentCsvSnapshot())
        assertNull(SyncState(csvMessageId = 1).currentCsvSnapshot())
        assertNull(SyncState(previousCsvFileId = "f").previousCsvSnapshot())
        assertNull(SyncState(previousCsvMessageId = 1).previousCsvSnapshot())
    }
    @Test fun `every publication keeps the immediately preceding snapshot`() {
        val result = state.withPublishedCsv(uploaded)
        assertEquals(uploaded, result.currentCsvSnapshot())
        assertEquals(current, result.previousCsvSnapshot())
        assertEquals("registry", result.doctorsFileId)
        assertEquals(7L, result.announcementMessageId)
        assertEquals(current, state.currentCsvSnapshot())
    }
    @Test fun `rotation uses message order rather than timestamps`() {
        val result = state.withPublishedCsv(uploaded)
        assertEquals(uploaded, result.currentCsvSnapshot())
        assertEquals(current, result.previousCsvSnapshot())
    }
    @Test fun `late older upload cannot displace newest snapshot during rotation`() {
        val result = state.withPublishedCsv(old.copy(messageId = 15))
        assertEquals(current, result.currentCsvSnapshot())
        assertEquals(15L, result.previousCsvSnapshot()?.messageId)
    }
    @Test fun `repeated publication does not duplicate recovery pointer`() {
        val result = state.withPublishedCsv(current)
        assertEquals(current, result.currentCsvSnapshot())
        assertEquals(old, result.previousCsvSnapshot())
    }
    @Test fun `first publication has no recovery`() {
        for (rotate in listOf(false, true)) {
            val result = SyncState().withPublishedCsv(uploaded)
            assertEquals(uploaded, result.currentCsvSnapshot())
            assertNull(result.previousCsvSnapshot())
            assertEquals(0L, result.previousCsvUpdatedAt)
        }
    }
    @Test fun `missing current still retains a complete recovery pointer`() {
        val result = state.copy(csvFileId = null).withPublishedCsv(uploaded)
        assertEquals(uploaded, result.currentCsvSnapshot())
        assertEquals(old, result.previousCsvSnapshot())
    }
}
