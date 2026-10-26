package com.hos.rushdpatients.sync

import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.doctor.DoctorRegistryAction
import com.hos.rushdpatients.domain.doctor.DoctorRegistryActionRequired
import android.content.Context
import androidx.room.withTransaction
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.config.Topic
import com.hos.rushdpatients.config.Topics
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.patient.PatientValidationResult
import com.hos.rushdpatients.domain.patient.PatientValidator
import com.hos.rushdpatients.domain.report.ReportBuilder
import com.hos.rushdpatients.domain.report.BuiltReport
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.Markdown
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.Logging
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/** Shares SyncService's patient mutex and singleton remote state store. */
internal class PatientPublicationCoordinator(
    private val patientSyncMutex: Mutex,
    private val context: Context,
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val syncStateRepository: SyncStateRepository,
    private val settingsRepository: SettingsRepository,
    private val auditRepository: AuditRepository,
    private val dispatchers: DispatcherProvider,
    private val remoteState: RemoteSyncStateStore,
    private val doctorSync: DoctorSyncCoordinator,
    private val reportBuilder: ReportBuilder,
    private val database: AppDatabase
) {

    suspend fun prepareCsvPublication(shiftId: String, reviewed: BuiltReport? = null): CsvPublicationReview = patientSyncMutex.withLock {
        withContext(dispatchers.io) {
            val source = requireNotNull(remoteState.readState()) { context.getString(R.string.sync_index_unavailable_fetch) }
            val actor = doctorSync.requirePublishedClinician(source)
            val built = reviewed ?: reportBuilder.build(shiftId)
            val doctorsCsv = if (actor.isAdmin && built.registryChangeSummary != null) doctorSync.preparePendingPublication(source, built.registryDoctors) else null
            if (doctorsCsv == null) doctorSync.requirePublishedAssignments(source, built.shift, built.patients)
            val remoteBundle = source.currentCsvSnapshot()?.let { downloadSnapshot(it) }.orEmpty()
            val remote = remoteBundle.firstOrNull { it.shiftDate == built.shift.date }
            val before = remote?.patients.orEmpty().associateBy(Patient::id)
            val after = built.patients.associateBy(Patient::id)
            fun names(ids: Set<String>, rows: Map<String, Patient>) =
                ids.mapNotNull { rows[it]?.displayName }.sorted().joinToString(context.getString(R.string.sync_list_separator)).ifBlank { context.getString(R.string.sync_nothing) }
            val changed = KnownShiftSnapshot.changes(built.patients, remote?.patients.orEmpty()).changed.mapTo(mutableSetOf()) { it.id }
            val text = buildString {
                if (doctorsCsv == null && built.registryChangeSummary != null) {
                    append(context.getString(R.string.patients_doctors_remain_pending))
                }
                if (doctorsCsv != null) {
                    append(context.getString(R.string.patients_review_publish_all))
                    append(built.registryChangeSummary?.summary.orEmpty()).append('\n')
                    append(DoctorsRegistryCodec.decode(doctorsCsv).doctors.joinToString("\n") { it.fullName + " · " + it.clinicalRole.arabicLabel }).append("\n\n")
                }
                append(context.getString(R.string.patients_review_snapshot_notice))
                append(context.getString(R.string.patients_review_additions)).append(names(after.keys - before.keys, after)).append('\n')
                append(context.getString(R.string.patients_review_changes)).append(names(changed, after)).append('\n')
                append(context.getString(R.string.patients_review_removals)).append(names(before.keys - after.keys, before)).append("\n\n")
                append(built.textChunks.joinToString("\n\n") { Markdown.toPlainText(it) })
            }
            val publishedShift = built.shift.copy(
                baseSnapshotId = remote?.snapshotId ?: built.shift.snapshotId,
                snapshotId = UUID.randomUUID().toString(),
                revision = maxOf(built.shift.revision, remote?.shiftRevision ?: 0L) + 1,
                publishedByDeviceId = getOrCreateDeviceId())
            val snapshots = listOf(ShiftCsvSnapshot(publishedShift, built.patients))
            CsvPublicationReview(shiftId, CsvBundleCodec.encode(snapshots), source,
                actor.id, actor.telegramId, publishedShift, built.patients, doctorsCsv, text)
        }
    }

    suspend fun uploadCsv(shiftId: String): Result<CsvUploadResult> = try {
        uploadReviewedCsv(prepareCsvPublication(shiftId))
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }

    suspend fun uploadReviewedCsv(review: CsvPublicationReview): Result<CsvUploadResult> =
        patientSyncMutex.withLock { uploadCsvInternal(review.shiftId, review) }

    private suspend fun requirePublicationReview(review: CsvPublicationReview, state: SyncState?) {
        val actor = doctorSync.requirePublishedClinician(requireNotNull(state))
        require(actor.id == review.actorId && actor.telegramId == review.telegramId) { context.getString(R.string.patients_reviewer_changed) }
        doctorSync.requirePublishedAssignments(state, review.shift, review.patients)
    }

    private suspend fun uploadCsvInternal(
        shiftId: String,
        review: CsvPublicationReview
    ): Result<CsvUploadResult> = withContext(dispatchers.io) {
        try {
            val initialSource = requireNotNull(remoteState.readState()) { context.getString(R.string.sync_index_unavailable) }
            val actor = doctorSync.requirePublishedClinician(initialSource)
            require(actor.id == review.actorId && actor.telegramId == review.telegramId) { context.getString(R.string.patients_actor_changed) }
            val shift = review.shift
            val patients = review.patients
            require(shift.doctorIds.distinct().size >= AppConstants.MIN_SHIFT_DOCTORS) { context.getString(R.string.patients_roster_required) }
            require(patients.none { PatientValidator.validate(it) is PatientValidationResult.Invalid }) { context.getString(R.string.patients_invalid_content) }
            review.doctorsCsv?.let { doctorSync.uploadReviewedDoctors(it).getOrThrow() }
            val publishingDeviceId = requireNotNull(shift.publishedByDeviceId)
            requirePublicationReview(review, remoteState.readState())

            val csv = review.csv

            val file = File.createTempFile("upload_shift_", ".csv", context.cacheDir)
            file.writeText(csv, Charsets.UTF_8)

            val msg = try {
                telegram.sendDocument(
                    chatId = topics.chatId,
                    file = file,
                    caption = context.getString(R.string.patients_snapshot_caption, shift.date),
                    parseMode = null,
                    messageThreadId = topics.threadId(Topic.CSV)
                )
            } finally {
                file.delete()
            }
            val uploadedFileId = requireNotNull(msg.document?.fileId) {
                context.getString(R.string.patients_upload_file_id_missing)
            }

            val uploadedAt = msg.date.takeIf { it > 0L }
                ?.times(1_000L)
                ?: System.currentTimeMillis()
            val uploadedSnapshot = CsvSnapshot(
                fileId = uploadedFileId,
                messageId = msg.messageId,
                date = shift.date.toString(),
                updatedAt = uploadedAt
            )
            requirePublicationReview(review, remoteState.readState())
            val newState = remoteState.updateState { current ->
                current.withPublishedCsv(uploadedSnapshot).copy(
                    publicationJournal = (
                        current.publicationJournal + PublicationJournalEntry(
                            snapshotId = requireNotNull(shift.snapshotId),
                            parentSnapshotId = shift.baseSnapshotId,
                            fileId = uploadedFileId,
                            messageId = msg.messageId,
                            at = uploadedAt,
                            deviceId = publishingDeviceId
                        )
                    ).distinctBy { it.messageId }.sortedByDescending { it.messageId }.take(12)
                )
            }
            val published = newState.currentCsvSnapshot()
            check(published != null && published.messageId == msg.messageId) {
                context.getString(R.string.patients_publication_superseded)
            }
            var localBookkeepingFailed = false
            try {
                database.withTransaction {
                    syncStateRepository.update(
                        channel = SyncChannel.CSV,
                        offset = published.updatedAt,
                        messageId = published.messageId,
                        fileId = published.fileId
                    )
                    val localShift = shiftRepository.getById(shift.id)
                    if (localShift != null && localShift.revision < shift.revision &&
                        localShift.doctorIds == shift.doctorIds && localShift.sortSpecJson == shift.sortSpecJson) {
                        shiftRepository.updatePublicationMetadata(shift, msg.messageId)
                    } else shiftRepository.updateCsvMessageId(shift.id, msg.messageId)
                    auditRepository.record(
                        actorDoctorId = null,
                        actorName = null,
                        action = AppConstants.AUDIT_CSV_UPLOADED,
                        detail = "shift=$shiftId patients=${patients.size} snapshot=${shift.snapshotId} device=$publishingDeviceId"
                    )
                    settingsRepository.pruneShiftSyncCache(shiftRepository.getRecent(12).map { it.id }.toSet() + shift.id)
                    settingsRepository.put(AppConstants.SETTING_PATIENT_PUBLISHED_AT_PREFIX + shift.id, uploadedAt.toString())
                    settingsRepository.put(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + shift.id,
                        CsvCodec.encode(shift, patients))
                }
            } catch (e: CancellationException) { throw e }
            catch (error: Exception) {
                // Remote publication succeeded; keep an explicit bookkeeping diagnostic.
                localBookkeepingFailed = true
                Logging.e("CSV published, but local sync bookkeeping failed", error)
            }

            Result.success(CsvUploadResult(msg.messageId, patients.size, localBookkeepingFailed))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun requireRollbackAssignments(source: SyncState, csv: String) {
        CsvBundleCodec.decode(csv).forEach { parsed ->
            doctorSync.requirePublishedAssignments(source, Shift(
                id = requireNotNull(parsed.shiftId), date = parsed.shiftDate, doctorIds = parsed.doctorIds), parsed.patients)
        }
    }

    suspend fun prepareRollback(): CsvRollbackReview = patientSyncMutex.withLock {
        withContext(dispatchers.io) {
            val source = requireNotNull(remoteState.readState()) { context.getString(R.string.sync_index_unavailable) }
            val actor = doctorSync.requirePublishedAdmin(source)
            val snapshot = requireNotNull(source.previousCsvSnapshot()) { context.getString(R.string.patients_previous_publication_absent) }
            val remote = telegram.getFile(snapshot.fileId)
            val local = File.createTempFile("rollback_review_", ".csv", context.cacheDir)
            val csv = try {
                telegram.downloadFile(requireNotNull(remote.filePath), local)
                local.readText(Charsets.UTF_8)
            } finally { local.delete() }
            val shifts = CsvBundleCodec.decode(csv)
            requireRollbackAssignments(source, csv)
            CsvRollbackReview(snapshot, csv, actor.id, actor.telegramId,
                context.getString(R.string.patients_rollback_review_notice) +
                    shifts.joinToString("\n") { context.getString(R.string.patients_snapshot_inventory, it.shiftDate, it.patients.size) })
        }
    }

    suspend fun publishRollback(review: CsvRollbackReview): Result<Unit> = patientSyncMutex.withLock {
        withContext(dispatchers.io) {
            try {
                val source = requireNotNull(remoteState.readState())
                val actor = doctorSync.requirePublishedAdmin(source)
                requireRollbackAssignments(source, review.csv)
                require(actor.id == review.actorId && actor.telegramId == review.telegramId) { context.getString(R.string.doctors_admin_changed) }
                val file = File.createTempFile("rollback_publish_", ".csv", context.cacheDir)
                val message = try {
                    file.writeText(review.csv, Charsets.UTF_8)
                    telegram.sendDocument(chatId = topics.chatId, file = file, caption = context.getString(R.string.patients_rollback_caption),
                        parseMode = null, messageThreadId = topics.threadId(Topic.CSV))
                } finally { file.delete() }
                val currentSource = requireNotNull(remoteState.readState())
                val currentActor = doctorSync.requirePublishedAdmin(currentSource)
                requireRollbackAssignments(currentSource, review.csv)
                require(currentActor.id == actor.id && currentActor.telegramId == actor.telegramId) { context.getString(R.string.patients_admin_changed_upload) }
                val snapshot = review.snapshot.copy(fileId = requireNotNull(message.document?.fileId),
                    messageId = message.messageId, updatedAt = message.date.takeIf { it > 0 }?.times(1000L) ?: System.currentTimeMillis())
                val parsed = CsvBundleCodec.decode(review.csv).first()
                val deviceId = getOrCreateDeviceId()
                remoteState.updateState { current -> current.withPublishedCsv(snapshot).copy(
                    publicationJournal = (current.publicationJournal + PublicationJournalEntry(
                        snapshotId = parsed.snapshotId ?: "rollback-${message.messageId}", parentSnapshotId = parsed.baseSnapshotId,
                        fileId = snapshot.fileId, messageId = snapshot.messageId, at = snapshot.updatedAt,
                        deviceId = deviceId, forced = true)).distinctBy { it.messageId }.sortedByDescending { it.messageId }.take(12)) }
                syncStateRepository.update(SyncChannel.CSV, snapshot.updatedAt, snapshot.messageId, snapshot.fileId)
                // Baselines now refer to a superseded publication; keep local data explicitly pending.
                settingsRepository.getAll().keys.filter { it.startsWith(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX) }
                    .forEach { settingsRepository.delete(it) }

                auditRepository.record(actor.id, actor.fullName, "csv_previous_republished", detail = "message=${message.messageId}")
                Result.success(Unit)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
        }
    }

    private suspend fun downloadSnapshot(snapshot: CsvSnapshot): List<ParsedShift> {
        val remote = telegram.getFile(snapshot.fileId)
        val path = requireNotNull(remote.filePath) { context.getString(R.string.sync_file_path_unavailable) }
        val local = File.createTempFile("snapshot_shift_", ".csv", context.cacheDir)
        return try {
            telegram.downloadFile(path, local)
            CsvBundleCodec.decode(local.readText(Charsets.UTF_8))
        } finally {
            local.delete()
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        settingsRepository.get(AppConstants.SETTING_DEVICE_ID)?.takeIf(String::isNotBlank)?.let {
            return it
        }
        val created = UUID.randomUUID().toString()
        settingsRepository.put(AppConstants.SETTING_DEVICE_ID, created)
        return created
    }
}
