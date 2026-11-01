package com.hos.rushdpatients.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            .openHelperFactory(SupportOpenHelperFactory(key)).addMigrations(*Migrations.ALL).build().also { database = it }
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

    @Test fun versionSixUpgradePreservesEncryptedRowsAndRelaxesOnlyLabels() = runBlocking<Unit> {
        val key = DatabaseKeyProvider(context).getOrCreatePassphrase()
        var db = open(key.copyOf())
        val ward = ShiftMapper.toEntity(Shift(id = "ward-before", date = java.time.LocalDate.of(2026, 1, 1),
            doctorIds = listOf("doctor-before")))
        val doctor = DoctorMapper.toEntity(Doctor(id = "doctor-before", fullName = "د. بلوط شجرة",
            firstName = "بلوط", lastName = "شجرة", gender = Gender.MALE,
            clinicalRole = ClinicalRole.RESIDENT, telegramId = 999009))
        val patient = PatientMapper.toEntity(Patient(id = "tea", name = "شاي",
            responsibleResidentId = doctor.id, labs = "synthetic retained clinical text"), ward.id)
        db.shiftDao().upsert(ward); db.shiftDao().selectCurrent(ward.id)
        db.doctorDao().upsert(doctor); db.patientDao().upsert(patient)
        db.settingDao().put(SettingEntity("synthetic-undo", "preserved"))
        // Version 6 has identical tables/columns and these two unique indexes.
        // Recreate that exact predecessor shape inside disposable encrypted storage.
        db.openHelper.writableDatabase.apply {
            execSQL("DROP INDEX index_shifts_dateEpochDay")
            execSQL("CREATE UNIQUE INDEX index_shifts_dateEpochDay ON shifts(dateEpochDay)")
            execSQL("DROP INDEX index_doctors_fullName")
            execSQL("CREATE UNIQUE INDEX index_doctors_fullName ON doctors(fullName)")
            execSQL("UPDATE room_master_table SET identity_hash = 'synthetic-version-six' WHERE id = 42")
            version = 6
        }
        db.close()
        db = open(key.copyOf())
        assertEquals(7, db.openHelper.writableDatabase.version)
        assertEquals(ward, db.shiftDao().getById(ward.id))
        assertEquals(doctor, db.doctorDao().getById(doctor.id))
        assertEquals(patient, db.patientDao().getById(patient.id))
        assertEquals(ward.id, db.shiftDao().getLatest()?.id)
        assertEquals("preserved", db.settingDao().get("synthetic-undo"))
        db.shiftDao().upsert(ward.copy(id = "ward-after"))
        db.doctorDao().upsert(doctor.copy(id = "doctor-after", telegramId = null, deletedAtEpochMillis = 1))
        assertNotNull(db.shiftDao().getById("ward-after"))
        assertNotNull(db.doctorDao().getById("doctor-after"))
        assertEquals(ward.id, db.shiftDao().getByDate(ward.dateEpochDay)?.id)
        db.openHelper.writableDatabase.query("PRAGMA index_list(doctors)").use { cursor ->
            val indexes = mutableMapOf<String, Int>()
            while (cursor.moveToNext()) indexes[cursor.getString(cursor.getColumnIndexOrThrow("name"))] =
                cursor.getInt(cursor.getColumnIndexOrThrow("unique"))
            assertEquals(0, indexes["index_doctors_fullName"])
            assertEquals(1, indexes["index_doctors_telegramId"])
        }
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        db.close()
        assertFalse(context.getDatabasePath("synthetic-encrypted.db").readBytes()
            .toString(Charsets.ISO_8859_1).contains(patient.labs))
        key.fill(0)
    }

}
