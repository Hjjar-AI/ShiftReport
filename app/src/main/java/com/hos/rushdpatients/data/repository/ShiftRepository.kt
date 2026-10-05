package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.data.db.dao.ShiftDao
import com.hos.rushdpatients.data.mapper.ShiftMapper
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val dao: ShiftDao,
    private val dispatchers: DispatcherProvider
) {

    suspend fun getLatest(): Shift? = withContext(dispatchers.io) {
        dao.getLatest()?.let(ShiftMapper::fromEntity)
    }

    fun observeLatest(): Flow<Shift?> =
        dao.observeLatest().map { it?.let(ShiftMapper::fromEntity) }

    fun observeAll(): Flow<List<Shift>> =
        dao.observeAll().map { list -> list.map(ShiftMapper::fromEntity) }

    suspend fun getById(id: String): Shift? = withContext(dispatchers.io) {
        dao.getById(id)?.let(ShiftMapper::fromEntity)
    }

    suspend fun getByDate(date: LocalDate): Shift? = withContext(dispatchers.io) {
        dao.getByDate(date.toEpochDay())?.let(ShiftMapper::fromEntity)
    }

    fun observeById(id: String): Flow<Shift?> =
        dao.observeById(id).map { it?.let(ShiftMapper::fromEntity) }

    suspend fun getRecent(limit: Int = 30): List<Shift> = withContext(dispatchers.io) {
        dao.getRecent(limit).map(ShiftMapper::fromEntity)
    }

    suspend fun getOrCreateForDate(
        date: LocalDate,
        preferredId: String? = null
    ): Shift = withContext(dispatchers.io) {
        val existing = dao.getByDate(date.toEpochDay())
        if (existing != null) {
            return@withContext ShiftMapper.fromEntity(existing)
        }
        val requestedId = preferredId?.takeIf { it.isNotBlank() }
        val idCollision = requestedId?.let { dao.getById(it) }
        require(idCollision == null) {
            "معرّف الوردية مستخدم لتاريخ مختلف"
        }
        val created = Shift(
            id = requestedId ?: UUID.randomUUID().toString(),
            date = date,
            doctorIds = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        dao.upsert(ShiftMapper.toEntity(created))
        created
    }

    suspend fun upsert(shift: Shift) = withContext(dispatchers.io) {
        dao.upsert(ShiftMapper.toEntity(shift.copy(updatedAt = Instant.now())))
    }

    suspend fun markSent(
        shiftId: String,
        sentAt: Instant,
        reportMessageId: Long?,
        pdfMessageId: Long?
    ) = withContext(dispatchers.io) {
        dao.markSent(shiftId, sentAt.toEpochMilli(), reportMessageId, pdfMessageId)
    }

    suspend fun updateCsvMessageId(shiftId: String, csvMessageId: Long) = withContext(dispatchers.io) {
        dao.updateCsvMessageId(shiftId, csvMessageId, Instant.now().toEpochMilli())
    }

    suspend fun updatePublicationMetadata(shift: Shift, csvMessageId: Long) =
        withContext(dispatchers.io) {
            dao.updatePublicationMetadata(
                shiftId = shift.id,
                csvMessageId = csvMessageId,
                revision = shift.revision,
                snapshotId = shift.snapshotId,
                baseSnapshotId = shift.baseSnapshotId,
                publishedByDeviceId = shift.publishedByDeviceId,
                updatedAtMillis = Instant.now().toEpochMilli()
            )
        }

    suspend fun updateDoctorIds(shiftId: String, doctorIds: List<String>) =
        withContext(dispatchers.io) {
            dao.updateDoctorIds(
                shiftId = shiftId,
                doctorIdsCsv = doctorIds.joinToString(","),
                updatedAtMillis = Instant.now().toEpochMilli()
            )
        }

    suspend fun updateSortSpec(shiftId: String, sortSpecJson: String?) =
        withContext(dispatchers.io) {
            dao.updateSortSpec(shiftId, sortSpecJson, Instant.now().toEpochMilli())
        }

}
