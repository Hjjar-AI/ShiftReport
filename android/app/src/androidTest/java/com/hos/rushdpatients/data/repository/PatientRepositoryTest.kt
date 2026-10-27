package com.hos.rushdpatients.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

import com.hos.rushdpatients.testutil.DatabaseFixture
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.util.DefaultDispatcherProvider

@RunWith(AndroidJUnit4::class)
class PatientRepositoryTest : DatabaseFixture() {
    private fun repository() = PatientRepository(db.patientDao(), db, DefaultDispatcherProvider())
    @Test fun lastSaveWinsWithoutRevisionConflict() = runBlocking<Unit> {
        val repo = repository()
        repo.upsert(Patient(id = "a", name = "شاي", revision = 1), "shift-1")
        val draft = requireNotNull(repo.getById("a"))
        val saved = repo.save(draft.copy(labs = "new result"), "shift-1")
        assertEquals(2L, saved.revision)
        val last = repo.save(draft.copy(labs = "last save"), "shift-1")
        assertEquals(3L, last.revision)
        assertEquals("last save", repo.getById("a")?.labs)
    }
    @Test fun deleteAndRestoreRequireMatchingSectionAndDeletionState() = runBlocking<Unit> {
        val repo = repository()
        repo.upsert(Patient(id = "a", name = "شاي", revision = 1), "shift-1")
        try { repo.softDelete("a", "shift-2"); fail("Wrong section accepted") }
        catch (_: StalePatientEditException) { }
        val deleted = repo.softDelete("a", "shift-1")
        assertEquals(2L, deleted.revision)
        assertTrue(repo.getForShift("shift-1").isEmpty())
        try { repo.softDelete("a", "shift-1"); fail("Repeated deletion accepted") }
        catch (_: StalePatientEditException) { }
        try { repo.restore("a", "shift-2"); fail("Wrong section restore accepted") }
        catch (_: StalePatientEditException) { }
        assertEquals(3L, repo.restore("a", "shift-1").revision)
        try { repo.restore("a", "shift-1"); fail("Active patient restored twice") }
        catch (_: StalePatientEditException) { }
        assertEquals(1, repo.getForShift("shift-1").size)
    }
    @Test fun missingDeletedAndWrongSectionSavesPreserveStoredRows() = runBlocking<Unit> {
        val repo = repository()
        repo.upsert(Patient(id = "existing", name = "شاي"), "shift-1")
        for (id in listOf("missing", "existing")) {
            try { repo.save(Patient(id = id, labs = "rejected"), "shift-2"); fail("Unavailable patient saved") }
            catch (_: StalePatientEditException) { }
        }
        assertEquals(listOf("existing"), repo.getForShift("shift-1").map { it.id })
        assertEquals("", repo.getById("existing")?.labs)
        assertNull(repo.getById("missing"))
        repo.softDelete("existing", "shift-1")
        try { repo.save(Patient(id = "existing", labs = "rejected"), "shift-1"); fail("Deleted patient saved") }
        catch (_: StalePatientEditException) { }
        assertTrue(requireNotNull(repo.getById("existing")).isDeleted)
    }
    @Test fun staleClinicalSaveKeepsLatestStoredAssignmentMarking() = runBlocking<Unit> {
        val repo = repository()
        val yesterday = com.hos.rushdpatients.util.ShiftDate.current().minusDays(1)
        val stale = com.hos.rushdpatients.data.model.Patient(id = "marked", name = "شاي", assignmentChangedOn = yesterday)
        repo.upsert(stale, "shift-1")
        val latest = stale.copy(assignmentChangedOn = yesterday.plusDays(1), revision = 2)
        repo.upsert(latest, "shift-1")
        val saved = repo.save(stale.copy(labs = "later clinical edit"), "shift-1")
        assertEquals(latest.assignmentChangedOn, saved.assignmentChangedOn)
        assertEquals(3L, saved.revision)
        assertEquals("later clinical edit", repo.getById(stale.id)?.labs)
    }

}
