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
        shifts(), AuditRepository(db.auditDao(), dispatchers), dispatchers)
    private suspend fun seedCurrent() {
        seedShift("today", ShiftDate.current())
        db.doctorDao().upsert(DoctorMapper.toEntity(Doctor(id = "actor", fullName = "أرز",
            firstName = "أرز", lastName = "شجرة", gender = Gender.MALE)))
    }
    private suspend fun addTask(done: Boolean = false): Patient {
        mutations().addPatient(Patient(id = "a", name = "شاي", tasks = listOf(PatientTask("task", "فحص", done = done))), "today")
        return requireNotNull(patients().getById("a"))
    }
    private suspend fun clearPending() = settings().putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
    private suspend fun assertPendingClear() = assertFalse(settings().getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))

    @Test fun legacyTaskOwnerDoesNotBlockPatientSave() = runBlocking<Unit> {
        seedCurrent()
        clearPending()
        mutations().addPatient(Patient(id = "a", name = "شاي", tasks = listOf(
            PatientTask("task", "فحص", ownerDoctorId = "missing"))), "today")
        assertEquals("missing", patients().getById("a")?.tasks?.single()?.ownerDoctorId)
        assertTrue(db.auditDao().getForPatient("a").isEmpty())
        assertFalse(settings().getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false))
    }
    @Test fun checklistCompletionNeedsNoActorStampOrLegacyPendingMarker() = runBlocking<Unit> {
        seedCurrent()
        val before = addTask()
        clearPending()
        val saved = mutations().updatePatient(before.copy(tasks = listOf(before.tasks.single().copy(done = true))), "today")
        assertTrue(saved.tasks.single().done)
        assertNull(saved.tasks.single().completedByDoctorId)
        assertNull(saved.lastEditedByDoctorId)
        assertTrue(db.auditDao().getForPatient("a").isEmpty())
        assertPendingClear()
    }
    @Test fun completedTaskAttributionCannotBeRewrittenByLaterDraft() = runBlocking<Unit> {
        seedCurrent()
        val before = addTask(done = true)
        val original = before.tasks.single()
        val saved = mutations().updatePatient(before.copy(tasks = listOf(original.copy(
            description = "فحص معدل", completedByDoctorId = "forged", completedByName = "forged",
            completedAtEpochMillis = 1))), "today")
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
        val saved = mutations().updatePatient(before.copy(tasks = listOf(pending)), "today")
        assertEquals(pending.description, saved.tasks.single().description)
        assertFalse(saved.tasks.single().done)
        assertNull(saved.tasks.single().completedByDoctorId)
        assertNull(saved.tasks.single().completedAtEpochMillis)
    }
    @Test fun legacyTaskOwnerNameIsPreservedWhileDescriptionIsTrimmed() = runBlocking<Unit> {
        seedCurrent()
        mutations().addPatient(Patient(id = "a", name = "شاي", tasks = listOf(PatientTask(
            "task", "  فحص  ", ownerDoctorId = "actor", ownerName = "stale label"))), "today")
        val task = requireNotNull(patients().getById("a")).tasks.single()
        assertEquals("stale label", task.ownerName)
        assertEquals("فحص", task.description)
    }
    @Test fun duplicatePatientAdditionCannotOverwriteClinicalData() = runBlocking<Unit> {
        seedCurrent()
        val before = addTask()
        clearPending()
        val count = db.auditDao().getForPatient("a").size
        try {
            mutations().addPatient(before.copy(labs = "replacement"), "today")
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
                mutations().setShiftDoctors("today", ids, actor)
                fail("Invalid roster accepted")
            } catch (_: IllegalArgumentException) { }
            assertEquals(before, shifts().getById("today"))
            assertPendingClear()
        }
    }
    @Test fun lastValidRosterSaveWins() = runBlocking<Unit> {
        seedCurrent()
        db.doctorDao().upsert(DoctorMapper.toEntity(Doctor(id = "other", fullName = "بلوط", firstName = "بلوط", lastName = "شجرة", gender = Gender.MALE)))
        mutations().setShiftDoctors("today", listOf("actor"), actor)
        val first = requireNotNull(shifts().getById("today"))
        mutations().setShiftDoctors("today", listOf("other"), actor)
        val last = requireNotNull(shifts().getById("today"))
        assertEquals(listOf("other"), last.doctorIds)
        assertEquals(first.revision + 1, last.revision)
    }
    @Test fun lastValidSortSavePreservesClinicalContent() = runBlocking<Unit> {
        seedCurrent()
        val patient = addTask()
        shifts().updateSortSpec("today", SortSpecCodec.encode(SortSpec.EMPTY))
        mutations().setShiftSort("today", SortSpecCodec.encode(SortSpec.DEFAULT), actor)
        assertEquals(SortSpecCodec.encode(SortSpec.DEFAULT), shifts().getById("today")?.sortSpecJson)
        assertEquals(patient.labs, patients().getById("a")?.labs)
        assertEquals(patient.tasks, patients().getById("a")?.tasks)
    }
    @Test fun wrongShiftDeleteCannotRecyclePatientOrWriteAudit() = runBlocking<Unit> {
        seedCurrent()
        // Patient belongs to an older shift, but mutation targets today's editable shift.
        patients().upsert(Patient(id = "a", name = "شاي", revision = 1), "shift-1")
        val before = requireNotNull(patients().getById("a"))
        clearPending()
        try {
            mutations().deletePatient(before, "today")
            fail("Cross-shift deletion accepted")
        } catch (_: StalePatientEditException) { }
        assertEquals(before, patients().getById("a"))
        assertTrue(db.auditDao().getForPatient("a").isEmpty())
        assertPendingClear()
    }
    @Test fun crossSectionDuplicateAdditionCannotMoveOrDropStoredPatients() = runBlocking<Unit> {
        seedCurrent()
        val first = Patient(id = "old-a", name = "شاي", revision = 1)
        patients().upsertAll(listOf(first, Patient(id = "old-b", name = "قهوة", revision = 1)), "shift-1")
        val source = requireNotNull(patients().getById(first.id))
        val target = requireNotNull(shifts().getById("today"))
        clearPending()
        try {
            mutations().addPatient(source.copy(labs = "replacement"), "today")
            fail("Cross-section duplicate identity accepted")
        } catch (_: IllegalArgumentException) { }
        assertTrue(patients().getForShift("today").isEmpty())
        assertEquals(2, patients().getForShift("shift-1").size)
        assertEquals(source, patients().getById(first.id))
        assertEquals(target, shifts().getById("today"))
        assertPendingClear()
    }
}
