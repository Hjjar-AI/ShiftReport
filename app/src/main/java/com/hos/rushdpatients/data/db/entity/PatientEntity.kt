package com.hos.rushdpatients.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "patients",
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DoctorEntity::class,
            parentColumns = ["id"],
            childColumns = ["responsibleResidentId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = DoctorEntity::class,
            parentColumns = ["id"],
            childColumns = ["responsibleSpecialistId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["shiftId"]),
        // admittanceNumber is a per-patient admission count, NOT a unique id.
        Index(value = ["shiftId", "admittanceNumber"]),
        Index(value = ["responsibleResidentId"]),
        Index(value = ["responsibleSpecialistId"]),
        Index(value = ["deletedAtEpochMillis"]),
        Index(value = ["updatedAtEpochMillis"])
    ]
)
data class PatientEntity(
    @PrimaryKey val id: String,
    val shiftId: String,
    val admittanceNumber: String,
    val admittanceDateEpochDay: Long?,
    val gender: String,
    val name: String,
    val birthDateEpochDay: Long?,
    val hasCompanion: Boolean,
    val diagnosisType: String,
    val initialDiagnosis: String,
    val treatmentPlan: String,
    val followUp: String,
    val labs: String,
    val responsibleResidentId: String?,
    val responsibleSpecialistId: String?,
    val warningFlagsCsv: String,
    val warningDetailsEncoded: String,
    val isPriority: Boolean,
    val lastEditedByDoctorId: String?,
    val lastEditedByName: String?,
    val sortOrder: Int,
    val revision: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?
)
