package com.hos.rushdpatients.data.db

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.entity.SettingEntity
import com.hos.rushdpatients.data.mapper.PatientMapper
import com.hos.rushdpatients.data.maintenance.RecycleBinCleanupWorker
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.testutil.DatabaseFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecycleRetentionTest : DatabaseFixture() {
    private val cutoff = 1_800_000_000_000L
    private val prefix = AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX
    private suspend fun seed(id: String, deleted: Long?) {
        db.patientDao().upsert(PatientMapper.toEntity(Patient(id = id, name = "شاي"), "shift-1")
            .copy(deletedAtEpochMillis = deleted))
        db.settingDao().put(SettingEntity(prefix + id, "synthetic draft $id"))
    }
    private suspend fun sweep() = RecycleBinCleanupWorker.purgeExpired(db, cutoff)
    @Test fun exactCutoffIsInclusiveAndActiveRecentPatientsAndOtherSettingsSurvive() = runBlocking<Unit> {
        seed("before", cutoff - 1); seed("exact", cutoff); seed("after", cutoff + 1); seed("active", null)
        db.settingDao().put(SettingEntity(prefix + "orphan", "orphan"))
        db.settingDao().put(SettingEntity("unrelated", "keep"))
        db.settingDao().put(SettingEntity(AppConstants.SETTING_PATIENT_DRAFT, "add draft"))
        sweep()
        assertEquals(setOf("after", "active"), db.patientDao().getAll().map { it.id }.toSet())
        for (id in listOf("before", "exact", "orphan")) assertNull(db.settingDao().get(prefix + id))
        for (id in listOf("after", "active")) assertNotNull(db.settingDao().get(prefix + id))
        assertEquals("keep", db.settingDao().get("unrelated"))
        assertEquals("add draft", db.settingDao().get(AppConstants.SETTING_PATIENT_DRAFT))
    }
    @Test fun draftCleanupFailureRollsBackPatientPurgeAndCanRetry() = runBlocking<Unit> {
        seed("expired", cutoff - 1)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_cleanup BEFORE DELETE ON settings
            BEGIN SELECT RAISE(ABORT, 'synthetic cleanup failure'); END
        """.trimIndent())
        try { sweep(); fail("Failure ignored") } catch (_: android.database.sqlite.SQLiteException) { }
        assertNotNull(db.patientDao().getById("expired")); assertNotNull(db.settingDao().get(prefix + "expired"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_cleanup")
        sweep()
        assertNull(db.patientDao().getById("expired")); assertNull(db.settingDao().get(prefix + "expired"))
    }
    @Test fun cancellationRollsBackBothRowsAndDrafts() = runBlocking<Unit> {
        seed("expired", cutoff)
        try {
            db.withTransaction<Unit> {
                sweep()
                throw kotlinx.coroutines.CancellationException("synthetic cancellation")
            }
            fail("Cancellation ignored")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertNotNull(db.patientDao().getById("expired")); assertNotNull(db.settingDao().get(prefix + "expired"))
    }
    @Test fun restoreBeforeSweepPreservesPatientAndSweepBeforeRestoreCannotResurrectIt() = runBlocking<Unit> {
        seed("restored", cutoff); seed("purged", cutoff)
        val restored = requireNotNull(db.patientDao().getById("restored")).copy(deletedAtEpochMillis = null)
        db.patientDao().upsert(restored)
        sweep()
        assertEquals(restored, db.patientDao().getById("restored"))
        assertNotNull(db.settingDao().get(prefix + "restored"))
        val repository = com.hos.rushdpatients.data.repository.PatientRepository(db.patientDao(), db,
            com.hos.rushdpatients.util.DefaultDispatcherProvider())
        try { repository.restore("purged", "shift-1"); fail("Purged patient resurrected") }
        catch (_: com.hos.rushdpatients.data.repository.StalePatientEditException) { }
        assertNull(db.patientDao().getById("purged"))
    }
}
