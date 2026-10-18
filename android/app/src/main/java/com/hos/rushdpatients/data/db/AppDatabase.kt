package com.hos.rushdpatients.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.dao.AuditDao
import com.hos.rushdpatients.data.db.dao.DoctorDao
import com.hos.rushdpatients.data.db.dao.PatientDao
import com.hos.rushdpatients.data.db.dao.SettingDao
import com.hos.rushdpatients.data.db.dao.ShiftDao
import com.hos.rushdpatients.data.db.dao.SyncStateDao
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import com.hos.rushdpatients.data.db.entity.DoctorEntity
import com.hos.rushdpatients.data.db.entity.PatientEntity
import com.hos.rushdpatients.data.db.entity.SettingEntity
import com.hos.rushdpatients.data.db.entity.ShiftEntity
import com.hos.rushdpatients.data.db.entity.SyncStateEntity

@Database(
    entities = [
        PatientEntity::class,
        DoctorEntity::class,
        ShiftEntity::class,
        SettingEntity::class,
        SyncStateEntity::class,
        AuditEntryEntity::class
    ],
    version = AppConstants.DATABASE_VERSION,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun patientDao(): PatientDao
    abstract fun doctorDao(): DoctorDao
    abstract fun shiftDao(): ShiftDao
    abstract fun settingDao(): SettingDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val DATABASE_NAME = AppConstants.DATABASE_NAME

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(
                DatabaseKeyProvider(context).getOrCreatePassphrase()
            )
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(*Migrations.ALL)
                .build()
        }
    }
}
