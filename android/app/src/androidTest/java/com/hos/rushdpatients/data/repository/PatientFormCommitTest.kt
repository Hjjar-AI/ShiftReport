package com.hos.rushdpatients.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.entity.SettingEntity
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.testutil.DatabaseFixture
import com.hos.rushdpatients.util.DefaultDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real transactions on a fresh schema; never opens an installed project. */
@RunWith(AndroidJUnit4::class)
class PatientFormCommitTest : DatabaseFixture() {
    private val dispatchers = DefaultDispatcherProvider()
    private fun patients() = PatientRepository(db.patientDao(), db, dispatchers)
    private fun mutations(): WardMutationRepository {
        val patients = patients()
        val settings = SettingsRepository(db.settingDao(), dispatchers)
        return WardMutationRepository(db, patients,
            DoctorRepository(db.doctorDao(), patients, db, settings, dispatchers),
            ShiftRepository(db.shiftDao(), dispatchers), AuditRepository(db.auditDao(), dispatchers), dispatchers)
    }
    private suspend fun failDraftDeletion(key: String) {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_form_cleanup BEFORE DELETE ON settings
            WHEN OLD.`key` = '$key' BEGIN SELECT RAISE(ABORT, 'synthetic cleanup failure'); END
        """.trimIndent())
    }
    @Test fun failedEditCleanupRollsBackClinicalWriteAndKeepsRecoverableDraft() = runBlocking<Unit> {
        val patient = Patient(id = "tea", name = "شاي", labs = "before")
        patients().upsert(patient, "shift-1")
        val key = AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + patient.id
        db.settingDao().put(SettingEntity(key, "synthetic retained draft"))
        failDraftDeletion(key)
        try { mutations().updatePatientFromForm(patient.copy(labs = "after"), "shift-1"); fail("Cleanup failure accepted") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(patient, patients().getById(patient.id))
        assertEquals("synthetic retained draft", db.settingDao().get(key))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_form_cleanup")
        val saved = mutations().updatePatientFromForm(patient.copy(labs = "after"), "shift-1")
        assertEquals("after", saved.labs); assertEquals(saved, patients().getById(patient.id))
        assertNull(db.settingDao().get(key))
    }
    @Test fun failedAddCleanupCannotCommitPatientOrRenumberExistingRows() = runBlocking<Unit> {
        val existing = Patient(id = "tea", name = "شاي", sortOrder = 1)
        patients().upsert(existing, "shift-1")
        db.settingDao().put(SettingEntity(AppConstants.SETTING_PATIENT_DRAFT, "synthetic add draft"))
        failDraftDeletion(AppConstants.SETTING_PATIENT_DRAFT)
        try { mutations().addPatientFromForm(Patient(id = "coffee", name = "قهوة"), "shift-1", 1); fail("Cleanup failure accepted") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertNull(patients().getById("coffee")); assertEquals(existing, patients().getById(existing.id))
        assertEquals("synthetic add draft", db.settingDao().get(AppConstants.SETTING_PATIENT_DRAFT))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_form_cleanup")
        mutations().addPatientFromForm(Patient(id = "coffee", name = "قهوة"), "shift-1", 1)
        assertNotNull(patients().getById("coffee")); assertNull(db.settingDao().get(AppConstants.SETTING_PATIENT_DRAFT))
    }
}
