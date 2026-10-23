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
    @Test fun staleDraftCannotOverwriteNewerSave() = runBlocking<Unit> {
        val repo = repository()
        repo.upsert(Patient(id = "a", name = "شاي", revision = 1), "shift-1")
        val draft = requireNotNull(repo.getById("a"))
        val saved = repo.updateOptimistically(draft.copy(labs = "new result"), "shift-1", 1)
        assertEquals(2L, saved.revision)
        try { repo.updateOptimistically(draft.copy(labs = "stale result"), "shift-1", 1); fail("Stale edit accepted") }
        catch (_: StalePatientEditException) { }
        assertEquals("new result", repo.getById("a")?.labs)
    }
    @Test fun deleteAndRestoreRequireMatchingRevisionAndShift() = runBlocking<Unit> {
        val repo = repository()
        repo.upsert(Patient(id = "a", name = "شاي", revision = 1), "shift-1")
        try { repo.softDeleteOptimistically("a", "shift-2", 1); fail("Wrong shift accepted") }
        catch (_: StalePatientEditException) { }
        val deleted = repo.softDeleteOptimistically("a", "shift-1", 1)
        assertEquals(2L, deleted.revision)
        assertTrue(repo.getForShift("shift-1").isEmpty())
        try { repo.restoreOptimistically("a", "shift-1", 1); fail("Stale restore accepted") }
        catch (_: StalePatientEditException) { }
        assertEquals(3L, repo.restoreOptimistically("a", "shift-1", 2).revision)
        assertEquals(1, repo.getForShift("shift-1").size)
    }
    @Test fun nonemptyRolloverTargetRemainsUntouched() = runBlocking<Unit> {
        val repo = repository()
        repo.upsert(Patient(id = "existing", name = "شاي"), "shift-1")
        try { repo.insertRolloverIfShiftEmpty(listOf(Patient(id = "incoming", name = "قهوة")), "shift-1")
            fail("Nonempty target accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(listOf("existing"), repo.getForShift("shift-1").map { it.id })
        repo.insertRolloverIfShiftEmpty(listOf(Patient(id = "incoming", name = "قهوة")), "shift-2")
        assertEquals(listOf("incoming"), repo.getForShift("shift-2").map { it.id })
    }
}
