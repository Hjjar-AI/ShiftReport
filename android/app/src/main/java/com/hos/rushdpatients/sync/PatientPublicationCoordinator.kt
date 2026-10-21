package com.hos.rushdpatients.sync

import com.hos.rushdpatients.domain.doctor.DoctorRegistryAction
import com.hos.rushdpatients.domain.doctor.DoctorRegistryActionRequired
import android.content.Context
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
    private val reportBuilder: ReportBuilder
) {

    suspend fun prepareCsvPublication(shiftId: String, forceLocal: Boolean = false): CsvPublicationReview = patientSyncMutex.withLock {
        withContext(dispatchers.io) {
            if (!forceLocal) doctorRepository.pendingSharedChangesSummary()?.let { throw DoctorRegistryActionRequired(DoctorRegistryAction.PUBLISH, it) }
            val source = requireNotNull(remoteState.readState()) { "فهرس المشروع غير متاح؛ أعد الجلب" }
            val actor = doctorSync.requirePublishedClinician(source)
            val built = reportBuilder.build(shiftId)
            if (forceLocal) doctorSync.requirePublishedAssignments(source, built.shift, built.patients)
            else doctorSync.requireRegistryMatchesPublished(source)
            require(built.shift.date == ShiftDate.current()) { "راجع مناوبة اليوم فقط قبل النشر" }
            val remote = source.currentCsvSnapshot()?.let { downloadSnapshot(it) }
                ?.firstOrNull { it.shiftDate == built.shift.date }
            val before = remote?.patients.orEmpty().associateBy(Patient::id)
            val after = built.patients.associateBy(Patient::id)
            fun names(ids: Set<String>, rows: Map<String, Patient>) =
                ids.mapNotNull { rows[it]?.name }.sorted().joinToString("، ").ifBlank { "لا يوجد" }
            val changed = (before.keys intersect after.keys).filter {
                !samePatientContent(before[it], after[it])
            }.toSet()
            val text = buildString {
                append("ستُنشر النسخة المحلية الكاملة للمناوبة، بما فيها الحذف والترتيب وأطباء المناوبة. آخر نشر ناجح هو المعتمد.\n")
                append("إضافات: ").append(names(after.keys - before.keys, after)).append('\n')
                append("تعديلات: ").append(names(changed, after)).append('\n')
                append("حذف من النسخة المنشورة: ").append(names(before.keys - after.keys, before)).append("\n\n")
                append(built.textChunks.joinToString("\n\n") { Markdown.toPlainText(it) })
            }
            check(if (forceLocal) sameDoctorPublicationSource(remoteState.readState(), source)
                else samePublicationSource(remoteState.readState(), source)) {
                "تغيّرت النسخة المنشورة أثناء إعداد المراجعة؛ أعد المراجعة"
            }
            CsvPublicationReview(shiftId, CsvCodec.encode(built.shift, built.patients), source,
                actor.id, actor.telegramId, text)
        }
    }

    suspend fun uploadCsv(shiftId: String): Result<CsvUploadResult> =
        patientSyncMutex.withLock { uploadCsvInternal(shiftId) }

    suspend fun uploadReviewedCsv(review: CsvPublicationReview, forceLocal: Boolean = false): Result<CsvUploadResult> =
        patientSyncMutex.withLock { uploadCsvInternal(review.shiftId, review, forceLocal) }

    private suspend fun requirePublicationReview(review: CsvPublicationReview, state: SyncState?, forceLocal: Boolean) {
        check(if (forceLocal) sameDoctorPublicationSource(state, review.source)
            else samePublicationSource(state, review.source)) {
            "تغيّرت نسخة المرضى أو الأطباء بعد المراجعة؛ أعد مراجعة النشر"
        }
        val actor = doctorSync.requirePublishedClinician(requireNotNull(state))
        require(actor.id == review.actorId && actor.telegramId == review.telegramId) {
            "تغيّر المستخدم؛ أعد مراجعة النشر"
        }
        val current = reportBuilder.build(review.shiftId)
        if (forceLocal) doctorSync.requirePublishedAssignments(requireNotNull(state), current.shift, current.patients)
        else doctorRepository.pendingSharedChangesSummary()?.let { throw DoctorRegistryActionRequired(DoctorRegistryAction.PUBLISH, it) }
        check(current.shift.date == ShiftDate.current() &&
            CsvCodec.encode(current.shift, current.patients) == review.csv) {
            "تغيرت المناوبة أو البيانات المحلية بعد المراجعة؛ أعد المراجعة"
        }
    }

    private suspend fun uploadCsvInternal(
        shiftId: String,
        review: CsvPublicationReview? = null,
        forceLocal: Boolean = false
    ): Result<CsvUploadResult> = withContext(dispatchers.io) {
        try {
            var shift = shiftRepository.getById(shiftId)
                ?: return@withContext Result.failure(IllegalStateException("الوردية غير موجودة"))
            require(shift.date == ShiftDate.current()) {
                "المناوبات المحفوظة للعرض والاستعادة فقط ولا يمكن نشرها كالمناوبة الحالية"
            }

            val patients = patientRepository.getForShift(shiftId)
            val publishingDeviceId = getOrCreateDeviceId()
            val remoteStateBeforeUpload = remoteState.readState()
            val remoteSnapshotBeforeUpload = remoteStateBeforeUpload?.currentCsvSnapshot()
            if (review != null) requirePublicationReview(review, remoteStateBeforeUpload, forceLocal)
            val remoteShift = remoteSnapshotBeforeUpload?.let { downloadSnapshot(it) }
                ?.firstOrNull { it.shiftDate == shift.date }

            // Publish this complete local snapshot; never merge remote fields into it.

            val localPublicationShift = shift
            shift = shift.copy(
                baseSnapshotId = remoteShift?.snapshotId ?: shift.snapshotId,
                snapshotId = UUID.randomUUID().toString(),
                revision = maxOf(shift.revision, remoteShift?.shiftRevision ?: 0L) + 1,
                publishedByDeviceId = publishingDeviceId
            )
            require(shift.doctorIds.distinct().size >= AppConstants.MIN_SHIFT_DOCTORS) {
                "حدد أطباء المناوبة قبل الرفع"
            }
            val invalidPatient = patients.firstOrNull {
                PatientValidator.validate(it) is PatientValidationResult.Invalid
            }
            require(invalidPatient == null) {
                "بيانات المريض ${invalidPatient?.name.orEmpty()} غير مكتملة"
            }
            if (review != null) {
                val orderedPatients = PatientComparators.ordered(patients, SortSpecCodec.decode(localPublicationShift.sortSpecJson),
                    doctorRepository.getAllIncludingDeleted().associate { it.id to it.fullName })
                check(CsvCodec.encode(localPublicationShift, orderedPatients) == review.csv) {
                    "تغيرت البيانات أثناء تجهيز النشر؛ أعد المراجعة"
                }
            }
            val previouslyUploaded = shiftRepository.getRecent(12)
                .filter { candidate ->
                    candidate.id == shift.id || candidate.csvMessageId != null
                }
            val bundledShifts = (listOf(shift) + previouslyUploaded)
                .distinctBy { it.id }
                .sortedByDescending { it.date }
                .take(AppConstants.CSV_BUNDLE_SHIFT_COUNT)
            val doctorNames = doctorRepository.getAllIncludingDeleted().associate { it.id to it.fullName }
            val csv = CsvBundleCodec.encode(
                bundledShifts.map { bundledShift ->
                    ShiftCsvSnapshot(
                        shift = bundledShift,
                        patients = PatientComparators.ordered(
                            if (bundledShift.id == shift.id) patients
                            else patientRepository.getForShift(bundledShift.id),
                            SortSpecCodec.decode(bundledShift.sortSpecJson),
                            doctorNames
                        )
                    )
                }
            )

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
            if (review != null) requirePublicationReview(review, remoteState.readState(), forceLocal)
            val newState = remoteState.updateState { current ->
                require(forceLocal || (current.currentCsvSnapshot()?.fileId == remoteSnapshotBeforeUpload?.fileId &&
                    current.currentCsvSnapshot()?.messageId == remoteSnapshotBeforeUpload?.messageId)) {
                    "وصلت نسخة أحدث أثناء الرفع؛ راجع وانشر النسخة الكاملة مجدداً"
                }
                if (review != null) check(if (forceLocal) sameDoctorPublicationSource(current, review.source)
                    else samePublicationSource(current, review.source)) {
                    "تغير فهرس المرضى أو الأطباء؛ أعد مراجعة النشر"
                }
                val previousCurrent = current.currentCsvSnapshot()
                val rotateRecovery = previousCurrent == null ||
                    previousCurrent.updatedAt <= 0L ||
                    uploadedSnapshot.updatedAt - previousCurrent.updatedAt >=
                    AppConstants.CSV_VERSION_WINDOW_HOURS * 60L * 60L * 1_000L
                current.withPublishedCsv(uploadedSnapshot, rotateRecovery).copy(
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
            runCatching {
                syncStateRepository.update(
                    channel = SyncChannel.CSV,
                    offset = published.updatedAt,
                    messageId = published.messageId,
                    fileId = published.fileId
                )
                shiftRepository.updatePublicationMetadata(shift, msg.messageId)
                auditRepository.record(
                    actorDoctorId = null,
                    actorName = null,
                    action = AppConstants.AUDIT_CSV_UPLOADED,
                    detail = "shift=$shiftId patients=${patients.size} snapshot=${shift.snapshotId} device=$publishingDeviceId"
                )
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
                if (changedDuringUpload) {
                    settingsRepository.putBoolean(
                        AppConstants.SETTING_PATIENTS_SYNC_PENDING,
                        true
                    )
                }
            }.onFailure { error ->
                // The remote snapshot is already authoritative; local bookkeeping must not
                // invite a duplicate report or upload.
                Logging.e("CSV published, but local sync bookkeeping failed", error)
            }

            Result.success(CsvUploadResult(msg.messageId, patients.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
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
