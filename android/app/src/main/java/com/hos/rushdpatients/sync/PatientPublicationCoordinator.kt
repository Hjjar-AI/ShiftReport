package com.hos.rushdpatients.sync

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

    suspend fun prepareCsvPublication(shiftId: String, forceLocal: Boolean = false, reviewed: BuiltReport? = null): CsvPublicationReview = patientSyncMutex.withLock {
        withContext(dispatchers.io) {
            val source = requireNotNull(remoteState.readState()) { "فهرس المشروع غير متاح؛ أعد الجلب" }
            val actor = doctorSync.requirePublishedClinician(source)
            val built = reviewed ?: reportBuilder.build(shiftId)
            val doctorsCsv = if (actor.isAdmin && built.registryChangeSummary != null) doctorSync.preparePendingPublication(source, built.registryDoctors) else null
            if (doctorsCsv == null) doctorSync.requirePublishedAssignments(source, built.shift, built.patients)
            require(ShiftDate.canPublish(built.shift.date)) { "انتهت مهلة نشر هذه المناوبة" }
            val remoteBundle = source.currentCsvSnapshot()?.let { downloadSnapshot(it) }.orEmpty()
            val remote = remoteBundle.firstOrNull { it.shiftDate == built.shift.date }
            val before = remote?.patients.orEmpty().associateBy(Patient::id)
            val after = built.patients.associateBy(Patient::id)
            fun names(ids: Set<String>, rows: Map<String, Patient>) =
                ids.mapNotNull { rows[it]?.name }.sorted().joinToString("، ").ifBlank { "لا يوجد" }
            val changed = (before.keys intersect after.keys).filter {
                !samePatientContent(before[it], after[it])
            }.toSet()
            val text = buildString {
                if (doctorsCsv == null && built.registryChangeSummary != null) {
                    append("تعديلات سجل الأطباء ستبقى معلقة؛ نشرها يتطلب مديراً. ستُنشر نسخة المرضى المراجعة فقط.\n")
                }
                if (doctorsCsv != null) {
                    append("نشر الكل — سجل الأطباء أولاً ثم المرضى\n")
                    append(built.registryChangeSummary?.substringBefore("\nافتح").orEmpty()).append('\n')
                    append(DoctorsRegistryCodec.decode(doctorsCsv).doctors.joinToString("\n") { it.fullName + " · " + it.clinicalRole.arabicLabel }).append("\n\n")
                }
                append("ستُنشر النسخة المحلية الكاملة للمناوبة، بما فيها الحذف والترتيب وأطباء المناوبة. آخر نشر ناجح هو المعتمد.\n")
                append("إضافات: ").append(names(after.keys - before.keys, after)).append('\n')
                append("تعديلات: ").append(names(changed, after)).append('\n')
                append("حذف من النسخة المنشورة: ").append(names(before.keys - after.keys, before)).append("\n\n")
                append(built.textChunks.joinToString("\n\n") { Markdown.toPlainText(it) })
            }
            val publishedShift = built.shift.copy(
                baseSnapshotId = remote?.snapshotId ?: built.shift.snapshotId,
                snapshotId = UUID.randomUUID().toString(),
                revision = maxOf(built.shift.revision, remote?.shiftRevision ?: 0L) + 1,
                publishedByDeviceId = getOrCreateDeviceId())
            // Retain already published history, never quietly upload local edits to other shifts.
            val history = remoteBundle.filter { it.shiftDate != built.shift.date }
                .take(AppConstants.CSV_BUNDLE_SHIFT_COUNT - 1).map { parsed ->
                    ShiftCsvSnapshot(com.hos.rushdpatients.data.model.Shift(
                        id = requireNotNull(parsed.shiftId), date = parsed.shiftDate, doctorIds = parsed.doctorIds,
                        sortSpecJson = parsed.sortSpecJson, revision = parsed.shiftRevision, snapshotId = parsed.snapshotId,
                        baseSnapshotId = parsed.baseSnapshotId, publishedByDeviceId = parsed.deviceId), parsed.patients)
                }
            val snapshots = listOf(ShiftCsvSnapshot(publishedShift, built.patients)) + history
            CsvPublicationReview(shiftId, CsvBundleCodec.encode(snapshots), source,
                actor.id, actor.telegramId, publishedShift, built.patients, doctorsCsv, text)
        }
    }

    suspend fun uploadCsv(shiftId: String): Result<CsvUploadResult> = try {
        uploadReviewedCsv(prepareCsvPublication(shiftId))
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }

    suspend fun uploadReviewedCsv(review: CsvPublicationReview, forceLocal: Boolean = false): Result<CsvUploadResult> =
        patientSyncMutex.withLock { uploadCsvInternal(review.shiftId, review, forceLocal) }

    private suspend fun requirePublicationReview(review: CsvPublicationReview, state: SyncState?) {
        val actor = doctorSync.requirePublishedClinician(requireNotNull(state))
        require(actor.id == review.actorId && actor.telegramId == review.telegramId) { "تغير المستخدم؛ سجّل الدخول بحساب المراجع" }
        doctorSync.requirePublishedAssignments(state, review.shift, review.patients)
        require(ShiftDate.canPublish(review.shift.date)) { "انتهت مهلة نشر هذه المناوبة" }
    }

    private suspend fun uploadCsvInternal(
        shiftId: String,
        review: CsvPublicationReview,
        forceLocal: Boolean = false
    ): Result<CsvUploadResult> = withContext(dispatchers.io) {
        try {
            val initialSource = requireNotNull(remoteState.readState()) { "فهرس المشروع غير متاح" }
            val actor = doctorSync.requirePublishedClinician(initialSource)
            require(actor.id == review.actorId && actor.telegramId == review.telegramId) { "تغير المستخدم" }
            val shift = review.shift
            val patients = review.patients
            require(ShiftDate.canPublish(shift.date)) { "انتهت مهلة نشر هذه المناوبة" }
            require(shift.doctorIds.distinct().size >= AppConstants.MIN_SHIFT_DOCTORS) { "حدد أطباء المناوبة قبل الرفع" }
            require(patients.none { PatientValidator.validate(it) is PatientValidationResult.Invalid }) { "بيانات مرضى غير صالحة" }
            review.doctorsCsv?.let { doctorSync.uploadReviewedDoctors(it).getOrThrow() }
            val publishingDeviceId = requireNotNull(shift.publishedByDeviceId)
            requirePublicationReview(review, remoteState.readState())
            require(shift.doctorIds.distinct().size >= AppConstants.MIN_SHIFT_DOCTORS) { "حدد أطباء المناوبة قبل الرفع" }
            require(patients.none { PatientValidator.validate(it) is PatientValidationResult.Invalid }) { "بيانات مرضى غير صالحة" }
            val csv = review.csv
            val bundledShifts = CsvBundleCodec.decode(csv)

            val file = File.createTempFile("upload_shift_", ".csv", context.cacheDir)
            file.writeText(csv, Charsets.UTF_8)

            val msg = try {
                telegram.sendDocument(
                    chatId = topics.chatId,
                    file = file,
                    caption = "${shift.date} • آخر ${bundledShifts.size} مناوبات",
                    parseMode = null,
                    messageThreadId = topics.threadId(Topic.CSV)
                )
            } finally {
                file.delete()
            }
            val uploadedFileId = requireNotNull(msg.document?.fileId) {
                "لم يعُد تليجرام بمعرّف ملف CSV"
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
                current.withPublishedCsv(uploadedSnapshot, true).copy(
                    publicationJournal = (
                        current.publicationJournal + PublicationJournalEntry(
                            snapshotId = requireNotNull(shift.snapshotId),
                            parentSnapshotId = shift.baseSnapshotId,
                            fileId = uploadedFileId,
                            messageId = msg.messageId,
                            at = uploadedAt,
                            deviceId = publishingDeviceId,
                            forced = forceLocal
                        )
                    ).distinctBy { it.messageId }.sortedByDescending { it.messageId }.take(12)
                )
            }
            val published = newState.currentCsvSnapshot()
            check(published != null && published.messageId == msg.messageId) {
                "وصل نشر أحدث بالتزامن؛ راجع النسخة الحالية قبل نشر نسختك الكاملة"
            }
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
                    // Clear the changes represented by this publication, then re-check local state.
                    // An edit racing the network upload either sets the flag after this clear or is
                    // detected by the snapshot comparison below.
                    settingsRepository.put(AppConstants.SETTING_PATIENT_PUBLISHED_AT_PREFIX + shift.id, uploadedAt.toString())
                    settingsRepository.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
                    settingsRepository.put(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + shift.id,
                        CsvCodec.encode(shift, patients))
                    val currentPatients = patientRepository.getForShift(shift.id)
                    val currentShift = shiftRepository.getById(shift.id)
                    val changedDuringUpload = !samePublishedPatients(currentPatients, patients) ||
                        currentShift == null ||
                        currentShift.doctorIds != shift.doctorIds ||
                        currentShift.multiDoctorMode != shift.multiDoctorMode ||
                        currentShift.sortSpecJson != shift.sortSpecJson
                    val otherPending = shiftRepository.getRecent(12).filter { it.id != shift.id }.any { other ->
                        val baseline = settingsRepository.get(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + other.id)
                        baseline != null && KnownShiftSnapshot.compare(other, patientRepository.getForShift(other.id), baseline) == KnownShiftState.CHANGED
                    }
                    if (changedDuringUpload || otherPending) {
                        settingsRepository.putBoolean(
                            AppConstants.SETTING_PATIENTS_SYNC_PENDING,
                            true
                        )
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (error: Exception) {
                // Remote publication succeeded; keep an explicit bookkeeping diagnostic.
                Logging.e("CSV published, but local sync bookkeeping failed", error)
            }

            Result.success(CsvUploadResult(msg.messageId, patients.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun requireRollbackAssignments(source: SyncState, csv: String) {
        CsvBundleCodec.decode(csv).forEach { parsed ->
            doctorSync.requirePublishedAssignments(source, com.hos.rushdpatients.data.model.Shift(
                id = requireNotNull(parsed.shiftId), date = parsed.shiftDate, doctorIds = parsed.doctorIds), parsed.patients)
        }
    }

    suspend fun prepareRollback(): CsvRollbackReview = patientSyncMutex.withLock {
        withContext(dispatchers.io) {
            val source = requireNotNull(remoteState.readState()) { "فهرس المشروع غير متاح" }
            val actor = doctorSync.requirePublishedAdmin(source)
            val snapshot = requireNotNull(source.previousCsvSnapshot()) { "لا توجد نسخة منشورة سابقة" }
            val remote = telegram.getFile(snapshot.fileId)
            val local = File.createTempFile("rollback_review_", ".csv", context.cacheDir)
            val csv = try {
                telegram.downloadFile(requireNotNull(remote.filePath), local)
                local.readText(Charsets.UTF_8)
            } finally { local.delete() }
            val shifts = CsvBundleCodec.decode(csv)
            requireRollbackAssignments(source, csv)
            CsvRollbackReview(snapshot, csv, actor.id, actor.telegramId,
                "ستصبح هذه النسخة السابقة هي المنشورة للجميع. البيانات المحلية ستبقى كما هي؛ اجلبها لتطبيقها محلياً.\n" +
                    shifts.joinToString("\n") { "${it.shiftDate}: ${it.patients.size} مريض" })
        }
    }

    suspend fun publishRollback(review: CsvRollbackReview): Result<Unit> = patientSyncMutex.withLock {
        withContext(dispatchers.io) {
            try {
                val source = requireNotNull(remoteState.readState())
                val actor = doctorSync.requirePublishedAdmin(source)
                requireRollbackAssignments(source, review.csv)
                require(actor.id == review.actorId && actor.telegramId == review.telegramId) { "تغير المدير" }
                val file = File.createTempFile("rollback_publish_", ".csv", context.cacheDir)
                val message = try {
                    file.writeText(review.csv, Charsets.UTF_8)
                    telegram.sendDocument(chatId = topics.chatId, file = file, caption = "استعادة النسخة المنشورة السابقة",
                        parseMode = null, messageThreadId = topics.threadId(Topic.CSV))
                } finally { file.delete() }
                val currentSource = requireNotNull(remoteState.readState())
                val currentActor = doctorSync.requirePublishedAdmin(currentSource)
                requireRollbackAssignments(currentSource, review.csv)
                require(currentActor.id == actor.id && currentActor.telegramId == actor.telegramId) { "تغير المدير أثناء الرفع" }
                val snapshot = review.snapshot.copy(fileId = requireNotNull(message.document?.fileId),
                    messageId = message.messageId, updatedAt = message.date.takeIf { it > 0 }?.times(1000L) ?: System.currentTimeMillis())
                val parsed = CsvBundleCodec.decode(review.csv).first()
                val deviceId = getOrCreateDeviceId()
                remoteState.updateState { current -> current.withPublishedCsv(snapshot, true).copy(
                    publicationJournal = (current.publicationJournal + PublicationJournalEntry(
                        snapshotId = parsed.snapshotId ?: "rollback-${message.messageId}", parentSnapshotId = parsed.baseSnapshotId,
                        fileId = snapshot.fileId, messageId = snapshot.messageId, at = snapshot.updatedAt,
                        deviceId = deviceId, forced = true)).distinctBy { it.messageId }.sortedByDescending { it.messageId }.take(12)) }
                syncStateRepository.update(SyncChannel.CSV, snapshot.updatedAt, snapshot.messageId, snapshot.fileId)
                // Baselines now refer to a superseded publication; keep local data explicitly pending.
                settingsRepository.getAll().keys.filter { it.startsWith(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX) }
                    .forEach { settingsRepository.delete(it) }
                settingsRepository.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true)
                auditRepository.record(actor.id, actor.fullName, "csv_previous_republished", detail = "message=${message.messageId}")
                Result.success(Unit)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
        }
    }

    private suspend fun downloadSnapshot(snapshot: CsvSnapshot): List<ParsedShift> {
        val remote = telegram.getFile(snapshot.fileId)
        val path = requireNotNull(remote.filePath) { "تعذر الحصول على مسار الملف" }
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
