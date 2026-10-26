package com.hos.rushdpatients.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.testutil.ClinicalRepositoryFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDownloadUndoTest : ClinicalRepositoryFixture() {
    private fun undo() = LocalDownloadUndo(db, shifts, patients, settings, audit, sessions, dispatchers, Json)
    private suspend fun record(): Patient {
        val before = Patient(id = "p", name = "شاي", labs = "old", revision = 3,
            admittanceNumber = "1", admittanceDate = java.time.LocalDate.of(2020, 1, 1),
            birthDate = java.time.LocalDate.of(1990, 1, 1), initialDiagnosis = "تشخيص تجريبي",
            treatmentPlan = "خطة تجريبية", followUp = "متابعة تجريبية")
        patients.upsert(before, "shift-1")
        val snapshot = ShiftCsvSnapshot(requireNotNull(shifts.getById("shift-1")), patients.getForShift("shift-1"))
        patients.upsert(before.copy(labs = "downloaded", revision = 4), "shift-1")
        undo().record(listOf(snapshot), listOf("shift-1"))
        return requireNotNull(patients.getById("p"))
    }
    @Test fun restartRetainsUndoAndRestoresWithNewRevision() = runBlocking<Unit> {
        val downloaded = record()
        assertTrue(undo().available.first())
        assertEquals("shift-1", undo().undo()) // New coordinator instance reads persisted undo.
        val restored = requireNotNull(patients.getById("p"))
        assertEquals("old", restored.labs)
        assertTrue(restored.revision > downloaded.revision)
        assertFalse(undo().available.first())
        assertTrue(PatientPublicationStatus(shifts, patients, doctors, settings, dispatchers).hasPending())
        assertTrue(db.auditDao().getRecent(20).isEmpty())
    }
    @Test fun patientEditAfterDownloadBlocksUndoWithoutLosingToken() = runBlocking<Unit> {
        val downloaded = record()
        patients.save(downloaded.copy(labs = "local edit"), "shift-1")
        val live = patients.getById("p")
        settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
        rejected { undo().undo() }
        assertEquals(live, patients.getById("p"))
        assertTrue(undo().available.first())
        assertFalse(settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))
        assertTrue(db.auditDao().getRecent(10).isEmpty())
    }
    @Test fun shiftEditAfterDownloadBlocksUndo() = runBlocking<Unit> {
        record()
        val shift = requireNotNull(shifts.getById("shift-1"))
        shifts.updateDoctorIds(shift.id, listOf(admin.id))
        rejected { undo().undo() }
        assertEquals("downloaded", patients.getById("p")?.labs)
        assertTrue(undo().available.first())
    }
    @Test fun changedSecondShiftPreventsPartialMultiShiftUndo() = runBlocking<Unit> {
        val before = listOf("shift-1", "shift-2").map { id -> ShiftCsvSnapshot(requireNotNull(shifts.getById(id)), emptyList()) }
        patients.upsert(Patient(id = "a", name = "شاي"), "shift-1")
        patients.upsert(Patient(id = "b", name = "قهوة"), "shift-2")
        undo().record(before, listOf("shift-1", "shift-2"))
        patients.upsert(requireNotNull(patients.getById("b")).copy(labs = "local"), "shift-2")
        rejected { undo().undo() }
        assertEquals(1, patients.getForShift("shift-1").size)
        assertEquals("local", patients.getById("b")?.labs)
    }
    @Test fun firstDownloadUndoLeavesEmptyShiftAndPreservesDeliveryEvidence() = runBlocking<Unit> {
        val shift = requireNotNull(shifts.getById("shift-1"))
        shifts.upsert(shift.copy(reportMessageId = 123, pdfMessageId = 124))
        patients.upsert(Patient(id = "a", name = "شاي"), shift.id)
        undo().record(listOf(null), listOf(shift.id))
        undo().undo()
        assertTrue(patients.getForShift(shift.id).isEmpty())
        val restored = requireNotNull(shifts.getById(shift.id))
        assertEquals(123L, restored.reportMessageId)
        assertEquals(124L, restored.pdfMessageId)
    }
}
