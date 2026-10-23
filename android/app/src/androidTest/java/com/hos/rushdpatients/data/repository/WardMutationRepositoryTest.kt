package com.hos.rushdpatients.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

import com.hos.rushdpatients.testutil.DatabaseFixture
import com.hos.rushdpatients.data.mapper.DoctorMapper
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class WardMutationRepositoryTest : DatabaseFixture() {
    private val dispatchers = DefaultDispatcherProvider()
    private val actor = Session("actor", "أرز", null, Role.NON_ADMIN, Instant.EPOCH)
    private fun patients() = PatientRepository(db.patientDao(), db, dispatchers)
    private fun settings() = SettingsRepository(db.settingDao(), dispatchers)
    private fun mutations(): WardMutationRepository {
        val patients = patients()
        val settings = settings()
        return WardMutationRepository(db, patients,
            DoctorRepository(db.doctorDao(), patients, db, settings, dispatchers),
            ShiftRepository(db.shiftDao(), dispatchers), settings, AuditRepository(db.auditDao(), dispatchers), dispatchers)
    }
    private suspend fun seedCurrent() {
        seedShift("today", ShiftDate.current())
        db.doctorDao().upsert(DoctorMapper.toEntity(Doctor(id = "actor", fullName = "أرز", firstName = "أرز",
            lastName = "شجرة", gender = Gender.MALE)))
    }
    @Test fun completedTaskIdentityIsStampedFromActorDuringSave() = runBlocking<Unit> {
        seedCurrent()
        val mutations = mutations()
        mutations.addPatient(Patient(id = "a", name = "شاي", tasks = listOf(PatientTask("task", "فحص"))), "today", actor)
        val draft = requireNotNull(patients().getById("a"))
        val saved = mutations.updatePatient(draft.copy(tasks = listOf(draft.tasks.single().copy(done = true,
            completedByDoctorId = "forged", completedByName = "forged", completedAtEpochMillis = 1))), "today", actor)
        val task = saved.tasks.single()
        assertEquals("actor", task.completedByDoctorId)
        assertEquals("أرز", task.completedByName)
        assertTrue(requireNotNull(task.completedAtEpochMillis) > 1)
        assertEquals("actor", saved.lastEditedByDoctorId)
        assertTrue(settings().getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false))
        assertEquals(2, db.auditDao().getForPatient("a").size)
    }
    @Test fun staleSaveLeavesPendingMarkerAndAuditUnchanged() = runBlocking<Unit> {
        seedCurrent()
        val mutations = mutations()
        mutations.addPatient(Patient(id = "a", name = "شاي"), "today", actor)
        val stale = requireNotNull(patients().getById("a"))
        mutations.updatePatient(stale.copy(labs = "current"), "today", actor)
        settings().putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
        val count = db.auditDao().getForPatient("a").size
        try { mutations.updatePatient(stale.copy(labs = "stale"), "today", actor); fail("Stale mutation accepted") }
        catch (_: StalePatientEditException) { }
        assertEquals("current", patients().getById("a")?.labs)
        assertEquals(count, db.auditDao().getForPatient("a").size)
        assertFalse(settings().getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))
    }
    @Test fun archivedShiftMutationIsRejectedWithoutWriting() = runBlocking<Unit> {
        seedShift("archived", ShiftDate.current().minusDays(1))
        try { mutations().addPatient(Patient(id = "a", name = "شاي"), "archived", actor)
            fail("Archived shift accepted mutation") } catch (_: IllegalArgumentException) { }
        assertNull(patients().getById("a"))
        assertTrue(db.auditDao().getForPatient("a").isEmpty())
    }
}
