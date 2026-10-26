package com.hos.rushdpatients.data.repository

import androidx.room.withTransaction
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.db.dao.PatientDao
import com.hos.rushdpatients.data.mapper.PatientMapper
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.patient.PatientAssignmentChange
import com.hos.rushdpatients.util.ShiftDate
import com.hos.rushdpatients.data.model.withoutTrackingMetadata
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
        dao.countActiveReferencesToDoctor(doctorId)
    }

    suspend fun countForShift(shiftId: String): Int = withContext(dispatchers.io) {
        dao.countForShift(shiftId)
    }

    suspend fun upsert(patient: Patient, shiftId: String) = withContext(dispatchers.io) {
        dao.upsert(PatientMapper.toEntity(patient.withoutTrackingMetadata(), shiftId))
    }

    suspend fun upsertAll(patients: List<Patient>, shiftId: String) = withContext(dispatchers.io) {
        dao.upsertAll(patients.map { PatientMapper.toEntity(it.withoutTrackingMetadata(), shiftId) })
    }

    suspend fun save(
        patient: Patient,
        shiftId: String
    ): Patient = withContext(dispatchers.io) {
        database.withTransaction {
            val current = dao.getById(patient.id)
                ?: throw StalePatientEditException()
            if (current.shiftId != shiftId ||
                current.deletedAtEpochMillis != null
            ) {
                throw StalePatientEditException()
            }
            val updated = PatientAssignmentChange.onSave(
                patient, PatientMapper.fromEntity(current), ShiftDate.current()
            ).withoutTrackingMetadata().copy(
                revision = current.revision + 1,
                updatedAt = Instant.EPOCH,
                lastEditedByDoctorId = null, lastEditedByName = null,
                deletedAt = null
            )
            dao.upsert(PatientMapper.toEntity(updated, shiftId))
            updated
        }
    }

    suspend fun softDelete(
        id: String,
        shiftId: String
    ): Patient = withContext(dispatchers.io) {
        database.withTransaction {
            val current = dao.getById(id) ?: throw StalePatientEditException()
            if (current.shiftId != shiftId ||
                current.deletedAtEpochMillis != null
            ) {
                throw StalePatientEditException()
            }
            val now = Instant.now()
            val deleted = PatientMapper.fromEntity(current).withoutTrackingMetadata().copy(
                revision = current.revision + 1,
                updatedAt = Instant.EPOCH,
                lastEditedByDoctorId = null, lastEditedByName = null,
                deletedAt = now
            )
            dao.upsert(PatientMapper.toEntity(deleted, shiftId))
            deleted
        }
    }

    suspend fun restore(
        id: String,
        shiftId: String
    ): Patient = withContext(dispatchers.io) {
        database.withTransaction {
            val current = dao.getById(id) ?: throw StalePatientEditException()
            if (current.shiftId != shiftId ||
                current.deletedAtEpochMillis == null
            ) {
                throw StalePatientEditException()
            }
            val restored = PatientMapper.fromEntity(current).withoutTrackingMetadata().copy(
                revision = current.revision + 1,
                updatedAt = Instant.EPOCH,
                lastEditedByDoctorId = null, lastEditedByName = null,
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
                updatedAtEpochMillis = 0, lastEditedByDoctorId = null, lastEditedByName = null
            )
        )
    }

    suspend fun getSoftDeleted(): List<Patient> = withContext(dispatchers.io) {
        dao.getSoftDeleted().map(PatientMapper::fromEntity)
    }

    fun observeSoftDeletedForShift(shiftId: String): Flow<List<Patient>> =
        dao.observeSoftDeletedForShift(shiftId)
            .map { rows -> rows.map(PatientMapper::fromEntity) }.flowOn(dispatchers.io)

    suspend fun getSoftDeletedForShift(shiftId: String): List<Patient> =
        withContext(dispatchers.io) {
            dao.getSoftDeletedForShift(shiftId).map(PatientMapper::fromEntity)
        }

    suspend fun getAll(): List<Patient> = withContext(dispatchers.io) {
        dao.getAll().map(PatientMapper::fromEntity)
    }


}

class StalePatientEditException : IllegalStateException(
    "هذا المريض غير متاح في المناوبة الحالية."
)
