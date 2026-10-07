package com.hos.rushdpatients.data.repository

import androidx.room.withTransaction
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.db.dao.PatientDao
import com.hos.rushdpatients.data.mapper.PatientMapper
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepository @Inject constructor(
    private val dao: PatientDao,
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider
) {

    fun observeForShift(shiftId: String): Flow<List<Patient>> =
        dao.observeForShift(shiftId).map { list -> list.map(PatientMapper::fromEntity) }.flowOn(dispatchers.io)

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
        dao.countActiveReferencesToDoctor(doctorId) + dao.getAll().count { entity ->
            entity.deletedAtEpochMillis == null &&
                PatientMapper.fromEntity(entity).tasks.any { !it.done && it.ownerDoctorId == doctorId }
        }
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

    suspend fun insertRolloverIfShiftEmpty(patients: List<Patient>, shiftId: String) =
        withContext(dispatchers.io) {
            database.withTransaction {
                require(dao.countForShift(shiftId) == 0) {
                    "لم تعد المناوبة فارغة؛ أعد مراجعة المرضى قبل تطبيق الترحيل"
                }
                if (patients.isNotEmpty()) {
                    val now = Instant.now()
                    dao.upsertAll(
                        patients.map { PatientMapper.toEntity(it.copy(updatedAt = now), shiftId) }
                    )
                }
            }
        }

    suspend fun updateOptimistically(
        patient: Patient,
        shiftId: String,
        expectedRevision: Long
    ): Patient = withContext(dispatchers.io) {
        database.withTransaction {
            val current = dao.getById(patient.id)
                ?: throw StalePatientEditException()
            if (current.shiftId != shiftId || current.revision != expectedRevision ||
                current.deletedAtEpochMillis != null
            ) {
                throw StalePatientEditException()
            }
            val updated = patient.copy(
                revision = expectedRevision + 1,
                updatedAt = Instant.now(),
                deletedAt = null
            )
            dao.upsert(PatientMapper.toEntity(updated, shiftId))
            updated
        }
    }

    suspend fun softDeleteOptimistically(
        id: String,
        shiftId: String,
        expectedRevision: Long
    ): Patient = withContext(dispatchers.io) {
        database.withTransaction {
            val current = dao.getById(id) ?: throw StalePatientEditException()
            if (current.shiftId != shiftId || current.revision != expectedRevision ||
                current.deletedAtEpochMillis != null
            ) {
                throw StalePatientEditException()
            }
            val now = Instant.now()
            val deleted = PatientMapper.fromEntity(current).copy(
                revision = expectedRevision + 1,
                updatedAt = now,
                deletedAt = now
            )
            dao.upsert(PatientMapper.toEntity(deleted, shiftId))
            deleted
        }
    }

    suspend fun restoreOptimistically(
        id: String,
        shiftId: String,
        expectedRevision: Long
    ): Patient = withContext(dispatchers.io) {
        database.withTransaction {
            val current = dao.getById(id) ?: throw StalePatientEditException()
            if (current.shiftId != shiftId || current.revision != expectedRevision ||
                current.deletedAtEpochMillis == null
            ) {
                throw StalePatientEditException()
            }
            val restored = PatientMapper.fromEntity(current).copy(
                revision = expectedRevision + 1,
                updatedAt = Instant.now(),
                deletedAt = null
            )
            dao.upsert(PatientMapper.toEntity(restored, shiftId))
            restored
        }
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

class StalePatientEditException : IllegalStateException(
    "تغيّرت بيانات المريض منذ فتحها. أعد فتح المريض وراجع أحدث نسخة قبل الحفظ."
)
