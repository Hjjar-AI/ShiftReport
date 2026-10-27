package com.hos.rushdpatients.domain.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.hos.rushdpatients.testutil.DatabaseFixture
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import org.junit.After
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedBackupManagerTest : DatabaseFixture() {
    private val dispatchers = DefaultDispatcherProvider()
    private val files = mutableListOf<File>()
    private fun patients() = PatientRepository(db.patientDao(), db, dispatchers)
    private fun manager(): EncryptedBackupManager {
        val settings = SettingsRepository(db.settingDao(), dispatchers)
        val patients = patients()
        return EncryptedBackupManager(ApplicationProvider.getApplicationContext<Context>(), db,
            ShiftRepository(db.shiftDao(), dispatchers), patients,
            DoctorRepository(db.doctorDao(), patients, db, settings, dispatchers), settings, dispatchers)
    }
    private suspend fun seedCurrent() {
        seedShift("current", ShiftDate.current())
        patients().upsert(Patient(id = "a", name = "شاي", admittanceNumber = "1",
            admittanceDate = ShiftDate.current(), birthDate = java.time.LocalDate.of(1990, 1, 1),
            initialDiagnosis = "تجريبي", treatmentPlan = "خطة تجريبية", followUp = "متابعة تجريبية"), "current")
    }
    private fun file(bytes: ByteArray): Uri {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return File.createTempFile("backup_test_", ".bin", context.cacheDir).also {
            it.writeBytes(bytes); files += it
        }.let(Uri::fromFile)
    }
    @After fun cleanFiles() { files.forEach { it.delete() } }
    @Test fun exportUsesFreshSaltAndNonceAndClearsCallerPassword() = runBlocking<Unit> {
        seedCurrent()
        val password = "synthetic-password".toCharArray()
        val a = manager().prepareExport(password)
        val b = manager().prepareExport("synthetic-password".toCharArray())
        assertEquals(1, a.patientCount)
        assertFalse(a.bytes.contentEquals(b.bytes))
        assertTrue(password.all { it == '\u0000' })
        assertFalse(a.bytes.toString(Charsets.UTF_8).contains("شاي"))
    }
    @Test fun corruptOrWrongPasswordBackupLeavesClinicalRecordsUntouched() = runBlocking<Unit> {
        seedCurrent()
        val exported = manager().prepareExport("synthetic-password".toCharArray())
        val before = patients().getForShift("current")
        val uri = file(exported.bytes)
        try { manager().restore(uri, "wrong-password".toCharArray()); fail("Wrong password accepted") }
        catch (_: IllegalArgumentException) { }
        val corrupt = exported.bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        try { manager().restore(file(corrupt), "synthetic-password".toCharArray()); fail("Tampered backup accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, patients().getForShift("current"))
    }
    @Test fun validBackupRestoresClinicalContentFromReviewedProjectRegistry() = runBlocking<Unit> {
        seedCurrent()
        val exported = manager().prepareExport("synthetic-password".toCharArray())
        val saved = requireNotNull(patients().getById("a"))
        patients().upsert(saved.copy(labs = "changed after export"), "current")
        assertEquals(1, manager().restore(file(exported.bytes), "synthetic-password".toCharArray()))
        val restored = requireNotNull(patients().getById("a"))
        assertEquals(saved.initialDiagnosis, restored.initialDiagnosis)
        assertEquals(saved.followUp, restored.followUp)
        assertEquals(saved.labs, restored.labs)
    }
    @Test fun restorePreservesHistoricalAssignmentDateInsteadOfMarkingToday() = runBlocking<Unit> {
        seedCurrent()
        val day = java.time.LocalDate.of(2026, 1, 1)
        val original = requireNotNull(patients().getById("a")).copy(assignmentChangedOn = day)
        patients().upsert(original, "current")
        val exported = manager().prepareExport("synthetic-password".toCharArray())
        try {
            patients().upsert(original.copy(assignmentChangedOn = day.plusDays(1), labs = "after export"), "current")
            assertEquals(1, manager().restore(file(exported.bytes), "synthetic-password".toCharArray()))
            val restored = requireNotNull(patients().getById("a"))
            assertEquals(day, restored.assignmentChangedOn)
            assertEquals(original.labs, restored.labs)
        } finally { exported.bytes.fill(0) }
    }

}
