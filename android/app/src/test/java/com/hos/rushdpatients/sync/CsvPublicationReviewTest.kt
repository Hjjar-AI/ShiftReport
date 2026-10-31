package com.hos.rushdpatients.sync

import org.junit.Assert.*
import org.junit.Test

class CsvPublicationReviewTest {
    private val source = SyncState(csvFileId = "csv", csvMessageId = 10,
        doctorsFileId = "doctors", doctorsMessageId = 20, doctorsUpdatedAt = 100)
    @Test fun `absent source never authorizes publication`() {
        assertFalse(sameDoctorPublicationSource(null, source))
        assertFalse(samePublicationSource(null, source))
    }
    @Test fun `every registry pointer and content change invalidates doctor review`() {
        listOf(source.copy(doctorsFileId = "other"), source.copy(doctorsMessageId = 21),
            source.copy(doctorsUpdatedAt = 101)).forEach {
            assertFalse(sameDoctorPublicationSource(source, it))
            assertFalse(samePublicationSource(source, it))
        }
    }
    @Test fun `csv pointer changes invalidate patient publication but not doctor review`() {
        listOf(source.copy(csvFileId = "other"), source.copy(csvMessageId = 11)).forEach {
            assertTrue(sameDoctorPublicationSource(source, it))
            assertFalse(samePublicationSource(source, it))
        }
    }
    @Test fun `unrelated recovery changes preserve source review`() {
        val changed = source.copy(previousCsvFileId = "recovery", previousCsvMessageId = 9)
        assertTrue(samePublicationSource(source, changed))
        assertTrue(sameDoctorPublicationSource(source, source))
    }
    @Test fun `unknown protocol kinds and versions are rejected`() {
        assertFalse(SyncState(kind = "other").isRecognized)
        assertFalse(SyncState(v = 0).isRecognized)
        assertFalse(SyncState(v = SyncState.CURRENT_VERSION + 1).isRecognized)
        (1 until SyncState.CURRENT_VERSION).forEach { assertFalse(SyncState(v = it).isRecognized) }
        assertTrue(SyncState(v = SyncState.CURRENT_VERSION).isRecognized)
    }
}
