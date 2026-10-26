package com.hos.rushdpatients.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

import com.hos.rushdpatients.testutil.DatabaseFixture

@RunWith(AndroidJUnit4::class)
class ShiftDaoTest : DatabaseFixture() {
    private val dao get() = db.shiftDao()
    @Test fun revisionCheckedRosterRejectsStaleUpdateWithoutOverwriting() = runBlocking<Unit> {
        assertEquals(1, dao.updateDoctorIds("shift-1", "a,b,c", 0, 1000))
        assertEquals(0, dao.updateDoctorIds("shift-1", "stale", 0, 2000))
        val saved = requireNotNull(dao.getById("shift-1"))
        assertEquals("a,b,c", saved.doctorIdsCsv)
        assertEquals(1L, saved.revision)
        assertEquals(1000L, saved.updatedAtEpochMillis)
    }
    @Test fun sortAndRosterShareRevisionGuard() = runBlocking<Unit> {
        assertEquals(1, dao.updateDoctorIds("shift-1", "a", 0, 1000))
        assertEquals(0, dao.updateSortSpec("shift-1", "stale", 0, 2000))
        assertEquals(1, dao.updateSortSpec("shift-1", "reviewed", 1, 3000))
        assertEquals(2L, dao.getById("shift-1")?.revision)
        assertEquals("reviewed", dao.getById("shift-1")?.sortSpecJson)
    }
    @Test fun publicationLineageAndDeliveryReceiptsRemainIndependent() = runBlocking<Unit> {
        dao.updatePublicationMetadata("shift-1", 7, 8, "snapshot", "base", "device", 1000)
        dao.markSent("shift-1", 2000, 9, 10)
        val saved = requireNotNull(dao.getById("shift-1"))
        assertEquals(7L, saved.csvMessageId)
        assertEquals(8L, saved.revision)
        assertEquals("snapshot", saved.snapshotId)
        assertEquals("base", saved.baseSnapshotId)
        assertEquals(9L, saved.reportMessageId)
        assertEquals(10L, saved.pdfMessageId)
    }
    @Test fun latestAndRecentUseShiftDate() = runBlocking<Unit> {
        assertEquals("shift-2", dao.getLatest()?.id)
        assertEquals(listOf("shift-2"), dao.getRecent(1).map { it.id })
    }
}
