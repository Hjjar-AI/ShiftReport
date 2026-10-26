package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.domain.doctor.RegistryChangeSummary
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.mapper.ShiftMapper
import com.hos.rushdpatients.data.model.DoctorOptions
import com.hos.rushdpatients.domain.doctor.DoctorTeams

import androidx.room.withTransaction
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.sync.DoctorsRegistryCodec
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.dao.DoctorDao
import com.hos.rushdpatients.data.mapper.DoctorMapper
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.util.ShiftDate
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
    private val patients: PatientRepository,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider
) {


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

    private suspend fun <T> mutate(block: suspend () -> T): T = withContext(dispatchers.io) {
        database.withTransaction {
            val before = sharedRegistryData(getAll())
            val result = block()
            // PINs, identity aliases and row timestamps are device-only, not registry edits.
            if (sharedRegistryData(getAll()) != before) markLocalChangesPending()
            result
        }
    }

    private fun sharedRegistryData(doctors: List<Doctor>): String =
        DoctorsRegistryCodec.encode(doctors.sortedBy { it.id })

    /** Reconcile old pending flags against the last confirmed portable registry, atomically. */
    suspend fun hasPendingSharedChanges(): Boolean = withContext(dispatchers.io) {
        database.withTransaction {
            val markedPending = settingsRepository.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
            val base = settingsRepository.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY)
                ?: return@withTransaction markedPending
            val baseline = try {
                DoctorsRegistryCodec.decode(base).doctors.takeIf { it.isNotEmpty() }
                    ?.let(::sharedRegistryData)
            } catch (e: IllegalArgumentException) {
                null
            } ?: return@withTransaction markedPending
            val pending = sharedRegistryData(getAll()) != baseline
            if (pending != markedPending) {
                settingsRepository.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, pending)
            }
            pending
        }
    }

    /** User-facing portable changes only; never include PINs, aliases or credentials. */
    suspend fun pendingSharedChangesSummary(): RegistryChangeSummary? = withContext(dispatchers.io) {
        database.withTransaction {
            if (!hasPendingSharedChanges()) return@withTransaction null
            val local = getAll().associateBy { it.id }
            val raw = settingsRepository.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY)
            val base = try { raw?.let { DoctorsRegistryCodec.decode(it).doctors } }
                catch (e: IllegalArgumentException) { null }
            if (base.isNullOrEmpty()) return@withTransaction RegistryChangeSummary("لم يتأكد نشر سجل الأطباء المحلي.")
            val previous = base.associateBy { it.id }
            val lines = buildList {
                (local.keys - previous.keys).forEach { add("إضافة طبيب: ${local.getValue(it).fullName}") }
                (previous.keys - local.keys).forEach { add("حذف طبيب: ${previous.getValue(it).fullName}") }
                (local.keys intersect previous.keys).forEach { id ->
                    val old = previous.getValue(id)
                    val next = local.getValue(id)
                    val fields = buildList {
                        if (old.fullName != next.fullName) add("الاسم")
                        if (old.gender != next.gender) add("الجنس")
                        if (old.telegramId != next.telegramId) add("ارتباط تليجرام")
                        if (old.customTitle.orEmpty() != next.customTitle.orEmpty()) add("اللقب")
                        if (old.clinicalRole != next.clinicalRole) add("الدور السريري")
                        if (old.teamSupervisorId != next.teamSupervisorId) add("الفريق")
                        if (old.supervisorGroupChatId != next.supervisorGroupChatId) add("وجهة التقرير")
                        if (old.rank != next.rank || old.isPermanentAdmin != next.isPermanentAdmin) add("صلاحية التطبيق")
                    }
                    if (fields.isNotEmpty()) add("${next.fullName}: ${fields.joinToString("، ")}")
                }
            }
            RegistryChangeSummary("تعديلات سجل الأطباء غير المنشورة:\n" + lines.joinToString("\n"))
        }
    }

    private fun invalidateUnchangedPin(doctor: Doctor, previous: Doctor?): Doctor =
        if (previous != null && previous.telegramId != doctor.telegramId)
            doctor.copy(extraOptions = doctor.options.withoutInheritedPin(previous.options))
        else doctor

    suspend fun upsert(doctor: Doctor) = mutate {
        val previous = dao.getById(doctor.id)?.let(DoctorMapper::fromEntity)
        val safe = invalidateUnchangedPin(doctor, previous)
        dao.upsert(DoctorMapper.toEntity(safe.copy(updatedAt = Instant.now())))
    }

    suspend fun upsertAll(doctors: List<Doctor>) = mutate {
        val now = Instant.now()
        val previous = getAllIncludingDeleted().associateBy { it.id }
        dao.upsertAll(doctors.map {
            DoctorMapper.toEntity(invalidateUnchangedPin(it, previous[it.id]).copy(updatedAt = now))
        })
    }

    suspend fun softDelete(id: String) = mutate {
        dao.softDelete(id, Instant.now().toEpochMilli())
    }

    /** Map previously recorded wire aliases back to stable local IDs; never match by a changed name. */
    suspend fun normalizeRemoteRegistry(doctors: List<Doctor>): List<Doctor> = withContext(dispatchers.io) {
        val local = getAllIncludingDeleted()
        val normalized = doctors.map { remote ->
            val matches = local.filter {
                it.id == remote.id || remote.id in it.options.syncAliases
            }
            require(matches.size <= 1) { "هوية الطبيب المنشورة ترتبط بأكثر من سجل محلي" }
            remote.copy(id = matches.singleOrNull()?.id ?: remote.id)
        }
        require(normalized.map { it.id }.distinct().size == normalized.size) {
            "معرّفات السجل المنشور تتطابق مع سجل محلي واحد؛ يلزم مراجعة الهوية"
        }
        DoctorTeams.remap(normalized, doctors.zip(normalized).associate { (source, target) -> source.id to target.id })
    }

    /** Called only inside the authorized doctor-deletion transaction; historical rosters stay intact. */
    internal suspend fun removeCurrentShiftAssignment(id: String): Shift? =
        withContext(dispatchers.io) {
            val shifts = database.shiftDao()
            val shift = shifts.getLatest() ?: return@withContext null
            val ids = shift.doctorIdsCsv.split(',').filter(String::isNotBlank)
            if (id !in ids) return@withContext null
            val changed = shifts.updateDoctorIds(
                shiftId = shift.id,
                doctorIdsCsv = ids.filterNot { it == id }.joinToString(","),
                updatedAtMillis = Instant.now().toEpochMilli()
            )
            if (changed != 1) throw StaleShiftEditException()

            ShiftMapper.fromEntity(shift)
        }

    suspend fun requireNoClinicalReferences(id: String) = withContext(dispatchers.io) {
        require(patients.countActiveReferencesToDoctor(id) == 0) {
            "لا يمكن حذف طبيب مسؤول عن مرضى أو مهام معلقة؛ أعد الإسناد أولاً"
        }
        val roster = database.shiftDao().getLatest()
            ?.doctorIdsCsv?.split(',').orEmpty()
        require(id !in roster) { "أزل الطبيب من أطباء المناوبة الحالية قبل حذفه" }
    }

    suspend fun protectedDeletionIds(candidateIds: Set<String>): Set<String> = withContext(dispatchers.io) {
        val roster = database.shiftDao().getLatest()
            ?.doctorIdsCsv?.split(',').orEmpty()
        getAll().filter {
            it.id in candidateIds && (it.isPermanentAdmin || it.id in roster ||
                patients.countActiveReferencesToDoctor(it.id) > 0)
        }.mapTo(mutableSetOf()) { it.id }
    }

    /** Caller must run this together with base/cursor/pending bookkeeping in a Room transaction. */
    suspend fun applyMergedRegistry(doctors: List<Doctor>) = withContext(dispatchers.io) {
        database.withTransaction {
            DoctorTeams.validate(doctors)
            val local = getAllIncludingDeleted()
            val byId = doctors.associateBy { it.id }
            local.filterNot { it.isDeleted }.forEach { current ->
                val next = byId[current.id]
                require(!current.isPermanentAdmin || next != null && next.isPermanentAdmin && next.rank > 0) {
                    "لا يمكن حذف المدير الدائم أو إزالة صلاحياته عبر دمج السجل"
                }
                if (next == null) requireNoClinicalReferences(current.id)
                require(next == null || next.clinicalRole == current.clinicalRole ||
                    patients.countActiveReferencesToDoctor(current.id) == 0) {
                    "أعد إسناد المرضى قبل تغيير التصنيف السريري للطبيب ${current.fullName}"
                }
            }
            val now = Instant.now()
            val merged = doctors.map { incoming ->
                val existing = local.firstOrNull { it.id == incoming.id }
                val candidate = incoming.copy(
                    firstName = existing?.takeIf { it.fullName == incoming.fullName }?.firstName ?: incoming.firstName,
                    lastName = existing?.takeIf { it.fullName == incoming.fullName }?.lastName ?: incoming.lastName,
                    // Identity changes invalidate the previous account's device-local PIN.
                    extraOptions = DoctorTeams.sharedOptions(DoctorOptions.from(existing?.extraOptions.orEmpty()).let { options ->
                        if (existing?.telegramId == incoming.telegramId) existing?.extraOptions.orEmpty() else options.withPinHash(null)
                    }, incoming),
                    telegramUsername = existing?.telegramUsername.takeIf {
                        existing?.telegramId == incoming.telegramId
                    },
                    updatedAt = existing?.updatedAt ?: now,
                    deletedAt = null
                )
                if (candidate == existing) candidate else candidate.copy(updatedAt = now)
            }
            // An unchanged pull must not manufacture a stale editor by changing every row's timestamp.
            val existingById = local.associateBy { it.id }
            val changed = merged.filter { it != existingById[it.id] }
            if (changed.isNotEmpty()) dao.upsertAll(changed.map(DoctorMapper::toEntity))
            dao.softDeleteMissing(merged.map { it.id }, now.toEpochMilli())
        }
    }

    suspend fun replaceAll(doctors: List<Doctor>) = withContext(dispatchers.io) {
        database.withTransaction {
            require(!hasPendingSharedChanges()) {
                "توجد تغييرات محلية في سجل الأطباء؛ استخدم المزامنة ومراجعة التعارضات"
            }
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
                    extraOptions = DoctorTeams.sharedOptions(if (existing.telegramId == remote.telegramId) existing.extraOptions else existing.options.withPinHash(null), remote) + if (remote.id != existing.id) {
                        DoctorOptions.from(emptySet()).withAlias(remote.id)
                    } else emptySet(),
                    updatedAt = Instant.now()
                )
            }
            val remapped = DoctorTeams.remap(merged, doctors.zip(merged).associate { (source, target) -> source.id to target.id })
            DoctorTeams.validate(remapped)
            val entities = remapped.map(DoctorMapper::toEntity)
            dao.replaceActiveRegistry(
                doctors = entities,
                retainedIds = entities.map { it.id },
                updatedAtMillis = Instant.now().toEpochMilli()
            )
            settingsRepository.put(AppConstants.SETTING_DOCTORS_BASE_REGISTRY, DoctorsRegistryCodec.encode(remapped))
            settingsRepository.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
        }
    }

    /**
     * Promote a doctor to admin by assigning the next available rank.
     * Idempotent: if already admin, returns the existing rank.
     */
    suspend fun promoteToAdmin(id: String, customTitle: String?): Doctor? = mutate {
        val doctor = dao.getById(id) ?: return@mutate null
        if (doctor.rank > 0) {
            return@mutate DoctorMapper.fromEntity(doctor)
        }
        val nextRank = dao.getMaxRank() + 1
        val updated = doctor.copy(
            rank = nextRank,
            isPermanentAdmin = false,
            customTitle = customTitle,
            updatedAtEpochMillis = Instant.now().toEpochMilli()
        )
        dao.upsert(updated)
        DoctorMapper.fromEntity(updated)
    }

    suspend fun demoteFromAdmin(id: String): Doctor? = mutate {
        val doctor = dao.getById(id) ?: return@mutate null
        if (doctor.isPermanentAdmin) return@mutate null
        val updated = doctor.copy(
            rank = 0,
            customTitle = null,
            updatedAtEpochMillis = Instant.now().toEpochMilli()
        )
        dao.upsert(updated)
        DoctorMapper.fromEntity(updated)
    }

    private suspend fun markLocalChangesPending() {
        settingsRepository.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, true)
    }
}
