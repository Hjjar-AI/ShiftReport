package com.hos.rushdpatients.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.hos.rushdpatients.data.db.entity.DoctorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorDao {

    @Query("""
        SELECT * FROM doctors
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY rank DESC, fullName ASC
    """)
    fun observeAll(): Flow<List<DoctorEntity>>

    @Query("""
        SELECT * FROM doctors
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY rank DESC, fullName ASC
    """)
    suspend fun getAll(): List<DoctorEntity>

    @Query("SELECT * FROM doctors ORDER BY rank DESC, fullName ASC")
    suspend fun getAllIncludingDeleted(): List<DoctorEntity>

    @Query("SELECT * FROM doctors WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DoctorEntity?

    @Query("SELECT * FROM doctors WHERE id = :id AND deletedAtEpochMillis IS NULL LIMIT 1")
    suspend fun getActiveById(id: String): DoctorEntity?

    @Query("SELECT * FROM doctors WHERE telegramId = :telegramId AND deletedAtEpochMillis IS NULL LIMIT 1")
    suspend fun getByTelegramId(telegramId: Long): DoctorEntity?

    @Query("SELECT * FROM doctors WHERE fullName = :fullName AND deletedAtEpochMillis IS NULL LIMIT 1")
    suspend fun getByFullName(fullName: String): DoctorEntity?

    @Query("""
        SELECT * FROM doctors
        WHERE deletedAtEpochMillis IS NULL
          AND rank > 0
        ORDER BY rank ASC
    """)
    suspend fun getAdmins(): List<DoctorEntity>

    @Query("""
        SELECT * FROM doctors
        WHERE deletedAtEpochMillis IS NULL
          AND rank = 0
        ORDER BY fullName ASC
    """)
    suspend fun getNonAdmins(): List<DoctorEntity>

    @Query("""
        SELECT COALESCE(MAX(rank), 0) FROM doctors
        WHERE deletedAtEpochMillis IS NULL
    """)
    suspend fun getMaxRank(): Int

    @Query("""
        SELECT COUNT(*) FROM doctors
        WHERE deletedAtEpochMillis IS NULL
    """)
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM doctors")
    suspend fun countAll(): Int

    @Upsert
    suspend fun upsert(doctor: DoctorEntity)

    @Upsert
    suspend fun upsertAll(doctors: List<DoctorEntity>)

    @Query("""
        UPDATE doctors
        SET deletedAtEpochMillis = :deletedAtMillis,
            updatedAtEpochMillis = :deletedAtMillis,
            rank = 0,
            isPermanentAdmin = 0,
            customTitle = NULL,
            telegramId = NULL
        WHERE id NOT IN (:retainedIds)
          AND deletedAtEpochMillis IS NULL
    """)
    suspend fun softDeleteMissing(retainedIds: List<String>, deletedAtMillis: Long)

    @Transaction
    suspend fun replaceActiveRegistry(
        doctors: List<DoctorEntity>,
        retainedIds: List<String>,
        updatedAtMillis: Long
    ) {
        upsertAll(doctors)
        softDeleteMissing(retainedIds, updatedAtMillis)
    }

    @Query("""
        UPDATE doctors
        SET deletedAtEpochMillis = :deletedAtMillis,
            updatedAtEpochMillis = :deletedAtMillis,
            rank = 0,
            isPermanentAdmin = 0,
            customTitle = NULL,
            telegramId = NULL
        WHERE id = :id
    """)
    suspend fun softDelete(id: String, deletedAtMillis: Long)

    @Query("DELETE FROM doctors")
    suspend fun deleteAll()
}
