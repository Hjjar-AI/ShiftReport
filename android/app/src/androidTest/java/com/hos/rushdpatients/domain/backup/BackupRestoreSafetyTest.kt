package com.hos.rushdpatients.domain.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.data.repository.StalePatientEditException
import com.hos.rushdpatients.testutil.ClinicalRepositoryFixture
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupRestoreSafetyTest : ClinicalRepositoryFixture() {
    private fun manager() = EncryptedBackupManager(preferences, db, shifts, patients, doctors, settings, dispatchers)
    private val password get() = "synthetic-password".toCharArray()
    private suspend fun seed(): Patient {
        seedShift("today", ShiftDate.current())
        patients.upsert(Patient(id = "p", name = "شاي", admittanceNumber = "1", admittanceDate = ShiftDate.current(),
            birthDate = LocalDate.of(1990, 1, 1), initialDiagnosis = "تجريبي", treatmentPlan = "خطة", followUp = "متابعة", revision = 4), "today")
        return requireNotNull(patients.getById("p"))
    }
    private suspend fun export(): Uri = File(preferences.cacheDir, "backup.bin").also {
        it.writeBytes(manager().prepareExport(password).bytes)
    }.let(Uri::fromFile)
    @Test fun restorePreservesContentAndLaterClinicalSaveRemainsLastSaveWins() = runBlocking<Unit> {
        val draft = seed()
        val shift = requireNotNull(shifts.getById("today"))
        val uri = export()
        patients.save(draft.copy(labs = "newer"), "today")
        manager().restore(uri, password)
        val restored = requireNotNull(patients.getById("p"))
        assertTrue(restored.revision > draft.revision + 1)
        assertTrue(requireNotNull(shifts.getById("today")).revision > shift.revision)
        assertEquals("", restored.labs)
        val saved = patients.save(draft.copy(labs = "last save"), "today")
        assertEquals("last save", patients.getById("p")?.labs)
        assertTrue(saved.revision > restored.revision)
        assertTrue(com.hos.rushdpatients.sync.PatientPublicationStatus(shifts, patients, doctors, settings, dispatchers).hasPending())
    }
    @Test fun changedResponsibleRoleRejectsRestoreWithoutAnyWrites() = runBlocking<Unit> {
        val patient = seed()
        patients.upsert(patient.copy(responsibleResidentId = admin.id), "today")
        val uri = export()
        doctors.upsert(admin.copy(clinicalRole = ClinicalRole.SUPERVISOR))
        val before = patients.getForShift("today")
        val shift = shifts.getById("today")
        settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
        rejected { manager().restore(uri, password) }
        assertEquals(before, patients.getForShift("today"))
        assertEquals(shift, shifts.getById("today"))
        assertFalse(settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))
    }
    @Test fun legacyDeletedTaskOwnerDoesNotBlockRestore() = runBlocking<Unit> {
        val patient = seed()
        doctors.upsert(doctor("owner"))
        patients.upsert(patient.copy(tasks = listOf(PatientTask("t", "فحص", ownerDoctorId = "owner"))), "today")
        val uri = export()
        doctors.softDelete("owner")
        manager().restore(uri, password)
        assertEquals("owner", patients.getById("p")?.tasks?.single()?.ownerDoctorId)
    }
    @Test fun crossShiftIdentityRejectsRestoreAndRollsBackPendingMarker() = runBlocking<Unit> {
        val patient = seed()
        val uri = export()
        patients.upsert(patient, "shift-1")
        settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
        rejected { manager().restore(uri, password) }
        assertEquals("shift-1", patients.getShiftId(patient.id))
        assertTrue(patients.getForShift("today").isEmpty())
        assertFalse(settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))
    }
    @Test fun malformedFileAndShortPasswordAlwaysWipeCallerArray() = runBlocking<Unit> {
        val bad = File(preferences.cacheDir, "bad").apply { writeText("invalid") }
        for (pass in listOf("short".toCharArray(), password)) {
            rejected { manager().restore(Uri.fromFile(bad), pass) }
            assertTrue(pass.all { it == '\u0000' })
        }
        val pass = password
        rejected { manager().restore(Uri.fromFile(File(preferences.cacheDir, "missing")), pass) }
        assertTrue(pass.all { it == '\u0000' })
    }
}
