package com.hos.rushdpatients.sync

import com.hos.rushdpatients.R
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
import com.hos.rushdpatients.util.runCatchingCancellable
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
            runCatchingCancellable {
                require(this@DoctorSyncCoordinator.registryData(DoctorsRegistryCodec.decode(registryData).doctors) ==
                    this@DoctorSyncCoordinator.registryData(doctorRepository.getAll())) {
                    context.getString(R.string.doctors_local_changed_before_publish)
                }
                uploadCurrentDoctorsInternal()
            }
        }
    }

    private fun registryData(doctors: List<Doctor>): String = DoctorsRegistryCodec.encode(doctors.sortedBy { it.id })

    private fun sameDoctorPointer(a: SyncState?, b: SyncState?): Boolean =
        a?.doctorsMessageId == b?.doctorsMessageId && a?.doctorsFileId == b?.doctorsFileId &&
            a?.doctorsUpdatedAt == b?.doctorsUpdatedAt && a?.doctorsData == b?.doctorsData

    private suspend fun uploadDoctorsInternal(
        registryData: String
    ) {
        try {
            val remoteStateBeforeUpload = remoteState.readState()
            val actor = adminAuthorizer.requireAdmin()
            if (remoteStateBeforeUpload != null &&
                (remoteStateBeforeUpload.doctorsFileId != null || remoteStateBeforeUpload.doctorsData != null)) {
                val remoteDoctors = loadPublishedRegistry(remoteStateBeforeUpload)
                val remoteActor = remoteDoctors.firstOrNull { it.id == actor.id && it.isAdmin && !it.isDeleted }
                require(remoteActor != null && remoteActor.telegramId == actor.telegramId) {
                    context.getString(R.string.doctors_published_admin_unavailable)
                }
                val proposed = DoctorsRegistryCodec.decode(registryData).doctors.associateBy { it.id }
                remoteDoctors.filter { it.isAdmin }.forEach { current ->
                    val next = proposed[current.id]
                    if (current.isPermanentAdmin) require(next != null && next.isPermanentAdmin && next.rank > 0) {
                        context.getString(R.string.doctors_permanent_admin_removal_blocked)
                    }
                    if (next == null || next.rank != current.rank || next.isPermanentAdmin != current.isPermanentAdmin) {
                        require(canModify(remoteActor, current)) {
                            context.getString(R.string.doctors_higher_rank_change_blocked)
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
                    caption = context.getString(R.string.doctors_registry_caption),
                    parseMode = null,
                    disableNotification = true,
                    messageThreadId = topics.threadId(Topic.DOCTORS)
                )
            } finally {
                file.delete()
            }
            val uploadedFileId = requireNotNull(message.document?.fileId) {
                context.getString(R.string.doctors_upload_file_id_missing)
            }

            require(adminAuthorizer.requireAdmin().id == actor.id) { context.getString(R.string.doctors_admin_changed_upload) }
            val newState = remoteState.updateState { current ->
                require(sameDoctorPointer(current, remoteStateBeforeUpload ?: SyncState())) {
                    context.getString(R.string.doctors_registry_changed_upload)
                }
                current.copy(
                    doctorsData = null,
                    doctorsFileId = uploadedFileId,
                    doctorsMessageId = message.messageId,
                    doctorsUpdatedAt = System.currentTimeMillis()
                )
            }
            check(newState.doctorsMessageId == message.messageId) {
                context.getString(R.string.doctors_publication_superseded)
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

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatchingCancellable { settingsRepository.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, true) }
            throw e
        }
    }

    /** Review captures portable registry bytes; later local edits stay pending. */
    suspend fun preparePendingPublication(source: SyncState, reviewedDoctors: List<Doctor>? = null): String? = withContext(dispatchers.io) {
        val doctors = reviewedDoctors ?: doctorRepository.getAll()
        val published = loadPublishedRegistry(source)
        if (registryData(doctors.filterNot { it.isDeleted }) == registryData(published.filterNot { it.isDeleted })) return@withContext null
        requirePublishedAdmin(source)
        validateDoctorRegistry(doctors)
        registryData(doctors.filterNot { it.isDeleted })
    }

    suspend fun uploadReviewedDoctors(csv: String): Result<Unit> = doctorSyncMutex.withLock {
        withContext(dispatchers.io) { runCatchingCancellable {
            validateDoctorRegistry(DoctorsRegistryCodec.decode(csv).doctors)
            uploadDoctorsInternal(csv)
        } }
    }

    suspend fun uploadCurrentDoctors(): Result<Unit> = doctorSyncMutex.withLock {
        withContext(dispatchers.io) { runCatchingCancellable { uploadCurrentDoctorsInternal() } }
    }

    private suspend fun uploadCurrentDoctorsInternal() {
        val doctors = doctorRepository.getAll()
        validateDoctorRegistry(doctors)
        uploadDoctorsInternal(registryData(doctors))
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
                ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.doctors_registry_absent)))
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
            doctorRepository.pendingSharedChangesSummary()?.let { throw DoctorRegistryActionRequired(DoctorRegistryAction.PUBLISH, it.message) }
            val local = doctorRepository.getAllIncludingDeleted()
            val state = remoteState.readState()
                ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.doctors_registry_absent)))
            val remote = loadPublishedRegistry(state)
            validateDoctorRegistry(remote)
            check(sameDoctorPointer(remoteState.readState(), state)) {
                context.getString(R.string.doctors_registry_changed_download)
            }
            database.withTransaction {
                check(doctorRepository.getAllIncludingDeleted() == local &&
                    !doctorRepository.hasPendingSharedChanges()) {
                    context.getString(R.string.doctors_local_changed_download)
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
        val published = loadPublishedRegistry(state)
        validateDoctorRegistry(published)
        if (registryData(doctorRepository.getAll()) != registryData(published)) {
            throw DoctorRegistryActionRequired(DoctorRegistryAction.FETCH,
                context.getString(R.string.doctors_registry_fetch_required))
        }
    }

    /** Forced patient publication does not publish or require unrelated registry edits. */
    suspend fun requirePublishedAssignments(state: SyncState, shift: Shift, patients: List<Patient>) = withContext(dispatchers.io) {
        val registry = loadPublishedRegistry(state)
        validateDoctorRegistry(registry)
        val published = registry.filterNot { it.isDeleted }.associateBy { it.id }
        val invalidRoster = shift.doctorIds.any { it !in published }
        val invalidPatient = patients.firstOrNull { patient ->
            patient.responsibleResidentId?.let { published[it]?.clinicalRole?.canBeResident() != true } == true ||
                patient.responsibleSpecialistId?.let { published[it]?.clinicalRole?.canBeSupervisor() != true } == true
        }
        if (invalidRoster || invalidPatient != null) {
            val subject = invalidPatient?.let { context.getString(R.string.sync_patient_subject, it.displayName) } ?: context.getString(R.string.report_roster_label)
            throw DoctorRegistryActionRequired(DoctorRegistryAction.PUBLISH,
                context.getString(R.string.doctors_assignment_publication_required, subject))
        }
    }

    suspend fun requirePublishedAdmin(state: SyncState, permanentOnly: Boolean = false): Doctor = withContext(dispatchers.io) {
        val actor = adminAuthorizer.requireAdmin()
        require(!permanentOnly || actor.isPermanentAdmin) { context.getString(R.string.doctors_permanent_admin_required) }
        require(actor.telegramId != null && sessions.current()?.telegramId == actor.telegramId) {
            context.getString(R.string.doctors_admin_identity_changed)
        }
        val registry = loadPublishedRegistry(state)
        require(registry.any { it.id == actor.id && it.isAdmin && (!permanentOnly || it.isPermanentAdmin) && !it.isDeleted && it.telegramId == actor.telegramId }) {
            context.getString(R.string.doctors_published_admin_unavailable)
        }
        actor
    }

    suspend fun requirePublishedClinician(state: SyncState): Doctor = withContext(dispatchers.io) {
        val session = sessions.current() ?: error(context.getString(R.string.doctors_publish_login_required))
        val actor = doctorRepository.getActiveById(session.doctorId)
            ?: error(context.getString(R.string.doctors_actor_inactive))
        require(actor.telegramId != null && session.telegramId == actor.telegramId) {
            context.getString(R.string.doctors_actor_identity_changed)
        }
        val registry = loadPublishedRegistry(state)
        require(registry.any { it.id == actor.id && !it.isDeleted && it.telegramId == actor.telegramId }) {
            context.getString(R.string.doctors_actor_unpublished)
        }
        actor
    }

    private fun validateDoctorRegistry(doctors: List<Doctor>) {
        require(doctors.isNotEmpty() && doctors.size <= AppConstants.MAX_DOCTORS) {
            context.getString(R.string.doctors_registry_invalid_count)
        }
        require(doctors.any { it.isAdmin && !it.isDeleted }) { context.getString(R.string.doctors_active_admin_required) }
        require(doctors.map { it.id }.distinct().size == doctors.size &&
            doctors.map { it.fullName.lowercase(Locale.ROOT) }.distinct().size == doctors.size) {
            context.getString(R.string.doctors_registry_duplicate_identity)
        }
        val telegramIds = doctors.mapNotNull { it.telegramId }
        require(telegramIds.distinct().size == telegramIds.size && telegramIds.all { it > 0 }) {
            context.getString(R.string.doctors_registry_invalid_telegram_identity)
        }
        require(doctors.all { it.rank >= 0 && (!it.isPermanentAdmin || it.rank > 0) }) {
            context.getString(R.string.doctors_registry_invalid_rank)
        }
        val ranks = doctors.filter { it.rank > 0 }.map { it.rank }
        require(ranks.distinct().size == ranks.size) { context.getString(R.string.doctors_registry_duplicate_rank) }
        DoctorsRegistryCodec.encode(doctors) // Also validates clinical role/group invariants.
    }

    private fun canModify(actor: Doctor, target: Doctor): Boolean =
        actor.isPermanentAdmin || (!target.isPermanentAdmin && actor.rank > target.rank)

    private val registryCacheMutex = Mutex()
    private var cachedRegistryKey: String? = null
    private var cachedRegistry: List<Doctor>? = null

    private suspend fun loadPublishedRegistry(state: SyncState): List<Doctor> {
        val decoded = registryCacheMutex.withLock {
            val key = state.doctorsData?.let { "inline:$it" } ?: "file:${requireNotNull(state.doctorsFileId)}"
            if (cachedRegistryKey == key) return@withLock requireNotNull(cachedRegistry)
            val registry = DoctorsRegistryCodec.decode(downloadDoctorsData(state)).doctors
            cachedRegistryKey = key
            cachedRegistry = registry
            registry
        }
        return doctorRepository.normalizeRemoteRegistry(decoded)
    }

    private suspend fun downloadDoctorsData(state: SyncState): String {
        state.doctorsData?.let { return it }
        val fileId = state.doctorsFileId
            ?: throw IllegalStateException(context.getString(R.string.doctors_registry_absent))
        val path = telegram.getFile(fileId).filePath
            ?: throw IllegalStateException(context.getString(R.string.doctors_registry_path_unavailable))
        val local = File.createTempFile("remote_doctors_", ".txt", context.cacheDir)
        return try {
            telegram.downloadFile(path, local)
            local.readText(Charsets.UTF_8)
        } finally {
            local.delete()
        }
    }

}
