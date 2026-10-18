package com.hos.rushdpatients.sync

import android.content.Context
import androidx.room.withTransaction
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpecCodec
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
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.doctor.DoctorRegistryConflict
import com.hos.rushdpatients.domain.doctor.DoctorMergeChoice
import com.hos.rushdpatients.domain.patient.PatientValidationResult
import com.hos.rushdpatients.domain.patient.PatientValidator
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.Logging
import com.hos.rushdpatients.util.ShiftDate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CsvFetchResult(val shiftId: String, val inserted: Int, val updated: Int)
data class CsvUploadResult(val messageId: Long, val patientCount: Int)
data class HandoverChangeSummary(
    val added: List<String>,
    val changed: List<String>,
    val removed: List<String>
) {
    val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
}

private data class PreparedShift(
    val parsed: ParsedShift,
    val patients: List<Patient>,
    val doctorIds: List<String>
)

private data class AppliedShift(
    val shift: Shift,
    val patients: List<Patient>,
    val inserted: Int,
    val updated: Int
)

private data class PendingPatientMerge(
    val shiftId: String,
    val remote: ParsedShift,
    val base: List<Patient>,
    val local: List<Patient>
)

@Singleton
class SyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegram: TelegramClient,
    private val topics: Topics,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val syncStateRepository: SyncStateRepository,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val auditRepository: AuditRepository,
    private val dispatchers: DispatcherProvider,
    private val remoteState: RemoteSyncStateStore,
    private val doctorSync: DoctorSyncCoordinator
) {

    private val patientSyncMutex = Mutex()
    private val _patientConflicts = MutableStateFlow<List<PatientFieldConflict>>(emptyList())
    val patientConflicts: StateFlow<List<PatientFieldConflict>> = _patientConflicts.asStateFlow()
    private var pendingPatientMerge: PendingPatientMerge? = null
    val doctorConflicts: StateFlow<List<DoctorRegistryConflict>> get() = doctorSync.doctorConflicts

    suspend fun uploadDoctors(registryData: String): Result<Unit> = doctorSync.uploadDoctors(registryData)
    suspend fun uploadCurrentDoctors(): Result<Unit> = doctorSync.uploadCurrentDoctors()
    suspend fun synchronizeDoctors(): Result<Unit> = doctorSync.synchronizeDoctors()
    suspend fun refreshDoctorsFromRemote(): Result<Unit> = doctorSync.refreshDoctorsFromRemote()
    suspend fun fetchLatestDoctors(): Result<String> = doctorSync.fetchLatestDoctors()
    suspend fun fetchAndApplyLatestDoctors(): Result<Int> = doctorSync.fetchAndApplyLatestDoctors()
    suspend fun resolveDoctorConflicts(choices: Map<String, DoctorMergeChoice>): Result<Unit> =
        doctorSync.resolveDoctorConflicts(choices)

    suspend fun fetchLatestCsv(): Result<CsvFetchResult> = patientSyncMutex.withLock {
        fetchCsv(previous = false)
    }

    suspend fun fetchPreviousCsv(): Result<CsvFetchResult> = patientSyncMutex.withLock {
        fetchCsv(previous = true)
    }

    /**
     * Background-safe patient synchronization. Local edits are published before any pull so a
     * periodic worker cannot silently replace an unsent device change.
     */
    suspend fun synchronizeCurrentPatients(): Result<*> {
        val pending = settingsRepository.getBoolean(
            AppConstants.SETTING_PATIENTS_SYNC_PENDING,
            false
        )
        if (pending) {
            val shift = shiftRepository.getByDate(ShiftDate.current())
                ?: return Result.failure<Unit>(IllegalStateException("لا توجد وردية لمزامنتها"))
            return uploadCsv(shift.id)
        }
        return fetchLatestCsv()
    }

    suspend fun describeLocalChanges(shiftId: String): HandoverChangeSummary =
        withContext(dispatchers.io) {
            val current = patientRepository.getForShift(shiftId).associateBy(Patient::id)
            val base = settingsRepository.get(AppConstants.SETTING_PATIENTS_BASE_SNAPSHOT)
                ?.let { csv -> runCatching { CsvCodec.decode(csv) }.getOrNull() }
                ?.patients.orEmpty().associateBy(Patient::id)
            HandoverChangeSummary(
                added = (current.keys - base.keys).mapNotNull { current[it]?.name }.sorted(),
                changed = (current.keys intersect base.keys)
                    .filter { !samePatientContent(current[it], base[it]) }
                    .mapNotNull { current[it]?.name }
                    .sorted(),
                removed = (base.keys - current.keys).mapNotNull { base[it]?.name }.sorted()
            )
        }

    private suspend fun fetchCsv(previous: Boolean): Result<CsvFetchResult> =
        withContext(dispatchers.io) {
            try {
                if (!previous) {
                    require(
                        !settingsRepository.getBoolean(
                            AppConstants.SETTING_PATIENTS_SYNC_PENDING,
                            false
                        )
                    ) {
                        "توجد تعديلات محلية غير منشورة؛ ارفعها أولاً كي لا تُستبدل بأحدث نسخة"
                    }
                }
                val state = remoteState.readState()
                    ?: return@withContext Result.failure(IllegalStateException("لا يوجد ملف منشور بعد"))
                val snapshot = if (previous) {
                    state.previousCsvSnapshot()
                } else {
                    state.currentCsvSnapshot()
                }
                if (snapshot == null) {
                    val message = if (previous) {
                        "لا توجد نسخة سابقة محفوظة"
                    } else {
                        "لا يوجد ملف منشور بعد"
                    }
                    return@withContext Result.failure(IllegalStateException(message))
                }
                if (!previous) {
                    val lastKnownMessageId = syncStateRepository.lastMessageId(SyncChannel.CSV)
                    require(lastKnownMessageId == null || snapshot.messageId >= lastKnownMessageId) {
                        "النسخة المنشورة أقدم من آخر نسخة معروفة على هذا الجهاز؛ تم إيقاف الاستبدال للحماية"
                    }
                }

            val guardedShift = if (previous) null else shiftRepository.getByDate(ShiftDate.current())
            val guardedPatients = guardedShift
                ?.let { patientRepository.getForShift(it.id) }
                .orEmpty()

            val remote = telegram.getFile(snapshot.fileId)
            val path = remote.filePath
                ?: return@withContext Result.failure(IllegalStateException("تعذر الحصول على مسار الملف"))

            val local = File.createTempFile("remote_shift_", ".csv", context.cacheDir)
            val parsedShifts = try {
                telegram.downloadFile(path, local)
                CsvBundleCodec.decode(local.readText(Charsets.UTF_8))
            } finally {
                local.delete()
            }

            val activeDoctors = doctorRepository.getAll()
            val doctorsById = buildMap {
                activeDoctors.forEach { doctor ->
                    put(doctor.id, doctor)
                    doctor.extraOptions.asSequence()
                        .filter { it.startsWith("syncAlias:") }
                        .map { it.removePrefix("syncAlias:") }
                        .filter { it.isNotBlank() }
                        .forEach { alias -> put(alias, doctor) }
                }
            }
            val prepared = parsedShifts.map { parsed ->
                prepareShift(parsed, activeDoctors, doctorsById)
            }
            val applied = database.withTransaction {
                if (!previous) {
                    val currentShift = shiftRepository.getByDate(ShiftDate.current())
                    val currentPatients = currentShift
                        ?.let { patientRepository.getForShift(it.id) }
                        .orEmpty()
                    require(
                        !settingsRepository.getBoolean(
                            AppConstants.SETTING_PATIENTS_SYNC_PENDING,
                            false
                        ) &&
                        sameEditableShift(currentShift, guardedShift) &&
                            samePublishedPatients(currentPatients, guardedPatients)
                    ) {
                        "تغيرت البيانات المحلية أثناء التنزيل؛ أعد المزامنة لنشر التعديلات بأمان"
                    }
                }
                prepared.map { preparedShift ->
                    applyPreparedShift(preparedShift).also { appliedShift ->
                        shiftRepository.updateCsvMessageId(appliedShift.shift.id, snapshot.messageId)
                    }
                }
            }
            val latest = applied.first()
            val inserted = applied.sumOf { it.inserted }
            val updated = applied.sumOf { it.updated }

            if (!previous) {
                syncStateRepository.update(
                    channel = SyncChannel.CSV,
                    offset = snapshot.updatedAt,
                    messageId = snapshot.messageId,
                    fileId = snapshot.fileId
                )
            }
            settingsRepository.putBoolean(
                AppConstants.SETTING_PATIENTS_SYNC_PENDING,
                previous && latest.shift.date == ShiftDate.current()
            )
            settingsRepository.put(
                AppConstants.SETTING_PATIENTS_BASE_SNAPSHOT,
                CsvCodec.encode(latest.shift, latest.patients)
            )
            if (!previous) {
                val currentShift = shiftRepository.getById(latest.shift.id)
                val currentPatients = patientRepository.getForShift(latest.shift.id)
                if (!sameEditableShift(currentShift, latest.shift) ||
                    !samePublishedPatients(currentPatients, latest.patients)
                ) {
                    settingsRepository.putBoolean(
                        AppConstants.SETTING_PATIENTS_SYNC_PENDING,
                        true
                    )
                }
            }

            auditRepository.record(
                actorDoctorId = null,
                actorName = null,
                action = AppConstants.AUDIT_CSV_DOWNLOADED,
                detail = "shifts=${applied.size} latest=${latest.shift.date} " +
                    "source=${if (previous) "previous" else "latest"} inserted=$inserted updated=$updated"
            )

                Result.success(CsvFetchResult(latest.shift.id, inserted, updated))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun prepareShift(
        parsed: ParsedShift,
        activeDoctors: List<Doctor>,
        doctorsById: Map<String, Doctor>
    ): PreparedShift {
        val normalizedPatients = parsed.patients.map { patient ->
            patient.copy(
                responsibleResidentId = patient.responsibleResidentId
                    ?.let { doctorsById[it]?.id ?: it },
                responsibleSpecialistId = patient.responsibleSpecialistId
                    ?.let { doctorsById[it]?.id ?: it },
                tasks = patient.tasks.map { task ->
                    task.copy(ownerDoctorId = task.ownerDoctorId?.let { doctorsById[it]?.id ?: it })
                }
            )
        }
        val normalizedDoctorIds = parsed.doctorIds.map { doctorsById[it]?.id ?: it }
        val validationErrors = normalizedPatients.mapNotNull { patient ->
            val resident = patient.responsibleResidentId?.let(doctorsById::get)
            val specialist = patient.responsibleSpecialistId?.let(doctorsById::get)
            when (val result = PatientValidator.validate(patient)) {
                PatientValidationResult.Valid -> when {
                    !patient.responsibleResidentId.isNullOrBlank() && resident == null ->
                        "المقيم المسؤول غير موجود للمريض ${patient.name}"
                    !patient.responsibleSpecialistId.isNullOrBlank() && specialist == null ->
                        "الاختصاصي المسؤول غير موجود للمريض ${patient.name}"
                    patient.tasks.any { !it.done && it.ownerDoctorId != null && doctorsById[it.ownerDoctorId] == null } ->
                        "مسؤول مهمة غير موجود للمريض ${patient.name}"
                    resident != null && !resident.clinicalRole.canBeResident() ->
                        "تصنيف المقيم غير صالح للمريض ${patient.name}"
                    specialist != null && !specialist.clinicalRole.canBeSupervisor() ->
                        "تصنيف الاختصاصي غير صالح للمريض ${patient.name}"
                    else -> null
                }
                is PatientValidationResult.Invalid ->
                    "بيانات المريض ${patient.name} غير صالحة: ${result.errors.joinToString()}"
            }
        }
        require(validationErrors.isEmpty()) { validationErrors.joinToString("\n") }
        require(normalizedPatients.size <= AppConstants.MAX_PATIENTS_PER_SHIFT) {
            "عدد المرضى يتجاوز الحد الأقصى في مناوبة ${parsed.shiftDate}"
        }
        require(normalizedDoctorIds.all { id -> activeDoctors.any { it.id == id } }) {
            "قائمة أطباء مناوبة ${parsed.shiftDate} تحتوي معرّفات غير معروفة"
        }
        require(normalizedDoctorIds.distinct().size >= AppConstants.MIN_SHIFT_DOCTORS
        ) { "عدد أطباء مناوبة ${parsed.shiftDate} غير صالح" }
        return PreparedShift(parsed, normalizedPatients, normalizedDoctorIds.distinct())
    }

    private suspend fun applyPreparedShift(prepared: PreparedShift): AppliedShift {
        val parsed = prepared.parsed
        val existingShift = shiftRepository.getByDate(parsed.shiftDate)
        val existing = existingShift
            ?.let { patientRepository.getForShift(it.id).associateBy(Patient::id) }
            ?: emptyMap()
        var inserted = 0
        var updated = 0
        val toUpsert = prepared.patients.mapIndexed { index, patient ->
            val normalized = patient.copy(sortOrder = index + 1)
            if (existing.containsKey(normalized.id)) updated++ else inserted++
            normalized
        }
        val shift = existingShift
            ?: shiftRepository.getOrCreateForDate(parsed.shiftDate, parsed.shiftId)
        val conflicting = toUpsert.firstOrNull { patient ->
            patientRepository.getShiftId(patient.id)?.let { owner -> owner != shift.id } == true
        }
        require(conflicting == null) {
            "معرّف المريض ${conflicting?.id} مستخدم في وردية أخرى"
        }
        patientRepository.softDeleteMissingFromShift(shift.id, toUpsert.map(Patient::id))
        patientRepository.upsertAll(toUpsert, shift.id)
        val updatedShift = shift.copy(
            doctorIds = prepared.doctorIds,
            sortSpecJson = parsed.sortSpecJson,
            revision = parsed.shiftRevision,
            snapshotId = parsed.snapshotId,
            baseSnapshotId = parsed.baseSnapshotId,
            publishedByDeviceId = parsed.deviceId
        )
        shiftRepository.upsert(updatedShift)
        return AppliedShift(updatedShift, toUpsert, inserted, updated)
    }

    suspend fun uploadCsv(shiftId: String): Result<CsvUploadResult> =
        patientSyncMutex.withLock { uploadCsvWithRetry(shiftId) }

    private suspend fun uploadCsvWithRetry(shiftId: String): Result<CsvUploadResult> {
        var last: Result<CsvUploadResult>? = null
        repeat(3) { attempt ->
            val result = uploadCsvInternal(shiftId, forceCurrent = false)
            if (result.isSuccess) return result
            last = result
            val message = result.exceptionOrNull()?.message.orEmpty()
            val publicationRace = message.contains("بالتزامن") || message.contains("نسخة أحدث")
            if (!publicationRace || attempt == 2) return result
        }
        return last ?: Result.failure(IllegalStateException("تعذر نشر البيانات"))
    }

    suspend fun forceUploadCsv(shiftId: String): Result<CsvUploadResult> =
        patientSyncMutex.withLock { uploadCsvInternal(shiftId, forceCurrent = true) }

    private suspend fun uploadCsvInternal(
        shiftId: String,
        forceCurrent: Boolean
    ): Result<CsvUploadResult> = withContext(dispatchers.io) {
        try {
            var shift = shiftRepository.getById(shiftId)
                ?: return@withContext Result.failure(IllegalStateException("الوردية غير موجودة"))
            require(shift.date == ShiftDate.current()) {
                "المناوبات المحفوظة للعرض والاستعادة فقط ولا يمكن نشرها كالمناوبة الحالية"
            }

            var patients = patientRepository.getForShift(shiftId)
            val publishingDeviceId = getOrCreateDeviceId()
            val remoteStateBeforeMerge = remoteState.readState()
            val remoteSnapshotBeforeMerge = remoteStateBeforeMerge?.currentCsvSnapshot()
            val remoteShift = if (forceCurrent) null else remoteSnapshotBeforeMerge?.let { downloadSnapshot(it) }
                ?.firstOrNull { it.shiftDate == shift.date }

            if (!forceCurrent && remoteShift != null && remoteShift.snapshotId != shift.snapshotId) {
                val base = settingsRepository.get(AppConstants.SETTING_PATIENTS_BASE_SNAPSHOT)
                    ?.let { csv -> runCatching { CsvCodec.decode(csv) }.getOrNull() }
                    ?.takeIf { it.shiftDate == shift.date && it.snapshotId == shift.snapshotId }
                val basePatients = base?.patients.orEmpty()
                val merged = mergePatients(basePatients, patients, remoteShift.patients)
                if (merged.conflicts.isNotEmpty()) {
                    pendingPatientMerge = PendingPatientMerge(
                        shiftId = shift.id,
                        remote = remoteShift,
                        base = basePatients,
                        local = patients
                    )
                    _patientConflicts.value = merged.conflicts
                    error("توجد ${merged.conflicts.size} حقول متعارضة وتحتاج إلى مراجعة")
                }
                pendingPatientMerge = null
                _patientConflicts.value = emptyList()
                patients = merged.patients
                patientRepository.softDeleteMissingFromShift(shift.id, patients.map(Patient::id))
                patientRepository.upsertAll(patients, shift.id)
                auditRepository.record(
                    null, null, AppConstants.AUDIT_SYNC_MERGED,
                    "shift=${shift.id} remote=${remoteShift.snapshotId} patients=${patients.size}"
                )
            }

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
            val newState = remoteState.updateState { current ->
                if (!forceCurrent) {
                    require(current.currentCsvSnapshot()?.messageId == remoteSnapshotBeforeMerge?.messageId) {
                        "وصلت نسخة أحدث أثناء الرفع؛ أعد المحاولة ليتم دمجها"
                    }
                }
                val previousCurrent = current.currentCsvSnapshot()
                val rotateRecovery = previousCurrent == null ||
                    previousCurrent.updatedAt <= 0L ||
                    uploadedSnapshot.updatedAt - previousCurrent.updatedAt >=
                    AppConstants.CSV_VERSION_WINDOW_HOURS * 60L * 60L * 1_000L
                val protectedCurrent = if (forceCurrent) current.copy(
                    previousCsvFileId = current.csvFileId,
                    previousCsvMessageId = current.csvMessageId,
                    previousCsvDate = current.csvDate,
                    previousCsvUpdatedAt = current.csvUpdatedAt
                ) else current
                protectedCurrent.withPublishedCsv(uploadedSnapshot, rotateRecovery).copy(
                    publicationJournal = (
                        current.publicationJournal + PublicationJournalEntry(
                            snapshotId = requireNotNull(shift.snapshotId),
                            parentSnapshotId = shift.baseSnapshotId,
                            fileId = uploadedFileId,
                            messageId = msg.messageId,
                            at = uploadedAt,
                            deviceId = publishingDeviceId,
                            forced = forceCurrent
                        )
                    ).distinctBy { it.messageId }.sortedByDescending { it.messageId }.take(12)
                )
            }
            val published = newState.currentCsvSnapshot()
            check(published != null && published.messageId == msg.messageId) {
                "وصل نشر أحدث بالتزامن؛ أعد المحاولة لدمج النسختين"
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
                if (forceCurrent) {
                    auditRepository.record(
                        null, null, AppConstants.AUDIT_SYNC_FORCED,
                        "shift=$shiftId replacedMessage=${remoteSnapshotBeforeMerge?.messageId ?: "none"} " +
                            "replacement=${shift.snapshotId} device=$publishingDeviceId"
                    )
                }
                // Clear the changes represented by this publication, then re-check local state.
                // An edit racing the network upload either sets the flag after this clear or is
                // detected by the snapshot comparison below.
                settingsRepository.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
                settingsRepository.put(
                    AppConstants.SETTING_PATIENTS_BASE_SNAPSHOT,
                    CsvCodec.encode(shift, patients)
                )
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
        val local = File.createTempFile("merge_shift_", ".csv", context.cacheDir)
        return try {
            telegram.downloadFile(path, local)
            CsvBundleCodec.decode(local.readText(Charsets.UTF_8))
        } finally {
            local.delete()
        }
    }

    suspend fun resolvePatientConflicts(
        resolutions: Map<String, ConflictChoice>
    ): Result<CsvUploadResult> = patientSyncMutex.withLock {
        val pending = pendingPatientMerge
            ?: return@withLock Result.failure(IllegalStateException("لا توجد تعارضات معلقة"))
        val merged = mergePatients(pending.base, pending.local, pending.remote.patients, resolutions)
        if (merged.conflicts.isNotEmpty()) {
            _patientConflicts.value = merged.conflicts
            return@withLock Result.failure(IllegalStateException("لم تُحسم جميع الحقول المتعارضة"))
        }
        runCatching {
            val shift = shiftRepository.getById(pending.shiftId) ?: error("الوردية غير موجودة")
            patientRepository.softDeleteMissingFromShift(shift.id, merged.patients.map(Patient::id))
            patientRepository.upsertAll(merged.patients, shift.id)
            val remoteBase = shift.copy(
                doctorIds = pending.remote.doctorIds,
                sortSpecJson = pending.remote.sortSpecJson,
                revision = pending.remote.shiftRevision,
                snapshotId = pending.remote.snapshotId,
                baseSnapshotId = pending.remote.baseSnapshotId,
                publishedByDeviceId = pending.remote.deviceId
            )
            shiftRepository.upsert(remoteBase)
            settingsRepository.put(
                AppConstants.SETTING_PATIENTS_BASE_SNAPSHOT,
                CsvCodec.encode(remoteBase, pending.remote.patients)
            )
            pendingPatientMerge = null
            _patientConflicts.value = emptyList()
            auditRepository.record(null, null, AppConstants.AUDIT_SYNC_MERGED,
                "resolvedFields=${resolutions.size} shift=${shift.id}")
            uploadCsvWithRetry(shift.id).getOrThrow()
        }
    }

    fun dismissPatientConflicts() {
        pendingPatientMerge = null
        _patientConflicts.value = emptyList()
    }

    suspend fun getOrCreateDeviceId(): String {
        settingsRepository.get(AppConstants.SETTING_DEVICE_ID)?.takeIf(String::isNotBlank)?.let {
            return it
        }
        val created = UUID.randomUUID().toString()
        settingsRepository.put(AppConstants.SETTING_DEVICE_ID, created)
        return created
    }

    suspend fun getPublicationJournal(): List<PublicationJournalEntry> =
        remoteState.readState()?.publicationJournal.orEmpty().sortedByDescending { it.messageId }

    private fun samePublishedPatients(left: List<Patient>, right: List<Patient>): Boolean {
        fun Patient.withoutLocalMetadata() = copy(
            updatedAt = java.time.Instant.EPOCH,
            revision = 0
        )
        return left.associate { it.id to it.withoutLocalMetadata() } ==
            right.associate { it.id to it.withoutLocalMetadata() }
    }

    private fun sameEditableShift(left: Shift?, right: Shift?): Boolean {
        if (left == null || right == null) return left == right
        return left.id == right.id &&
            left.date == right.date &&
            left.doctorIds == right.doctorIds &&
            left.multiDoctorMode == right.multiDoctorMode &&
            left.sortSpecJson == right.sortSpecJson
    }

    suspend fun recordAnnouncement(messageId: Long) = withContext(dispatchers.io) {
        remoteState.updateState { current ->
            current.copy(
                announcementMessageId = messageId,
                announcementUpdatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun announcementMessageId(): Long? = withContext(dispatchers.io) {
        remoteState.readState()?.announcementMessageId
    }

}
