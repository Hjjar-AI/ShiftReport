package com.hos.rushdpatients.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.entity.SettingEntity
import com.hos.rushdpatients.data.mapper.DoctorMapper
import com.hos.rushdpatients.data.mapper.PatientMapper
import com.hos.rushdpatients.data.mapper.ShiftMapper
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.IsolatedStorageContext
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabasePersistenceTest {
    private val context = IsolatedStorageContext()
    private var database: AppDatabase? = null
    private fun open(key: ByteArray): AppDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, AppDatabase::class.java,
            context.getDatabasePath("synthetic-encrypted.db").absolutePath)
            .openHelperFactory(SupportOpenHelperFactory(key)).build().also { database = it }
    }
    @After fun cleanup() { database?.close(); context.cleanUp() }
    @Test fun encryptedRowsAndDeviceKeySurviveCloseAndReopen() = runBlocking<Unit> {
        val key = DatabaseKeyProvider(context).getOrCreatePassphrase()
        var db = open(key.copyOf())
        db.settingDao().put(SettingEntity("test", "synthetic clinical text"))
        db.close()
        val restoredKey = DatabaseKeyProvider(context).getOrCreatePassphrase()
        assertArrayEquals(key, restoredKey)
        db = open(restoredKey.copyOf())
        assertEquals("synthetic clinical text", db.settingDao().get("test"))
        db.close()
        val raw = context.getDatabasePath("synthetic-encrypted.db").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.startsWith("SQLite format 3"))
        assertFalse(raw.contains("synthetic clinical text"))
        key.fill(0); restoredKey.fill(0)
    }
    @Test fun wrongKeyCannotReadOrReplaceEncryptedRows() = runBlocking<Unit> {
        val key = DatabaseKeyProvider(context).getOrCreatePassphrase()
        open(key.copyOf()).settingDao().put(SettingEntity("test", "preserved"))
        database?.close()
        try {
            open("incorrect synthetic passphrase".toByteArray()).settingDao().get("test")
            fail("Wrong encryption key accepted")
        } catch (_: Exception) { } finally { database?.close() }
        assertEquals("preserved", open(key.copyOf()).settingDao().get("test"))
        key.fill(0)
    }
    @Test fun missingKeyNeverCreatesReplacementForExistingClinicalStorage() {
        val existing = context.getDatabasePath(com.hos.rushdpatients.config.AppConstants.DATABASE_NAME)
        existing.writeText("synthetic preserved storage")
        try { DatabaseKeyProvider(context).getOrCreatePassphrase(); fail("Replacement key created") }
        catch (_: IllegalStateException) { }
        assertEquals("synthetic preserved storage", existing.readText())
    }

    @Test fun initialSchemaRetainsGroupedLabsTasksAndNonUniqueLabelsOnReopen() = runBlocking<Unit> {
        val key = DatabaseKeyProvider(context).getOrCreatePassphrase()
        try {
            var db = open(key.copyOf())
            assertEquals(1, AppConstants.DATABASE_VERSION)
            assertEquals(AppConstants.DATABASE_VERSION, db.openHelper.writableDatabase.version)
            val date = java.time.LocalDate.of(2026, 1, 1)
            val ward = ShiftMapper.toEntity(Shift(id = "initial-ward", date = date, doctorIds = listOf("cedar")))
            val doctor = DoctorMapper.toEntity(Doctor(id = "cedar", fullName = "أرز شجرة", firstName = "أرز",
                lastName = "شجرة", gender = Gender.MALE, telegramId = 999009))
            val groups = listOf(PatientLabGroup(id = "panel", title = "فحص تجريبي", date = date.toString(), results = "Hb=12\nNa=140"))
            val patient = PatientMapper.toEntity(Patient(id = "tea", name = "شاي", responsibleResidentId = doctor.id,
                labs = PatientLabGroups.text(groups), labGroups = groups,
                tasks = listOf(PatientTask("task", "مراجعة تجريبية"))), ward.id)
            db.shiftDao().upsert(ward); db.shiftDao().selectCurrent(ward.id)
            db.shiftDao().upsert(ward.copy(id = "same-date"))
            db.doctorDao().upsert(doctor)
            db.doctorDao().upsert(doctor.copy(id = "same-name", telegramId = null))
            db.patientDao().upsert(patient)
            db.close()
            db = open(key.copyOf())
            assertEquals(patient, db.patientDao().getById(patient.id))
            assertEquals(ward, db.shiftDao().getById(ward.id))
            assertNotNull(db.shiftDao().getById("same-date"))
            assertNotNull(db.doctorDao().getById("same-name"))
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { key.fill(0) }
    }
}
