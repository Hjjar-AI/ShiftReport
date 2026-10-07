package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.data.db.dao.SyncStateDao
import com.hos.rushdpatients.data.db.entity.SyncStateEntity
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStateRepository @Inject constructor(
    private val dao: SyncStateDao,
    private val dispatchers: DispatcherProvider
) {

    suspend fun get(channel: SyncChannel): SyncStateEntity? = withContext(dispatchers.io) {
        dao.get(channel.key)
    }

    fun observe(channel: SyncChannel): Flow<SyncStateEntity?> =
        dao.observe(channel.key)

    suspend fun getAll(): List<SyncStateEntity> = withContext(dispatchers.io) {
        dao.getAll()
    }

    suspend fun update(
        channel: SyncChannel,
        offset: Long,
        messageId: Long?,
        fileId: String?
    ) = withContext(dispatchers.io) {
        val existing = dao.get(channel.key)
        val syncedAt = Instant.now().toEpochMilli()
        if (existing == null) {
            dao.upsert(
                SyncStateEntity(
                    channel = channel.key,
                    lastOffset = offset,
                    lastMessageId = messageId,
                    lastFileId = fileId,
                    lastSyncedAtEpochMillis = syncedAt
                )
            )
        } else {
            dao.update(channel.key, offset, messageId, fileId, syncedAt)
        }
    }

    suspend fun reset(channel: SyncChannel) = withContext(dispatchers.io) {
        dao.delete(channel.key)
    }

    suspend fun resetAll() = withContext(dispatchers.io) {
        dao.deleteAll()
    }

    suspend fun lastOffset(channel: SyncChannel): Long = withContext(dispatchers.io) {
        dao.get(channel.key)?.lastOffset ?: 0L
    }

    suspend fun lastFileId(channel: SyncChannel): String? = withContext(dispatchers.io) {
        dao.get(channel.key)?.lastFileId
    }

    suspend fun lastMessageId(channel: SyncChannel): Long? = withContext(dispatchers.io) {
        dao.get(channel.key)?.lastMessageId
    }

    suspend fun lastSyncedAt(channel: SyncChannel): Instant? = withContext(dispatchers.io) {
        dao.get(channel.key)?.lastSyncedAtEpochMillis?.let(Instant::ofEpochMilli)
    }
}