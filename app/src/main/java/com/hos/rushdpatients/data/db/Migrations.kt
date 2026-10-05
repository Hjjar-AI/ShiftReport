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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}