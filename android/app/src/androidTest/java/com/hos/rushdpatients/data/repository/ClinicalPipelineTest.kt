package com.hos.rushdpatients.data.repository

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.backup.EncryptedBackupManager
import com.hos.rushdpatients.domain.report.ReportBuilder
import com.hos.rushdpatients.domain.report.TextReportBuilder
import com.hos.rushdpatients.pdf.PdfPatientContent
import com.hos.rushdpatients.sync.*
import com.hos.rushdpatients.testutil.SyncFixture
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ClinicalPipelineTest : SyncFixture() {
    private fun ward() = WardMutationRepository(db, patients, doctors, shifts, audit, dispatchers)
    private fun backup() = EncryptedBackupManager(preferences, db, shifts, patients, doctors, settings, dispatchers)
    private suspend fun seed(): Patient {
        val shift = requireNotNull(shifts.getById("shift-1"))
        shifts.upsert(shift.copy(date = ShiftDate.current(), doctorIds = listOf(admin.id)))
        val row = Patient(id = "pipeline-tea", name = "شاي", admittanceNumber = "12",
            admittanceDate = LocalDate.of(2020, 1, 1), birthDate = LocalDate.of(1990, 1, 1),
            initialDiagnosis = "تشخيص, \"تجريبي\"", treatmentPlan = "خطة تجريبية\nسطر ثان",
            followUp = "متابعة تجريبية", labs = "نتيجة تجريبية ☕", responsibleResidentId = admin.id,
            badges = listOf(PatientBadge("تنبيه تجريبي", PatientBadgePriority.HIGH)),
            tasks = listOf(PatientTask("task", "مراجعة نتيجة تجريبية", ownerDoctorId = admin.id)))
        ward().addPatient(row, shift.id)
        return requireNotNull(patients.getById(row.id))
    }
    @Test fun clinicalSaveCsvReportBackupUndoRestoreSequenceKeepsContentAndAllowsLastSaveWins() = runBlocking<Unit> {
        val original = seed()
        val completed = ward().updatePatient(original.copy(tasks = original.tasks.map { it.copy(done = true) }), "shift-1")
        val shift = requireNotNull(shifts.getById("shift-1"))
        val decoded = CsvBundleCodec.decode(CsvBundleCodec.encode(listOf(ShiftCsvSnapshot(shift, listOf(completed))))).single().patients.single()
        assertEquals(completed.labs, decoded.labs)
        assertEquals(completed.badges, decoded.badges)
        assertEquals(completed.tasks, decoded.tasks)
        assertNull(decoded.tasks.single().completedByDoctorId)
        val report = ReportBuilder(patients, doctors, shifts, TextReportBuilder(), db, dispatchers).build(shift.id)
        assertTrue(report.textChunks.joinToString("\n").contains("مراجعة نتيجة تجريبية"))
        assertTrue(PdfPatientContent.buildNotesCell(decoded).contains(decoded.labs))
        val prepared = backup().prepareExport("synthetic-password".toCharArray())
        val file = File(preferences.cacheDir, "pipeline.rpb").apply { writeBytes(prepared.bytes) }
        prepared.bytes.fill(0)
        val beforeDownload = ShiftCsvSnapshot(shift, listOf(completed))
        val changed = ward().updatePatient(completed.copy(labs = "تنزيل تجريبي أحدث"), shift.id)
        undo.record(listOf(beforeDownload), listOf(shift.id))
        undo.undo()
        val undone = requireNotNull(patients.getById(original.id))
        assertTrue(undone.revision > changed.revision)
        assertEquals(completed.labs, undone.labs)
        backup().restore(Uri.fromFile(file), "synthetic-password".toCharArray())
        val restored = requireNotNull(patients.getById(original.id))
        assertTrue(restored.revision > undone.revision)
        assertEquals(completed.tasks, restored.tasks)
        val lastSaved = ward().updatePatient(original.copy(labs = "last save"), shift.id)
        assertEquals("last save", lastSaved.labs)
        val final = ward().updatePatient(restored.copy(labs = "تعديل بعد الاستعادة"), shift.id)
        assertTrue(final.revision > restored.revision)
        assertEquals(completed.tasks.single().completedAtEpochMillis, final.tasks.single().completedAtEpochMillis)
    }
    private suspend fun rollbackOnPatientWriteFailure(operation: String) {
        val original = seed()
        val beforeAudit = db.auditDao().getRecent(100)
        settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_clinical_write BEFORE INSERT ON patients BEGIN SELECT RAISE(ABORT, 'synthetic patient write failure'); END")
        rejected {
            when (operation) {
                "update" -> ward().updatePatient(original.copy(labs = "changed"), "shift-1")
                "delete" -> ward().deletePatient(original, "shift-1")
                "add" -> ward().addPatient(original.copy(id = "other"), "shift-1")
            }
        }
        assertEquals(original, patients.getById(original.id))
        assertNull(patients.getById("other"))
        assertEquals(beforeAudit, db.auditDao().getRecent(100))
        assertFalse(settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))
    }
    @Test fun failedPatientWriteRollsBackPatientEditRevisionAndAudit() = runBlocking<Unit> { rollbackOnPatientWriteFailure("update") }
    @Test fun failedPatientWriteRollsBackDeleteAndAudit() = runBlocking<Unit> { rollbackOnPatientWriteFailure("delete") }
    @Test fun failedPatientWriteRollsBackAdditionAndAudit() = runBlocking<Unit> { rollbackOnPatientWriteFailure("add") }
    @Test fun maximumBackupRevisionRejectsRestoreWithoutOverflowOrPartialWrites() = runBlocking<Unit> {
        val original = seed()
        patients.upsert(original.copy(revision = Long.MAX_VALUE), "shift-1")
        val prepared = backup().prepareExport("synthetic-password".toCharArray())
        val file = File(preferences.cacheDir, "overflow.rpb").apply { writeBytes(prepared.bytes) }
        prepared.bytes.fill(0)
        val before = patients.getById(original.id)
        rejected { backup().restore(Uri.fromFile(file), "synthetic-password".toCharArray()) }
        assertEquals(before, patients.getById(original.id))
    }
}
