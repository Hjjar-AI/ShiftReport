package com.hos.rushdpatients.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hos.rushdpatients.data.db.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {

    @Query("""
        SELECT * FROM shifts
        ORDER BY dateEpochDay DESC
        LIMIT 1
    """)
    suspend fun getLatest(): ShiftEntity?

    @Query("""
        SELECT * FROM shifts
        ORDER BY dateEpochDay DESC
        LIMIT 1
    """)
    fun observeLatest(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE dateEpochDay = :epochDay LIMIT 1")
    suspend fun getByDate(epochDay: Long): ShiftEntity?

    @Query("""
        SELECT * FROM shifts
        ORDER BY dateEpochDay DESC
        LIMIT :limit
    """)
    suspend fun getRecent(limit: Int): List<ShiftEntity>

    @Query("""
        SELECT * FROM shifts
        ORDER BY dateEpochDay DESC
    """)
    fun observeAll(): Flow<List<ShiftEntity>>

    @Upsert
    suspend fun upsert(shift: ShiftEntity)

    @Query("""
        UPDATE shifts
        SET sentAtEpochMillis = :sentAtMillis,
            reportMessageId = :reportMessageId,
            pdfMessageId = :pdfMessageId,
            updatedAtEpochMillis = :sentAtMillis
        WHERE id = :shiftId
    """)
    suspend fun markSent(
        shiftId: String,
        sentAtMillis: Long,
        reportMessageId: Long?,
        pdfMessageId: Long?
    )

    @Query("""
        UPDATE shifts
        SET csvMessageId = :csvMessageId,
            updatedAtEpochMillis = :updatedAtMillis
        WHERE id = :shiftId
    """)
    suspend fun updateCsvMessageId(
        shiftId: String,
        csvMessageId: Long,
        updatedAtMillis: Long
    )

    @Query("""
        UPDATE shifts
        SET csvMessageId = :csvMessageId,
            revision = :revision,
            snapshotId = :snapshotId,
            baseSnapshotId = :baseSnapshotId,
            publishedByDeviceId = :publishedByDeviceId,
            updatedAtEpochMillis = :updatedAtMillis
        WHERE id = :shiftId
    """)
    suspend fun updatePublicationMetadata(
        shiftId: String,
        csvMessageId: Long,
        revision: Long,
        snapshotId: String?,
        baseSnapshotId: String?,
        publishedByDeviceId: String?,
        updatedAtMillis: Long
    )

    @Query("""
        UPDATE shifts
        SET doctorIdsCsv = :doctorIdsCsv,
            updatedAtEpochMillis = :updatedAtMillis
        WHERE id = :shiftId
    """)
    suspend fun updateDoctorIds(
        shiftId: String,
        doctorIdsCsv: String,
        updatedAtMillis: Long
    )

    @Query("""
        UPDATE shifts
        SET sortSpecJson = :sortSpecJson,
            updatedAtEpochMillis = :updatedAtMillis
        WHERE id = :shiftId
    """)
    suspend fun updateSortSpec(
        shiftId: String,
        sortSpecJson: String?,
        updatedAtMillis: Long
    )

    @Query("DELETE FROM shifts")
    suspend fun deleteAll()
}
