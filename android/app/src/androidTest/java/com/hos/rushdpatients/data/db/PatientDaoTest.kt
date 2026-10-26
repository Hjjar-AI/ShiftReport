package com.hos.rushdpatients.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

import com.hos.rushdpatients.testutil.DatabaseFixture
import com.hos.rushdpatients.data.mapper.PatientMapper
import com.hos.rushdpatients.data.model.Patient
import kotlinx.coroutines.flow.first
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PatientDaoTest : DatabaseFixture() {
    private val dao get() = db.patientDao()
    private fun entity(id: String, shift: String = "shift-1", order: Int = 1) =
        PatientMapper.toEntity(Patient(id = id, name = "شاي $id", admittanceNumber = "1",
            sortOrder = order, updatedAt = Instant.ofEpochMilli(1000)), shift)
    @Test fun upsertReadAndShiftIsolation() = runBlocking<Unit> {
        dao.upsert(entity("a"))
        dao.upsert(entity("b", "shift-2"))
        assertEquals(listOf("a"), dao.getForShift("shift-1").map { it.id })
        assertEquals("shift-2", dao.getShiftId("b"))
        assertEquals("شاي a", dao.getById("a")?.name)
    }
    @Test fun deletedPatientsStayInRecycleAndDisappearFromLiveCount() = runBlocking<Unit> {
        dao.upsert(entity("a"))
        dao.upsert(entity("b"))
        dao.softDelete("a", 5000)
        assertEquals(listOf("b"), dao.observeForShift("shift-1").first().map { it.id })
        assertEquals(1, dao.countForShift("shift-1"))
        assertEquals(listOf("a"), dao.getSoftDeletedForShift("shift-1").map { it.id })
        assertEquals(5000L, dao.getById("a")?.deletedAtEpochMillis)
        assertEquals(0L, dao.getById("a")?.updatedAtEpochMillis)
        assertEquals(2, dao.getAll().size)
    }
    @Test fun replaceSnapshotOnlyDeletesMissingPatientsInSelectedShift() = runBlocking<Unit> {
        dao.upsertAll(listOf(entity("keep"), entity("remove"), entity("other", "shift-2")))
        dao.softDeleteMissingFromShift("shift-1", listOf("keep"), 5000)
        assertNull(dao.getById("keep")?.deletedAtEpochMillis)
        assertEquals(5000L, dao.getById("remove")?.deletedAtEpochMillis)
        assertNull(dao.getById("other")?.deletedAtEpochMillis)
    }
    @Test fun emptyRetainedSnapshotDeletesAllInThatShiftOnly() = runBlocking<Unit> {
        dao.upsertAll(listOf(entity("a"), entity("b", "shift-2")))
        dao.softDeleteMissingFromShift("shift-1", emptyList(), 5000)
        assertTrue(dao.getForShift("shift-1").isEmpty())
        assertEquals(1, dao.getForShift("shift-2").size)
    }
    @Test fun storedOrderAndDuplicateAdmissionNumbersArePreserved() = runBlocking<Unit> {
        dao.upsertAll(listOf(entity("a", order = 3), entity("b", order = 1), entity("c", order = 2)))
        assertEquals(listOf("b", "c", "a"), dao.getForShift("shift-1").map { it.id })
        dao.upsert(entity("a", order = 0))
        assertEquals(listOf("a", "b", "c"), dao.getForShift("shift-1").map { it.id })
    }
    @Test fun missingParentShiftFailsForeignKeyCheck() = runBlocking<Unit> {
        try { dao.upsert(entity("a", "missing")); fail("Missing shift accepted") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
    }
}
