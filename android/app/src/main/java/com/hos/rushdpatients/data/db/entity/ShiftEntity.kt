package com.hos.rushdpatients.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shifts",
    indices = [
        Index(value = ["dateEpochDay"], unique = true),
        Index(value = ["updatedAtEpochMillis"])
    ]
)
data class ShiftEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val doctorIdsCsv: String,
    val multiDoctorMode: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val sentAtEpochMillis: Long?,
    val reportMessageId: Long?,
    val pdfMessageId: Long?,
    val csvMessageId: Long?,
    val sortSpecJson: String?,
    val revision: Long,
    val snapshotId: String?,
    val baseSnapshotId: String?,
    val publishedByDeviceId: String?
)
