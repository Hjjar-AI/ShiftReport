package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.data.db.dao.PatientDao
import com.hos.rushdpatients.data.mapper.PatientMapper
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepository @Inject constructor(
    private val dao: PatientDao,
    private val dispatchers: DispatcherProvider
) {

    fun observeForShift(shiftId: String): Flow<List<Patient>> =
        dao.observeForShift(shiftId).map { list -> list.map(PatientMapper::fromEntity) }

    suspend fun getForShift(shiftId: String): List<Patient> = withContext(dispatchers.io) {
        dao.getForShift(shiftId).map(PatientMapper::fromEntity)
    }

    suspend fun getById(id: String): Patient? = withContext(dispatchers.io) {
        dao.getById(id)?.let(PatientMapper::fromEntity)
    }

    suspend fun getShiftId(id: String): String? = withContext(dispatchers.io) {
        dao.getShiftId(id)
    }

    suspend fun countActiveReferencesToDoctor(doctorId: String): Int = withContext(dispatchers.io) {
        dao.countActiveReferencesToDoctor(doctorId)
    }

    suspend fun countForShift(shiftId: String): Int = withContext(dispatchers.io) {
        dao.countForShift(shiftId)
    }

    suspend fun upsert(patient: Patient, shiftId: String) = withContext(dispatchers.io) {
        dao.upsert(PatientMapper.toEntity(patient.copy(updatedAt = Instant.now()), shiftId))
    }

    suspend fun upsertAll(patients: List<Patient>, shiftId: String) = withContext(dispatchers.io) {
        val now = Instant.now()
        dao.upsertAll(patients.map { PatientMapper.toEntity(it.copy(updatedAt = now), shiftId) })
    }

    suspend fun softDelete(id: String) = withContext(dispatchers.io) {
        dao.softDelete(id, Instant.now().toEpochMilli())
    }

    suspend fun softDeleteMissingFromShift(shiftId: String, retainedIds: List<String>) =
        withContext(dispatchers.io) {
            if (retainedIds.isEmpty()) {
                dao.getForShift(shiftId).forEach { dao.softDelete(it.id, Instant.now().toEpochMilli()) }
            } else {
                dao.softDeleteMissingFromShift(shiftId, retainedIds, Instant.now().toEpochMilli())
            }
        }

    suspend fun restore(id: String) = withContext(dispatchers.io) {
        val existing = dao.getById(id) ?: return@withContext
        dao.upsert(
            existing.copy(
                deletedAtEpochMillis = null,
                updatedAtEpochMillis = Instant.now().toEpochMilli()
            )
        )
    }

    suspend fun getSoftDeleted(): List<Patient> = withContext(dispatchers.io) {
        dao.getSoftDeleted().map(PatientMapper::fromEntity)
    }

    suspend fun getSoftDeletedForShift(shiftId: String): List<Patient> =
        withContext(dispatchers.io) {
            dao.getSoftDeletedForShift(shiftId).map(PatientMapper::fromEntity)
        }

    suspend fun getAll(): List<Patient> = withContext(dispatchers.io) {
        dao.getAll().map(PatientMapper::fromEntity)
    }

}
