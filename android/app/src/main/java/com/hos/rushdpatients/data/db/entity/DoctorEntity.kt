package com.hos.rushdpatients.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctors",
    indices = [
        Index(value = ["telegramId"], unique = true),
        Index(value = ["fullName"], unique = true),
        Index(value = ["deletedAtEpochMillis"]),
        Index(value = ["rank"])
    ]
)
data class DoctorEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val telegramId: Long?,
    val telegramUsername: String?,
    val customTitle: String?,
    val rank: Int,
    val isPermanentAdmin: Boolean,
    val extraOptions: String,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?
)