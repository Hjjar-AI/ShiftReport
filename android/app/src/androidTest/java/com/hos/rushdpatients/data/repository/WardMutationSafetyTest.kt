package com.hos.rushdpatients.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.mapper.DoctorMapper
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.testutil.DatabaseFixture
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Fresh in-memory Room transactions; never touches installed clinical records. */
@RunWith(AndroidJUnit4::class)
class WardMutationSafetyTest : DatabaseFixture() {
    private val dispatchers = DefaultDispatcherProvider()
    private val actor = Session("actor", "أرز", null, Role.NON_ADMIN, Instant.EPOCH)
    private fun patients() = PatientRepository(db.patientDao(), db, dispatchers)
    private fun settings() = SettingsRepository(db.settingDao(), dispatchers)
    private fun shifts() = ShiftRepository(db.shiftDao(), dispatchers)
    private fun mutations() = WardMutationRepository(db, patients(),
        DoctorRepository(db.doctorDao(), patients(), db, settings(), dispatchers),
        shifts(), settings(), AuditRepository(db.auditDao(), dispatchers), dispatchers)
    private suspend fun seedCurrent() {
        seedShift("today", ShiftDate.current())
        db.doctorDao().upsert(DoctorMapper.toEntity(Doctor(id = "actor", fullName = "أرز",
            firstName = "أرز", lastName = "شجرة", gender = Gender.MALE)))
    }
    private suspend fun addTask(done: Boolean = false): Patient {
        mutations().addPatient(Patient(id = "a", name = "شاي", tasks = listOf(PatientTask("task", "فحص", done = done))), "today", actor)
        return requireNotNull(patients().getById("a"))
    }
    private suspend fun clearPending() = settings().putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
    private suspend fun assertPendingClear() = assertFalse(settings().getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))

    @Test fun invalidTaskOwnerRollsBackPatientAuditAndPending() = runBlocking<Unit> {
        seedCurrent()
        clearPending()
        try {
            mutations().addPatient(Patient(id = "a", name = "شاي", tasks = listOf(
                PatientTask("task", "فحص", ownerDoctorId = "missing"))), "today", actor)
            fail("Unknown task owner accepted")
        } catch (_: IllegalArgumentException) { }
        assertNull(patients().getById("a"))
        assertTrue(db.auditDao().getForPatient("a").isEmpty())
        assertPendingClear()
    }
    @Test fun anonymousCompletionCannotWriteOrMarkPending() = runBlocking<Unit> {
        seedCurrent()
        val before = addTask()
        val audits = db.auditDao().getForPatient("a").size
        clearPending()
        try {
            mutations().updatePatient(before.copy(tasks = listOf(before.tasks.single().copy(done = true))), "today", null)
            fail("Anonymous completion accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, patients().getById("a"))
        assertEquals(audits, db.auditDao().getForPatient("a").size)
        assertPendingClear()
    }
    @Test fun completedTaskAttributionCannotBeRewrittenByLaterDraft() = runBlocking<Unit> {
        seedCurrent()
        val before = addTask(done = true)
        val original = before.tasks.single()
        val saved = mutations().updatePatient(before.copy(tasks = listOf(original.copy(
            description = "فحص معدل", completedByDoctorId = "forged", completedByName = "forged",
            completedAtEpochMillis = 1))), "today", actor)
        assertEquals("فحص معدل", saved.tasks.single().description)
        assertEquals(original.completedByDoctorId, saved.tasks.single().completedByDoctorId)
        assertEquals(original.completedByName, saved.tasks.single().completedByName)
        assertEquals(original.completedAtEpochMillis, saved.tasks.single().completedAtEpochMillis)
    }
    @Test fun reopeningTaskClearsCompletionAndKeepsDescription() = runBlocking<Unit> {
        seedCurrent()
        val before = addTask(done = true)
        // The editor clears completion metadata when toggling to pending.
        val pending = before.tasks.single().copy(done = false, completedByDoctorId = null,
            completedByName = null, completedAtEpochMillis = null)
        val saved = mutations().updatePatient(before.copy(tasks = listOf(pending)), "today", actor)
        assertEquals(pending.description, saved.tasks.single().description)
        assertFalse(saved.tasks.single().done)
        assertNull(saved.tasks.single().completedByDoctorId)
        assertNull(saved.tasks.single().completedAtEpochMillis)
    }
    @Test fun taskOwnerNameIsResolvedFromRegistry() = runBlocking<Unit> {
        seedCurrent()
        mutations().addPatient(Patient(id = "a", name = "شاي", tasks = listOf(PatientTask(
            "task", "  فحص  ", ownerDoctorId = "actor", ownerName = "stale label"))), "today", actor)
        val task = requireNotNull(patients().getById("a")).tasks.single()
        assertEquals("أرز", task.ownerName)
        assertEquals("فحص", task.description)
    }
    @Test fun duplicatePatientAdditionCannotOverwriteClinicalData() = runBlocking<Unit> {
        seedCurrent()
        val before = addTask()
        clearPending()
        val count = db.auditDao().getForPatient("a").size
        try {
            mutations().addPatient(before.copy(labs = "replacement"), "today", actor)
            fail("Duplicate patient accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, patients().getById("a"))
        assertEquals(count, db.auditDao().getForPatient("a").size)
        assertPendingClear()
    }
    @Test fun invalidRostersCannotChangeShiftOrPendingState() = runBlocking<Unit> {
        seedCurrent()
        val before = requireNotNull(shifts().getById("today"))
        clearPending()
        for (ids in listOf(emptyList(), listOf("actor", "actor"), listOf("missing"))) {
            try {
                mutations().setShiftDoctors("today", ids, before.revision, actor)
                fail("Invalid roster accepted")
            } catch (_: IllegalArgumentException) { }
            assertEquals(before, shifts().getById("today"))
            assertPendingClear()
        }
    }
    @Test fun staleRosterCannotReplaceNewerSelection() = runBlocking<Unit> {
        seedCurrent()
        val before = requireNotNull(shifts().getById("today"))
        mutations().setShiftDoctors("today", listOf("actor"), before.revision, actor)
        val saved = requireNotNull(shifts().getById("today"))
        clearPending()
        try {
            mutations().setShiftDoctors("today", listOf("actor"), before.revision, actor)
            fail("Stale roster accepted")
        } catch (_: StaleShiftEditException) { }
        assertEquals(saved, shifts().getById("today"))
        assertPendingClear()
    }
    @Test fun staleSortCannotReorderPatientsOrMarkPending() = runBlocking<Unit> {
        seedCurrent()
        val patient = addTask()
        val before = requireNotNull(shifts().getById("today"))
        shifts().updateSortSpec("today", SortSpecCodec.encode(SortSpec.EMPTY), before.revision)
        val saved = requireNotNull(shifts().getById("today"))
        clearPending()
        try {
            mutations().setShiftSort("today", SortSpecCodec.encode(SortSpec.DEFAULT), before.revision, actor)
            fail("Stale sort accepted")
        } catch (_: StaleShiftEditException) { }
        assertEquals(saved, shifts().getById("today"))
        assertEquals(patient, patients().getById("a"))
        assertPendingClear()
    }
    @Test fun wrongShiftDeleteCannotRecyclePatientOrWriteAudit() = runBlocking<Unit> {
        seedCurrent()
        // Patient belongs to an older shift, but mutation targets today's editable shift.
        patients().upsert(Patient(id = "a", name = "شاي", revision = 1), "shift-1")
        val before = requireNotNull(patients().getById("a"))
        clearPending()
        try {
            mutations().deletePatient(before, "today", actor)
            fail("Cross-shift deletion accepted")
        } catch (_: StalePatientEditException) { }
        assertEquals(before, patients().getById("a"))
        assertTrue(db.auditDao().getForPatient("a").isEmpty())
        assertPendingClear()
    }
    @Test fun partialRolloverCannotDropUnselectedSourcePatients() = runBlocking<Unit> {
        seedCurrent()
        val first = Patient(id = "old-a", name = "شاي", revision = 1)
        patients().upsertAll(listOf(first, Patient(id = "old-b", name = "قهوة", revision = 1)), "shift-1")
        val source = requireNotNull(patients().getById(first.id))
        val target = requireNotNull(shifts().getById("today"))
        clearPending()
        try {
            mutations().rollover(listOf(source.copy(id = "new-a")), "today", target.revision,
                listOf(source to "keep"), actor)
            fail("Partial rollover accepted")
        } catch (_: IllegalArgumentException) { }
        assertTrue(patients().getForShift("today").isEmpty())
        assertEquals(2, patients().getForShift("shift-1").size)
        assertEquals(target, shifts().getById("today"))
        assertPendingClear()
    }
}
