package com.hos.rushdpatients.sync

import com.hos.rushdpatients.domain.doctor.DoctorRegistryAction
import com.hos.rushdpatients.domain.doctor.DoctorRegistryActionRequired

import android.content.Context
import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.Topic
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val sessions: SessionManager,
    private val dispatchers: DispatcherProvider,
    private val remoteState: RemoteSyncStateStore
) {
    private val doctorSyncMutex = Mutex()

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

    /** Review captures portable registry bytes; later local edits stay pending. */
    suspend fun preparePendingPublication(source: SyncState, reviewedDoctors: List<Doctor>? = null): String? = withContext(dispatchers.io) {
        val doctors = reviewedDoctors ?: doctorRepository.getAll()
        val published = doctorRepository.normalizeRemoteRegistry(DoctorsRegistryCodec.decode(downloadDoctorsData(source)).doctors)
        if (registryData(doctors.filterNot { it.isDeleted }) == registryData(published.filterNot { it.isDeleted })) return@withContext null
        requirePublishedAdmin(source)
        validateDoctorRegistry(doctors)
        registryData(doctors.filterNot { it.isDeleted })
    }

    suspend fun uploadReviewedDoctors(csv: String): Result<Unit> = doctorSyncMutex.withLock {
        validateDoctorRegistry(DoctorsRegistryCodec.decode(csv).doctors)
        uploadDoctorsInternal(csv)
    }

    suspend fun uploadCurrentDoctors(): Result<Unit> = doctorSyncMutex.withLock {
        uploadCurrentDoctorsInternal()
    }

    private suspend fun uploadCurrentDoctorsInternal(): Result<Unit> = withContext(dispatchers.io) {
        try {
            val doctors = doctorRepository.getAll()
            validateDoctorRegistry(doctors)
            uploadDoctorsInternal(registryData(doctors))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Compatibility entry point: fetching never publishes local registry changes. */
    suspend fun synchronizeDoctors(): Result<Unit> = refreshDoctorsFromRemote()

    /** Preserve pending registry edits during patient-only replacement. Missing references still fail validation. */
    suspend fun refreshDoctorsForPatientFetch(): Result<Unit> = doctorSyncMutex.withLock {
        if (doctorRepository.hasPendingSharedChanges()) Result.success(Unit)
        else fetchAndApplyLatestDoctorsInternal().map { Unit }
    }

    suspend fun refreshDoctorsFromRemote(): Result<Unit> = doctorSyncMutex.withLock {
        fetchAndApplyLatestDoctorsInternal().map { Unit }
    }

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
            doctorRepository.pendingSharedChangesSummary()?.let { throw DoctorRegistryActionRequired(DoctorRegistryAction.PUBLISH, it) }
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
                    !doctorRepository.hasPendingSharedChanges()) {
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
            Result.success(remote.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requireRegistryMatchesPublished(state: SyncState) = withContext(dispatchers.io) {
        val published = doctorRepository.normalizeRemoteRegistry(
            DoctorsRegistryCodec.decode(downloadDoctorsData(state)).doctors)
        validateDoctorRegistry(published)
        if (registryData(doctorRepository.getAll()) != registryData(published)) {
            throw DoctorRegistryActionRequired(DoctorRegistryAction.FETCH,
                "سجل الأطباء المنشور مختلف عن النسخة على الجهاز. افتح سجل الأطباء واختر «جلب سجل الأطباء»، ثم راجع التقرير.")
        }
    }

    /** Forced patient publication does not publish or require unrelated registry edits. */
    suspend fun requirePublishedAssignments(state: SyncState, shift: Shift, patients: List<Patient>) = withContext(dispatchers.io) {
        val registry = doctorRepository.normalizeRemoteRegistry(
            DoctorsRegistryCodec.decode(downloadDoctorsData(state)).doctors)
        validateDoctorRegistry(registry)
        val published = registry.filterNot { it.isDeleted }.associateBy { it.id }
        val invalidRoster = shift.doctorIds.any { it !in published }
        val invalidPatient = patients.firstOrNull { patient ->
            patient.responsibleResidentId?.let { published[it]?.clinicalRole?.canBeResident() != true } == true ||
                patient.responsibleSpecialistId?.let { published[it]?.clinicalRole?.canBeSupervisor() != true } == true
        }
        if (invalidRoster || invalidPatient != null) {
            val subject = invalidPatient?.let { "المريض ${it.name}" } ?: "أطباء المناوبة"
            throw DoctorRegistryActionRequired(DoctorRegistryAction.PUBLISH,
                "$subject: يوجد طبيب غير منشور أو بتصنيف مختلف. انشر سجل الأطباء أو صحح الإسناد أولاً حتى تستطيع الأجهزة الأخرى قراءة بيانات المرضى. بقية تعديلات سجل الأطباء لا تمنع النشر بعد التأكيد.")
        }
    }

    suspend fun requirePublishedAdmin(state: SyncState, permanentOnly: Boolean = false): Doctor = withContext(dispatchers.io) {
        val actor = adminAuthorizer.requireAdmin()
        require(!permanentOnly || actor.isPermanentAdmin) { "هذه العملية متاحة للمدير الدائم فقط" }
        require(actor.telegramId != null && sessions.current()?.telegramId == actor.telegramId) {
            "تغير حساب المدير المرتبط؛ سجّل الدخول مجدداً"
        }
        val registry = doctorRepository.normalizeRemoteRegistry(
            DoctorsRegistryCodec.decode(downloadDoctorsData(state)).doctors)
        require(registry.any { it.id == actor.id && it.isAdmin && (!permanentOnly || it.isPermanentAdmin) && !it.isDeleted && it.telegramId == actor.telegramId }) {
            "صلاحية المدير لم تعد متاحة في السجل المنشور"
        }
        actor
    }

    suspend fun requirePublishedClinician(state: SyncState): Doctor = withContext(dispatchers.io) {
        val session = sessions.current() ?: error("سجّل الدخول قبل نشر البيانات")
        val actor = doctorRepository.getActiveById(session.doctorId)
            ?: error("الطبيب الحالي لم يعد نشطاً؛ سجّل الدخول مجدداً")
        require(actor.telegramId != null && session.telegramId == actor.telegramId) {
            "تغير حساب الطبيب المرتبط؛ سجّل الدخول مجدداً"
        }
        val registry = doctorRepository.normalizeRemoteRegistry(
            DoctorsRegistryCodec.decode(downloadDoctorsData(state)).doctors)
        require(registry.any { it.id == actor.id && !it.isDeleted && it.telegramId == actor.telegramId }) {
            "الطبيب الحالي غير متاح في السجل المنشور"
        }
        actor
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
