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

    private fun sameDoctorPointer(a: SyncState?, b: SyncState?): Boolean =
        a?.doctorsMessageId == b?.doctorsMessageId && a?.doctorsFileId == b?.doctorsFileId &&
            a?.doctorsUpdatedAt == b?.doctorsUpdatedAt && a?.doctorsData == b?.doctorsData

    private suspend fun uploadDoctorsInternal(
        registryData: String
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val remoteStateBeforeUpload = remoteState.readState()
            val actor = adminAuthorizer.requireAdmin()
            if (remoteStateBeforeUpload != null &&
                (remoteStateBeforeUpload.doctorsFileId != null || remoteStateBeforeUpload.doctorsData != null)) {
                val remoteDoctors = doctorRepository.normalizeRemoteRegistry(
                    DoctorsRegistryCodec.decode(downloadDoctorsData(remoteStateBeforeUpload)).doctors)
                val remoteActor = remoteDoctors.firstOrNull { it.id == actor.id && it.isAdmin && !it.isDeleted }
                require(remoteActor != null && remoteActor.telegramId == actor.telegramId) {
                    "صلاحية المدير لم تعد متاحة في السجل المنشور"
                }
                val proposed = DoctorsRegistryCodec.decode(registryData).doctors.associateBy { it.id }
                remoteDoctors.filter { it.isAdmin }.forEach { current ->
                    val next = proposed[current.id]
                    if (current.isPermanentAdmin) require(next != null && next.isPermanentAdmin && next.rank > 0) {
                        "لا يمكن إزالة مدير دائم عبر استبدال السجل"
                    }
                    if (next == null || next.rank != current.rank || next.isPermanentAdmin != current.isPermanentAdmin) {
                        require(remoteActor.isPermanentAdmin || !current.isPermanentAdmin && remoteActor.rank > current.rank) {
                            "لا يمكنك تغيير صلاحية مدير أعلى رتبة عبر استبدال السجل"
                        }
                    }
                }
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

            require(adminAuthorizer.requireAdmin().id == actor.id) { "تغيّر المدير أثناء الرفع" }
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
            if (!settingsRepository.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)) {
                val fetched = fetchAndApplyLatestDoctorsInternal()
                if (fetched.isSuccess) return@withContext Result.success(Unit)
                val error = fetched.exceptionOrNull()
                if (!isMissingDoctorRegistry(error)) return@withContext Result.failure(requireNotNull(error))
            }
            val doctors = doctorRepository.getAll()
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

    /** Retrieve the full portable registry; publish pending local changes first. */
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
            require(!settingsRepository.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)) {
                "توجد تعديلات محلية في سجل الأطباء؛ انشر النسخة الكاملة قبل تنزيل سجل بديل"
            }
            val local = doctorRepository.getAllIncludingDeleted()
            val state = remoteState.readState()
                ?: return@withContext Result.failure(IllegalStateException("لا يوجد سجل أطباء بعد"))
            val remote = doctorRepository.normalizeRemoteRegistry(
                DoctorsRegistryCodec.decode(downloadDoctorsData(state)).doctors)
            validateDoctorRegistry(remote)
            check(sameDoctorPointer(remoteState.readState(), state)) {
                "تغيّر سجل الأطباء أثناء التنزيل؛ أعد جلب النسخة الكاملة"
            }
            database.withTransaction {
                check(doctorRepository.getAllIncludingDeleted() == local &&
                    !settingsRepository.getBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)) {
                    "تغيّر سجل الأطباء المحلي أثناء التنزيل؛ لم تُستبدل تعديلاتك"
                }
                // Replace all portable fields while preserving device-only PINs/identity aliases
                // and the existing permanent-admin/clinical-reference safety checks.
                doctorRepository.applyMergedRegistry(remote)
                settingsRepository.put(AppConstants.SETTING_DOCTORS_BASE_REGISTRY, registryData(remote))
                settingsRepository.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
                syncStateRepository.update(SyncChannel.DOCTORS, state.doctorsUpdatedAt,
                    state.doctorsMessageId, state.doctorsFileId)
                auditRepository.record(null, null, AppConstants.AUDIT_DOCTORS_SYNCED,
                    "full_download:${remote.size}")
            }
            _doctorConflicts.value = emptyList()
            Result.success(remote.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveDoctorConflicts(choices: Map<String, DoctorMergeChoice>): Result<Unit> =
        Result.failure(IllegalStateException("الدمج الجزئي متوقف؛ اجلب أو انشر سجل الأطباء الكامل"))

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
