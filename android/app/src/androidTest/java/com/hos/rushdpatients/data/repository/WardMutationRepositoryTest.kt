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
            ShiftRepository(db.shiftDao(), dispatchers), AuditRepository(db.auditDao(), dispatchers), dispatchers)
    }
    private suspend fun seedCurrent() {
        seedShift("today", ShiftDate.current())
        db.doctorDao().upsert(DoctorMapper.toEntity(Doctor(id = "actor", fullName = "أرز", firstName = "أرز",
            lastName = "شجرة", gender = Gender.MALE)))
    }
    @Test fun clinicalCompletionDoesNotCreateAttributionOrAuditStamps() = runBlocking<Unit> {
        seedCurrent()
        val mutations = mutations()
        mutations.addPatient(Patient(id = "a", name = "شاي", tasks = listOf(PatientTask("task", "فحص"))), "today")
        val draft = requireNotNull(patients().getById("a"))
        val saved = mutations.updatePatient(draft.copy(tasks = listOf(draft.tasks.single().copy(done = true,
            completedByDoctorId = "forged", completedByName = "forged", completedAtEpochMillis = 1))), "today")
        val task = saved.tasks.single()
        assertTrue(task.done)
        assertNull(task.completedByDoctorId)
        assertNull(task.completedByName)
        assertNull(task.completedAtEpochMillis)
        assertNull(saved.lastEditedByDoctorId)
        assertNull(saved.lastEditedByName)
        assertTrue(db.auditDao().getForPatient("a").isEmpty())
    }
    @Test fun lastClinicalSaveWinsWithoutAuditOrRevisionPrompt() = runBlocking<Unit> {
        seedCurrent()
        val mutations = mutations()
        mutations.addPatient(Patient(id = "a", name = "شاي"), "today")
        val draft = requireNotNull(patients().getById("a"))
        mutations.updatePatient(draft.copy(labs = "first"), "today")
        val saved = mutations.updatePatient(draft.copy(labs = "last"), "today")
        assertEquals("last", patients().getById("a")?.labs)
        assertEquals(3L, saved.revision)
        assertTrue(db.auditDao().getForPatient("a").isEmpty())
    }
    @Test fun storedSectionDateDoesNotLockEditingButMissingSectionIsRejected() = runBlocking<Unit> {
        seedShift("continuous", ShiftDate.current().minusDays(1))
        mutations().addPatient(Patient(id = "a", name = "شاي"), "continuous")
        assertEquals("continuous", patients().getShiftId("a"))
        try { mutations().addPatient(Patient(id = "missing-section", name = "قهوة"), "missing")
            fail("Missing section accepted") } catch (_: IllegalArgumentException) { }
        assertNull(patients().getById("missing-section"))
        assertTrue(db.auditDao().getForPatient("a").isEmpty())
    }
}
