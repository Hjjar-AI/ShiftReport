package com.hos.rushdpatients.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations.
 *
 * Version 1 was the initial schema.
 * Version 2 relaxes `admittanceNumber` from a unique per-shift identifier to a
 * per-patient admission count that may repeat inside the same shift.
 */
object Migrations {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop the old unique composite index and recreate it non-unique.
            db.execSQL(
                "DROP INDEX IF EXISTS `index_patients_shiftId_admittanceNumber`"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_patients_shiftId_admittanceNumber` " +
                        "ON `patients` (`shiftId`, `admittanceNumber`)"
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `patients` ADD COLUMN `tasksJson` TEXT NOT NULL DEFAULT '[]'")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Existing patients remain unknown; no inferred assignment history.
            db.execSQL("ALTER TABLE `patients` ADD COLUMN `assignmentChangedOnEpochDay` INTEGER")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // IDs carry identity. Dates and names must not block retaining old rows
            // while replacing a complete snapshot with different authoritative IDs.
            db.execSQL("DROP INDEX IF EXISTS `index_shifts_dateEpochDay`")
            db.execSQL("CREATE INDEX `index_shifts_dateEpochDay` ON `shifts` (`dateEpochDay`)")
            db.execSQL("DROP INDEX IF EXISTS `index_doctors_fullName`")
            db.execSQL("CREATE INDEX `index_doctors_fullName` ON `doctors` (`fullName`)")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `patients` ADD COLUMN `labGroupsJson` TEXT NOT NULL DEFAULT '[]'")
            db.query("SELECT `id`, `labs` FROM `patients`").use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val text = cursor.getString(1)
                    val groups = com.hos.rushdpatients.data.model.PatientLabGroups.fromText(text, "$id-labs")
                    db.execSQL("UPDATE `patients` SET `labGroupsJson` = ? WHERE `id` = ?",
                        arrayOf(com.hos.rushdpatients.data.model.PatientLabGroups.encode(groups), id))
                }
            }
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
}
