package com.hos.rushdpatients.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_log",
    indices = [
        Index(value = ["actorDoctorId"]),
        Index(value = ["patientId"]),
        Index(value = ["action"]),
        Index(value = ["atEpochMillis"])
    ]
)
data class AuditEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actorDoctorId: String?,
    val actorName: String?,
    val action: String,
    val patientId: String?,
    val beforeValue: String?,
    val afterValue: String?,
    val detail: String,
    val atEpochMillis: Long
)
