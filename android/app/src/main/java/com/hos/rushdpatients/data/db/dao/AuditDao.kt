package com.hos.rushdpatients.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity

@Dao
interface AuditDao {

    @Insert
    suspend fun insert(entry: AuditEntryEntity): Long

    @Query("SELECT * FROM audit_log WHERE patientId = :patientId ORDER BY atEpochMillis DESC")
    suspend fun getForPatient(patientId: String): List<AuditEntryEntity>

    @Query("SELECT * FROM audit_log ORDER BY atEpochMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AuditEntryEntity>

}
