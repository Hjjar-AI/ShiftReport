package com.hos.rushdpatients.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hos.rushdpatients.data.db.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE channel = :channel LIMIT 1")
    suspend fun get(channel: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE channel = :channel LIMIT 1")
    fun observe(channel: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state")
    suspend fun getAll(): List<SyncStateEntity>

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("""
        UPDATE sync_state
        SET lastOffset = :offset,
            lastMessageId = :messageId,
            lastFileId = :fileId,
            lastSyncedAtEpochMillis = :syncedAtMillis
        WHERE channel = :channel
    """)
    suspend fun update(
        channel: String,
        offset: Long,
        messageId: Long?,
        fileId: String?,
        syncedAtMillis: Long
    )

    @Query("DELETE FROM sync_state WHERE channel = :channel")
    suspend fun delete(channel: String)

    @Query("DELETE FROM sync_state")
    suspend fun deleteAll()
}