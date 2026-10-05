package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.dao.DoctorDao
import com.hos.rushdpatients.data.mapper.DoctorMapper
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorRepository @Inject constructor(
    private val dao: DoctorDao,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider
) {

    private val syncAliasPrefix = "syncAlias:"

    fun observeAll(): Flow<List<Doctor>> =
        dao.observeAll().map { list -> list.map(DoctorMapper::fromEntity) }

    suspend fun getAll(): List<Doctor> = withContext(dispatchers.io) {
        dao.getAll().map(DoctorMapper::fromEntity)
    }

    suspend fun getAllIncludingDeleted(): List<Doctor> = withContext(dispatchers.io) {
        dao.getAllIncludingDeleted().map(DoctorMapper::fromEntity)
    }

    suspend fun getById(id: String): Doctor? = withContext(dispatchers.io) {
        dao.getById(id)?.let(DoctorMapper::fromEntity)
    }

    suspend fun getActiveById(id: String): Doctor? = withContext(dispatchers.io) {
        dao.getActiveById(id)?.let(DoctorMapper::fromEntity)
    }

    suspend fun getByTelegramId(telegramId: Long): Doctor? = withContext(dispatchers.io) {
        dao.getByTelegramId(telegramId)?.let(DoctorMapper::fromEntity)
    }

    suspend fun getByFullName(fullName: String): Doctor? = withContext(dispatchers.io) {
        dao.getByFullName(fullName)?.let(DoctorMapper::fromEntity)
    }

    suspend fun getAdmins(): List<Doctor> = withContext(dispatchers.io) {
        dao.getAdmins().map(DoctorMapper::fromEntity)
    }

    suspend fun getNonAdmins(): List<Doctor> = withContext(dispatchers.io) {
        dao.getNonAdmins().map(DoctorMapper::fromEntity)
    }

    suspend fun getMaxRank(): Int = withContext(dispatchers.io) { dao.getMaxRank() }

    suspend fun count(): Int = withContext(dispatchers.io) { dao.count() }

    suspend fun countAll(): Int = withContext(dispatchers.io) { dao.countAll() }

    suspend fun upsert(doctor: Doctor) = withContext(dispatchers.io) {
        markLocalChangesPending()
        dao.upsert(DoctorMapper.toEntity(doctor.copy(updatedAt = Instant.now())))
    }

    suspend fun upsertAll(doctors: List<Doctor>) = withContext(dispatchers.io) {
        markLocalChangesPending()
        val now = Instant.now()
        dao.upsertAll(doctors.map { DoctorMapper.toEntity(it.copy(updatedAt = now)) })
    }

    suspend fun softDelete(id: String) = withContext(dispatchers.io) {
        markLocalChangesPending()
        dao.softDelete(id, Instant.now().toEpochMilli())
    }

    suspend fun replaceAll(doctors: List<Doctor>) = withContext(dispatchers.io) {
        val local = dao.getAllIncludingDeleted().map(DoctorMapper::fromEntity)
        val merged = doctors.map { remote ->
            val existing = local.firstOrNull { candidate ->
                candidate.id == remote.id ||
                        (remote.telegramId != null && candidate.telegramId == remote.telegramId) ||
                        candidate.fullName == remote.fullName
            }
            if (existing == null) remote else remote.copy(
                id = existing.id,
                // PINs and other device-only secrets never travel through Telegram.
                extraOptions = existing.extraOptions + if (remote.id != existing.id) {
                    setOf("$syncAliasPrefix${remote.id}")
                } else emptySet(),
                updatedAt = Instant.now()
            )
        }
        val entities = merged.map(DoctorMapper::toEntity)
        dao.replaceActiveRegistry(
            doctors = entities,
            retainedIds = entities.map { it.id },
            updatedAtMillis = Instant.now().toEpochMilli()
        )
    }

    /**
     * Promote a doctor to admin by assigning the next available rank.
     * Idempotent: if already admin, returns the existing rank.
     */
    suspend fun promoteToAdmin(id: String, customTitle: String?): Doctor? = withContext(dispatchers.io) {
        val doctor = dao.getById(id) ?: return@withContext null
        if (doctor.rank > 0) {
            return@withContext DoctorMapper.fromEntity(doctor)
        }
        val nextRank = dao.getMaxRank() + 1
        val updated = doctor.copy(
            rank = nextRank,
            isPermanentAdmin = false,
            customTitle = customTitle,
            updatedAtEpochMillis = Instant.now().toEpochMilli()
        )
        markLocalChangesPending()
        dao.upsert(updated)
        DoctorMapper.fromEntity(updated)
    }

    suspend fun demoteFromAdmin(id: String): Doctor? = withContext(dispatchers.io) {
        val doctor = dao.getById(id) ?: return@withContext null
        if (doctor.isPermanentAdmin) return@withContext null
        val updated = doctor.copy(
            rank = 0,
            customTitle = null,
            updatedAtEpochMillis = Instant.now().toEpochMilli()
        )
        markLocalChangesPending()
        dao.upsert(updated)
        DoctorMapper.fromEntity(updated)
    }

    private suspend fun markLocalChangesPending() {
        settingsRepository.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, true)
    }
}
