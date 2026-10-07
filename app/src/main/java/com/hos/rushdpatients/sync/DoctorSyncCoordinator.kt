package com.hos.rushdpatients.sync

import android.content.Context
import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.Topic
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.doctor.DoctorRegistryMerge
import com.hos.rushdpatients.domain.doctor.DoctorRegistryConflict
import com.hos.rushdpatients.domain.doctor.DoctorMergeChoice
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private data class PendingDoctorMerge(
    val remoteState: SyncState,
    val baseData: String?,
    val base: List<Doctor>,
    val localSnapshot: List<Doctor>,
    val local: List<Doctor>,
    val remote: List<Doctor>,
    val baseKnown: Boolean,
    val protectedDeletionIds: Set<String>
)

class DoctorRegistryConflictsException : IllegalStateException(
    "توجد تعارضات في سجل الأطباء؛ افتح سجل الأطباء وراجع كل حقل قبل المزامنة"
)

@Singleton
class DoctorSyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val doctorRepository: DoctorRepository,
    private val syncStateRepository: SyncStateRepository,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val auditRepository: AuditRepository,
    private val adminAuthorizer: AdminAuthorizer,
    private val dispatchers: DispatcherProvider,
    private val remoteState: RemoteSyncStateStore
) {
    private val doctorSyncMutex = Mutex()
    private val _doctorConflicts = MutableStateFlow<List<DoctorRegistryConflict>>(emptyList())
    val doctorConflicts: StateFlow<List<DoctorRegistryConflict>> = _doctorConflicts.asStateFlow()
    private var pendingDoctorMerge: PendingDoctorMerge? = null
    private var pendingDoctorChoices: Map<String, DoctorMergeChoice> = emptyMap()

    suspend fun uploadDoctors(registryData: String): Result<Unit> = doctorSyncMutex.withLock {
        withContext(dispatchers.io) {
            try {
                require(this@DoctorSyncCoordinator.registryData(DoctorsRegistryCodec.decode(registryData).doctors) ==
                    this@DoctorSyncCoordinator.registryData(doctorRepository.getAll())) {
                    "تغيّر سجل الأطباء المحلي قبل المزامنة؛ أعد المحاولة بأحدث نسخة"
                }
                uploadCurrentDoctorsInternal()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun registryData(doctors: List<Doctor>): String = DoctorsRegistryCodec.encode(doctors.sortedBy { it.id })

    private fun portableDoctors(doctors: List<Doctor>): List<Doctor> =
        DoctorsRegistryCodec.decode(registryData(doctors)).doctors

    private fun sameDoctorPointer(a: SyncState?, b: SyncState?): Boolean =
        a?.doctorsMessageId == b?.doctorsMessageId && a?.doctorsFileId == b?.doctorsFileId &&
            a?.doctorsUpdatedAt == b?.doctorsUpdatedAt && a?.doctorsData == b?.doctorsData

    private suspend fun uploadDoctorsInternal(
        registryData: String
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val remoteStateBeforeUpload = remoteState.readState()
            val remoteMessageIdBeforeUpload = remoteStateBeforeUpload?.doctorsMessageId
            val known = syncStateRepository.get(SyncChannel.DOCTORS)
            val hasRemoteRegistry = remoteStateBeforeUpload?.let {
                it.doctorsFileId != null || it.doctorsData != null
            } == true
            require(!hasRemoteRegistry || known != null &&
                remoteMessageIdBeforeUpload == known.lastMessageId &&
                remoteStateBeforeUpload?.doctorsFileId == known.lastFileId &&
                remoteStateBeforeUpload?.doctorsUpdatedAt == known.lastOffset) {
                "وصل سجل أطباء أحدث من جهاز آخر؛ اجلبه قبل إعادة تطبيق تعديلاتك"
            }
            val file = File.createTempFile("doctors_registry_", ".csv", context.cacheDir)
            file.writeText(registryData, Charsets.UTF_8)
            val message = try {
                telegram.sendDocument(
                    chatId = topics.chatId,
                    file = file,
                    caption = "سجل الأطباء",
                    parseMode = null,
                    disableNotification = true,
                    messageThreadId = topics.threadId(Topic.DOCTORS)
                )
            } finally {
                file.delete()
            }
            val uploadedFileId = requireNotNull(message.document?.fileId) {
                "لم يعُد تليجرام بمعرّف ملف سجل الأطباء"
            }

            val newState = remoteState.updateState { current ->
                require(sameDoctorPointer(current, remoteStateBeforeUpload ?: SyncState())) {
                    "وصل سجل أطباء أحدث أثناء الرفع؛ اجلبه قبل إعادة تطبيق تعديلاتك"
                }
                current.copy(
                    doctorsData = null,
                    doctorsFileId = uploadedFileId,
                    doctorsMessageId = message.messageId,
                    doctorsUpdatedAt = System.currentTimeMillis()
                )
            }
            check(newState.doctorsMessageId == message.messageId) {
                "وصل نشر أحدث لسجل الأطباء بالتزامن؛ اجلبه قبل إعادة تطبيق تعديلاتك"
            }
            database.withTransaction {
                syncStateRepository.update(
                    channel = SyncChannel.DOCTORS,
                    offset = newState.doctorsUpdatedAt,
                    messageId = message.messageId,
                    fileId = uploadedFileId
                )
                settingsRepository.put(AppConstants.SETTING_DOCTORS_BASE_REGISTRY, registryData)
                // A local edit made during the network upload must remain pending.
                settingsRepository.putBoolean(
                    AppConstants.SETTING_DOCTORS_SYNC_PENDING,
                    this@DoctorSyncCoordinator.registryData(doctorRepository.getAll()) != registryData
                )
                auditRepository.record(
                    actorDoctorId = null, actorName = null,
                    action = AppConstants.AUDIT_DOCTORS_SYNCED,
                    detail = "published:${message.messageId}"
                )
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching {
                settingsRepository.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, true)
            }
            Result.failure(e)
        }
    }

    suspend fun uploadCurrentDoctors(): Result<Unit> = doctorSyncMutex.withLock {
        uploadCurrentDoctorsInternal()
    }

    private suspend fun uploadCurrentDoctorsInternal(): Result<Unit> = withContext(dispatchers.io) {
        try {
            val fetched = fetchAndApplyLatestDoctorsInternal()
            val error = fetched.exceptionOrNull()
            if (error != null && !isMissingDoctorRegistry(error)) return@withContext Result.failure(error)
            val doctors = doctorRepository.getAll()
            if (error == null && !settingsRepository.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)) {
                return@withContext Result.success(Unit)
            }
            validateDoctorRegistry(doctors)
            uploadDoctorsInternal(registryData(doctors))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun synchronizeDoctors(): Result<Unit> = doctorSyncMutex.withLock {
        withContext(dispatchers.io) {
            if (settingsRepository.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)) {
                uploadCurrentDoctorsInternal()
            } else {
                val fetched = fetchAndApplyLatestDoctorsInternal()
                if (isMissingDoctorRegistry(fetched.exceptionOrNull()) && doctorRepository.getAll().isNotEmpty()) {
                    uploadCurrentDoctorsInternal()
                } else fetched.map { Unit }
            }
        }
    }

    /** Pull and merge; pending local changes and unresolved fields are never discarded. */
    suspend fun refreshDoctorsFromRemote(): Result<Unit> = doctorSyncMutex.withLock {
        val fetched = fetchAndApplyLatestDoctorsInternal()
        if (isMissingDoctorRegistry(fetched.exceptionOrNull()) && doctorRepository.getAll().isNotEmpty()) {
            uploadCurrentDoctorsInternal()
        } else fetched.map { Unit }
    }

    private fun isMissingDoctorRegistry(error: Throwable?): Boolean =
        error is IllegalStateException && error.message == "لا يوجد سجل أطباء بعد"

    suspend fun fetchLatestDoctors(): Result<String> = doctorSyncMutex.withLock {
        fetchLatestDoctorsInternal()
    }

    private suspend fun fetchLatestDoctorsInternal(): Result<String> = withContext(dispatchers.io) {
        try {
            val state = remoteState.readState()
                ?: return@withContext Result.failure(IllegalStateException("لا يوجد سجل أطباء بعد"))
            Result.success(downloadDoctorsData(state))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAndApplyLatestDoctors(): Result<Int> = doctorSyncMutex.withLock {
        fetchAndApplyLatestDoctorsInternal()
    }

    private suspend fun fetchAndApplyLatestDoctorsInternal(): Result<Int> = withContext(dispatchers.io) {
        try {
            val localSnapshot = doctorRepository.getAllIncludingDeleted()
            val baseData = settingsRepository.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY)
            val local = portableDoctors(localSnapshot)
            val pending = settingsRepository.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
            val state = remoteState.readState()
                ?: return@withContext Result.failure(IllegalStateException("لا يوجد سجل أطباء بعد"))
            val remote = doctorRepository.normalizeRemoteRegistry(
                DoctorsRegistryCodec.decode(downloadDoctorsData(state)).doctors
            )
            validateDoctorRegistry(remote)
            check(sameDoctorPointer(remoteState.readState(), state)) {
                "تغيّر سجل الأطباء أثناء التنزيل؛ أعد المزامنة للحصول على أحدث نسخة"
            }
            val baseKnown = baseData != null || !pending
            val base = baseData?.let { DoctorsRegistryCodec.decode(it).doctors } ?: local
            val remoteIds = remote.mapTo(mutableSetOf()) { it.id }
            val protectedDeletionIds = doctorRepository.protectedDeletionIds(
                local.mapTo(mutableSetOf()) { it.id } - remoteIds
            )
            val review = PendingDoctorMerge(
                state, baseData, base, localSnapshot, local, remote, baseKnown, protectedDeletionIds
            )
            val merged = DoctorRegistryMerge.merge(
                base, local, remote, baseKnown, protectedDeletionIds = protectedDeletionIds
            )
            if (merged.conflicts.isNotEmpty()) {
                pendingDoctorMerge = review
                pendingDoctorChoices = emptyMap()
                _doctorConflicts.value = merged.conflicts
                return@withContext Result.failure(DoctorRegistryConflictsException())
            }
            applyDoctorMerge(review, merged.doctors)
            pendingDoctorMerge = null
            pendingDoctorChoices = emptyMap()
            _doctorConflicts.value = emptyList()
            Result.success(merged.doctors.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveDoctorConflicts(choices: Map<String, DoctorMergeChoice>): Result<Unit> =
        doctorSyncMutex.withLock {
            withContext(dispatchers.io) {
                try {
                    val review = pendingDoctorMerge ?: error("أعد المزامنة لتحميل التعارضات الحالية")
                    val actor = adminAuthorizer.requireAdmin()
                    val remoteActor = review.remote.firstOrNull { it.id == actor.id && it.isAdmin && !it.isDeleted }
                    require(remoteActor != null) {
                        "صلاحية المدير الحالية غير موجودة في السجل المنشور؛ يلزم مدير مخوّل لمراجعة التعارضات"
                    }
                    require(choices.keys == _doctorConflicts.value.mapTo(mutableSetOf()) { it.key }) {
                        "اختر قيمة لكل تعارض حالي فقط قبل تطبيق الدمج"
                    }
                    if (!sameDoctorPointer(remoteState.readState(), review.remoteState) ||
                        doctorRepository.getAllIncludingDeleted() != review.localSnapshot ||
                        settingsRepository.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY) != review.baseData
                    ) {
                        // Regenerate review; never apply choices to a different local or remote snapshot.
                        fetchAndApplyLatestDoctorsInternal().getOrThrow()
                        error("تغيّر السجل منذ فتح المراجعة؛ راجع أحدث نسخة قبل تطبيق اختياراتك")
                    }
                    val combinedChoices = pendingDoctorChoices + choices
                    val merged = DoctorRegistryMerge.merge(
                        review.base, review.local, review.remote, review.baseKnown, combinedChoices,
                        review.protectedDeletionIds
                    )
                    if (merged.conflicts.isNotEmpty()) {
                        pendingDoctorChoices = combinedChoices
                        _doctorConflicts.value = merged.conflicts
                        throw DoctorRegistryConflictsException()
                    }
                    if (!remoteActor.isPermanentAdmin) {
                        review.remote.filter { it.isAdmin }.forEach { target ->
                            val next = merged.doctors.firstOrNull { it.id == target.id }
                            val permissionChanged = next == null || next.rank != target.rank ||
                                next.isPermanentAdmin != target.isPermanentAdmin
                            require(!permissionChanged || remoteActor.rank > target.rank && !target.isPermanentAdmin) {
                                "لا يمكنك تغيير صلاحية مدير أعلى رتبة أو مدير دائم عبر الدمج"
                            }
                        }
                    }
                    applyDoctorMerge(review, merged.doctors, actor)
                    pendingDoctorMerge = null
                    pendingDoctorChoices = emptyMap()
                    _doctorConflicts.value = emptyList()
                    // Publication is a separate retryable step after the atomic local merge commits.
                    if (settingsRepository.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)) {
                        uploadDoctorsInternal(registryData(doctorRepository.getAll()))
                    } else Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    private suspend fun applyDoctorMerge(
        review: PendingDoctorMerge,
        doctors: List<Doctor>,
        resolvingActor: Doctor? = null
    ) {
        validateDoctorRegistry(doctors)
        database.withTransaction {
            check(doctorRepository.getAllIncludingDeleted() == review.localSnapshot &&
                settingsRepository.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY) == review.baseData) {
                "تغيّر سجل الأطباء المحلي أثناء الدمج؛ أعد المزامنة وراجع أحدث نسخة"
            }
            if (resolvingActor != null) {
                check(adminAuthorizer.requireAdmin().id == resolvingActor.id) { "تغيّرت صلاحية المدير" }
            }
            doctorRepository.applyMergedRegistry(doctors)
            settingsRepository.put(AppConstants.SETTING_DOCTORS_BASE_REGISTRY, registryData(review.remote))
            settingsRepository.putBoolean(
                AppConstants.SETTING_DOCTORS_SYNC_PENDING,
                registryData(doctors) != registryData(review.remote)
            )
            syncStateRepository.update(
                SyncChannel.DOCTORS, review.remoteState.doctorsUpdatedAt,
                review.remoteState.doctorsMessageId, review.remoteState.doctorsFileId
            )
            auditRepository.record(
                resolvingActor?.id, resolvingActor?.fullName, AppConstants.AUDIT_DOCTORS_SYNCED,
                if (resolvingActor == null) "merged:${doctors.size}" else "conflicts_resolved:${doctors.size}",
                beforeValue = registryData(review.local),
                afterValue = registryData(doctors)
            )
        }
    }

    private fun validateDoctorRegistry(doctors: List<Doctor>) {
        require(doctors.isNotEmpty() && doctors.size <= AppConstants.MAX_DOCTORS) {
            "عدد سجلات الأطباء غير صالح"
        }
        require(doctors.any { it.isAdmin && !it.isDeleted }) { "يجب إبقاء مدير نشط في سجل الأطباء" }
        require(doctors.map { it.id }.distinct().size == doctors.size &&
            doctors.map { it.fullName.lowercase(Locale.ROOT) }.distinct().size == doctors.size) {
            "سجل الأطباء يحتوي معرّفات أو أسماء مكررة؛ راجع السجلات المتعارضة"
        }
        val telegramIds = doctors.mapNotNull { it.telegramId }
        require(telegramIds.distinct().size == telegramIds.size && telegramIds.all { it > 0 }) {
            "سجل الأطباء يحتوي هويات تليجرام مكررة أو غير صالحة"
        }
        require(doctors.all { it.rank >= 0 && (!it.isPermanentAdmin || it.rank > 0) }) {
            "رتبة المدير أو الصلاحية الدائمة غير صالحة"
        }
        val ranks = doctors.filter { it.rank > 0 }.map { it.rank }
        require(ranks.distinct().size == ranks.size) { "رتب المديرين مكررة؛ راجع تعارض إسناد الرتبة" }
        DoctorsRegistryCodec.encode(doctors) // Also validates clinical role/group invariants.
    }

    private suspend fun downloadDoctorsData(state: SyncState): String {
        state.doctorsData?.let { return it }
        val fileId = state.doctorsFileId
            ?: throw IllegalStateException("لا يوجد سجل أطباء بعد")
        val path = telegram.getFile(fileId).filePath
            ?: throw IllegalStateException("تعذر الحصول على سجل الأطباء")
        val local = File.createTempFile("remote_doctors_", ".txt", context.cacheDir)
        return try {
            telegram.downloadFile(path, local)
            local.readText(Charsets.UTF_8)
        } finally {
            local.delete()
        }
    }

}
