package com.hos.rushdpatients.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hos.rushdpatients.data.db.entity.PatientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {

    @Query("""
        SELECT * FROM patients
        WHERE shiftId = :shiftId AND deletedAtEpochMillis IS NULL
        ORDER BY sortOrder ASC, name ASC
    """)
    fun observeForShift(shiftId: String): Flow<List<PatientEntity>>

    @Query("""
        SELECT * FROM patients
        WHERE shiftId = :shiftId AND deletedAtEpochMillis IS NULL
        ORDER BY sortOrder ASC, name ASC
    """)
    suspend fun getForShift(shiftId: String): List<PatientEntity>

    @Query("SELECT * FROM patients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PatientEntity?

    @Query("SELECT shiftId FROM patients WHERE id = :id LIMIT 1")
    suspend fun getShiftId(id: String): String?

    @Query("""
        SELECT COUNT(*) FROM patients
        WHERE deletedAtEpochMillis IS NULL
          AND (responsibleResidentId = :doctorId OR responsibleSpecialistId = :doctorId)
    """)
    suspend fun countActiveReferencesToDoctor(doctorId: String): Int

    @Query("SELECT * FROM patients WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PatientEntity?>

    @Query("""
        SELECT COUNT(*) FROM patients
        WHERE shiftId = :shiftId AND deletedAtEpochMillis IS NULL
    """)
    suspend fun countForShift(shiftId: String): Int

    @Query("""
        SELECT * FROM patients
        WHERE deletedAtEpochMillis IS NOT NULL
        ORDER BY deletedAtEpochMillis DESC
    """)
    suspend fun getSoftDeleted(): List<PatientEntity>

    @Query("""
        SELECT * FROM patients
        WHERE shiftId = :shiftId AND deletedAtEpochMillis IS NOT NULL
        ORDER BY deletedAtEpochMillis DESC
    """)
    suspend fun getSoftDeletedForShift(shiftId: String): List<PatientEntity>

    @Query("SELECT * FROM patients")
    suspend fun getAll(): List<PatientEntity>

    @Upsert
    suspend fun upsert(patient: PatientEntity)

    @Upsert
    suspend fun upsertAll(patients: List<PatientEntity>)

    @Query("""
        UPDATE patients
        SET deletedAtEpochMillis = :deletedAtMillis,
            updatedAtEpochMillis = :deletedAtMillis
        WHERE id = :id
    """)
    suspend fun softDelete(id: String, deletedAtMillis: Long)

    @Query("""
        UPDATE patients
        SET deletedAtEpochMillis = :deletedAtMillis,
            updatedAtEpochMillis = :deletedAtMillis
        WHERE shiftId = :shiftId
          AND id NOT IN (:retainedIds)
          AND deletedAtEpochMillis IS NULL
    """)
    suspend fun softDeleteMissingFromShift(
        shiftId: String,
        retainedIds: List<String>,
        deletedAtMillis: Long
    )

}
