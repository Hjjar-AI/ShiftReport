package com.hos.rushdpatients.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val channel: String,
    val lastOffset: Long,
    val lastMessageId: Long?,
    val lastFileId: String?,
    val lastSyncedAtEpochMillis: Long?
)