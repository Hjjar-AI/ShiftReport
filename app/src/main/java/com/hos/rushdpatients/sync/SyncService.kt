package com.hos.rushdpatients.sync

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
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SyncStateRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.patient.PatientValidationResult
import com.hos.rushdpatients.domain.patient.PatientValidator
import com.hos.rushdpatients.network.telegram.TelegramClient
import com.hos.rushdpatients.network.telegram.TelegramException
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
import kotlinx.serialization.json.Json
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

private data class CsvSnapshot(
    val fileId: String,
    val messageId: Long,
    val date: String?,
    val updatedAt: Long
)

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

private data class PatientMergeResult(
    val patients: List<Patient>,
    val conflicts: List<PatientFieldConflict>
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
    private val json: Json,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val syncStateRepository: SyncStateRepository,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val auditRepository: AuditRepository,
    private val dispatchers: DispatcherProvider
) {

    private val remoteStateMutex = Mutex()
    private val patientSyncMutex = Mutex()
    private val doctorSyncMutex = Mutex()
    private val _patientConflicts = MutableStateFlow<List<PatientFieldConflict>>(emptyList())
    val patientConflicts: StateFlow<List<PatientFieldConflict>> = _patientConflicts.asStateFlow()
    private var pendingPatientMerge: PendingPatientMerge? = null

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
                val state = readState()
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
                    ?.let { doctorsById[it]?.id ?: it }
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
        require(normalizedDoctorIds.distinct().size in
            AppConstants.MIN_SHIFT_DOCTORS..AppConstants.MAX_SHIFT_DOCTORS
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
            val remoteStateBeforeMerge = readState()
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
            require(shift.doctorIds.distinct().size in
                    AppConstants.MIN_SHIFT_DOCTORS..AppConstants.MAX_SHIFT_DOCTORS) {
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
            val csv = CsvBundleCodec.encode(
                bundledShifts.map { bundledShift ->
                    ShiftCsvSnapshot(
                        shift = bundledShift,
                        patients = if (bundledShift.id == shift.id) {
                            patients
                        } else {
                            patientRepository.getForShift(bundledShift.id)
                        }
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
            val newState = updateState { current ->
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

    private fun mergePatients(
        basePatients: List<Patient>,
        localPatients: List<Patient>,
        remotePatients: List<Patient>,
        resolutions: Map<String, ConflictChoice> = emptyMap()
    ): PatientMergeResult {
        val base = basePatients.associateBy(Patient::id)
        val local = localPatients.associateBy(Patient::id)
        val remote = remotePatients.associateBy(Patient::id)
        val merged = mutableListOf<Patient>()
        val conflicts = mutableListOf<PatientFieldConflict>()

        (base.keys + local.keys + remote.keys).forEach { id ->
            val before = base[id]
            val ours = local[id]
            val theirs = remote[id]

            if (before == null || ours == null || theirs == null) {
                val localChanged = !samePatientContent(ours, before)
                val remoteChanged = !samePatientContent(theirs, before)
                val key = "$id:record"
                val selected = when {
                    localChanged && remoteChanged && samePatientContent(ours, theirs) -> ours
                    localChanged && remoteChanged -> when (resolutions[key]) {
                        ConflictChoice.LOCAL -> ours
                        ConflictChoice.REMOTE -> theirs
                        null -> {
                            conflicts += PatientFieldConflict(
                                id,
                                ours?.name ?: theirs?.name ?: before?.name ?: id,
                                "record",
                                "السجل الكامل / الحذف",
                                ours?.name ?: "محذوف محلياً",
                                theirs?.name ?: "محذوف عن بعد"
                            )
                            null
                        }
                    }
                    localChanged -> ours
                    remoteChanged -> theirs
                    else -> ours ?: theirs
                }
                selected?.let(merged::add)
                return@forEach
            }
            val localRecordChanged = !samePatientContent(ours, before)

            fun <T> field(
                name: String,
                label: String,
                baseValue: T,
                localValue: T,
                remoteValue: T,
                display: (T) -> String = { it.toString() }
            ): T {
                val localChanged = localValue != baseValue
                val remoteChanged = remoteValue != baseValue
                if (!localChanged) return remoteValue
                if (!remoteChanged || localValue == remoteValue) return localValue
                val key = "$id:$name"
                return when (resolutions[key]) {
                    ConflictChoice.LOCAL -> localValue
                    ConflictChoice.REMOTE -> remoteValue
                    null -> {
                        conflicts += PatientFieldConflict(
                            patientId = id,
                            patientName = ours.name.ifBlank { theirs.name },
                            field = name,
                            fieldLabel = label,
                            localValue = display(localValue),
                            remoteValue = display(remoteValue)
                        )
                        localValue
                    }
                }
            }

            merged += before.copy(
                admittanceNumber = field("admittanceNumber", "رقم القبول الحالي", before.admittanceNumber, ours.admittanceNumber, theirs.admittanceNumber),
                admittanceDate = field("admittanceDate", "تاريخ الدخول", before.admittanceDate, ours.admittanceDate, theirs.admittanceDate),
                gender = field("gender", "الجنس", before.gender, ours.gender, theirs.gender) {
                    if (it == com.hos.rushdpatients.data.model.Gender.MALE) "ذكر" else "أنثى"
                },
                name = field("name", "الاسم", before.name, ours.name, theirs.name),
                birthDate = field("birthDate", "تاريخ الميلاد", before.birthDate, ours.birthDate, theirs.birthDate),
                hasCompanion = field("hasCompanion", "المرافق", before.hasCompanion, ours.hasCompanion, theirs.hasCompanion),
                diagnosisType = field("diagnosisType", "نوع التشخيص", before.diagnosisType, ours.diagnosisType, theirs.diagnosisType) { it.arabicLabel },
                initialDiagnosis = field("initialDiagnosis", "التشخيص الأولي", before.initialDiagnosis, ours.initialDiagnosis, theirs.initialDiagnosis),
                treatmentPlan = field("treatmentPlan", "الخطة العلاجية", before.treatmentPlan, ours.treatmentPlan, theirs.treatmentPlan),
                followUp = field("followUp", "المتابعة", before.followUp, ours.followUp, theirs.followUp),
                labs = field("labs", "التحاليل", before.labs, ours.labs, theirs.labs),
                responsibleResidentId = field("resident", "المقيم", before.responsibleResidentId, ours.responsibleResidentId, theirs.responsibleResidentId),
                responsibleSpecialistId = field("specialist", "الاختصاصي", before.responsibleSpecialistId, ours.responsibleSpecialistId, theirs.responsibleSpecialistId),
                badges = field("badges", "الشارات", before.badges, ours.badges, theirs.badges) { badges ->
                    badges.joinToString("، ") { badge ->
                        badge.priority?.let { "${badge.text} (${it.arabicLabel})" } ?: badge.text
                    }
                },
                isPriority = field("priority", "الأولوية", before.isPriority, ours.isPriority, theirs.isPriority),
                lastEditedByDoctorId = if (localRecordChanged) ours.lastEditedByDoctorId else theirs.lastEditedByDoctorId,
                lastEditedByName = if (localRecordChanged) ours.lastEditedByName else theirs.lastEditedByName,
                revision = maxOf(ours.revision, theirs.revision) + 1,
                updatedAt = java.time.Instant.now(),
                sortOrder = ours.sortOrder
            )
        }
        return PatientMergeResult(
            patients = merged.sortedWith(compareBy<Patient> { it.sortOrder }.thenBy { it.name }),
            conflicts = conflicts
        )
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
        readState()?.publicationJournal.orEmpty().sortedByDescending { it.messageId }

    private fun samePatientContent(left: Patient?, right: Patient?): Boolean {
        if (left == null || right == null) return left == right
        return left.copy(updatedAt = java.time.Instant.EPOCH, revision = 0, sortOrder = 0) ==
            right.copy(updatedAt = java.time.Instant.EPOCH, revision = 0, sortOrder = 0)
    }

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

    suspend fun uploadDoctors(registryData: String): Result<Unit> = doctorSyncMutex.withLock {
        uploadDoctorsInternal(registryData)
    }

    private suspend fun uploadDoctorsInternal(
        registryData: String
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val remoteStateBeforeUpload = readState()
            val remoteMessageIdBeforeUpload = remoteStateBeforeUpload?.doctorsMessageId
            val lastKnownMessageId = syncStateRepository.lastMessageId(SyncChannel.DOCTORS)
            require(remoteMessageIdBeforeUpload == null || remoteMessageIdBeforeUpload == lastKnownMessageId) {
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

            val newState = updateState { current ->
                require(current.doctorsMessageId == remoteMessageIdBeforeUpload) {
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
            syncStateRepository.update(
                channel = SyncChannel.DOCTORS,
                offset = newState.doctorsUpdatedAt,
                messageId = message.messageId,
                fileId = uploadedFileId
            )

            auditRepository.record(
                actorDoctorId = null,
                actorName = null,
                action = AppConstants.AUDIT_DOCTORS_SYNCED,
                detail = "size=${registryData.length}"
            )

            settingsRepository.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)

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

    suspend fun uploadCurrentDoctors(): Result<Unit> {
        val doctors = doctorRepository.getAll()
        return uploadDoctors(DoctorsRegistryCodec.encode(doctors))
    }

    suspend fun synchronizeDoctors(): Result<Unit> {
        val hasPendingLocalChanges = settingsRepository.getBoolean(
            AppConstants.SETTING_DOCTORS_SYNC_PENDING,
            false
        )
        return if (hasPendingLocalChanges) {
            uploadCurrentDoctors()
        } else {
            val fetched = fetchAndApplyLatestDoctors()
            val error = fetched.exceptionOrNull()
            if (error is IllegalStateException &&
                error.message == "لا يوجد سجل أطباء بعد" &&
                doctorRepository.getAll().isNotEmpty()
            ) {
                // Upgrade an older Telegram state that has CSV data but no doctor registry.
                uploadCurrentDoctors()
            } else {
                fetched.map { Unit }
            }
        }
    }

    /**
     * User-requested refresh. Unlike background synchronization, this deliberately accepts the
     * authoritative remote registry and is the recovery path after a stale publish is rejected.
     */
    suspend fun refreshDoctorsFromRemote(): Result<Unit> {
        val fetched = fetchAndApplyLatestDoctors()
        val error = fetched.exceptionOrNull()
        return if (error is IllegalStateException &&
            error.message == "لا يوجد سجل أطباء بعد" &&
            doctorRepository.getAll().isNotEmpty()
        ) {
            uploadCurrentDoctors()
        } else {
            fetched.map { Unit }
        }
    }

    suspend fun fetchLatestDoctors(): Result<String> = doctorSyncMutex.withLock {
        fetchLatestDoctorsInternal()
    }

    private suspend fun fetchLatestDoctorsInternal(): Result<String> = withContext(dispatchers.io) {
        try {
            val state = readState()
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
            val state = readState()
                ?: return@withContext Result.failure(IllegalStateException("لا يوجد سجل أطباء بعد"))
            val data = downloadDoctorsData(state)
            val decoded = DoctorsRegistryCodec.decode(data)
            require(decoded.doctors.isNotEmpty()) { "سجل الأطباء المنشور فارغ أو غير صالح" }
            doctorRepository.replaceAll(decoded.doctors)
            syncStateRepository.update(
                channel = SyncChannel.DOCTORS,
                offset = state.doctorsUpdatedAt,
                messageId = state.doctorsMessageId,
                fileId = state.doctorsFileId
            )
            settingsRepository.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
            Result.success(decoded.doctors.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordAnnouncement(messageId: Long) = withContext(dispatchers.io) {
        updateState { current ->
            current.copy(
                announcementMessageId = messageId,
                announcementUpdatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun announcementMessageId(): Long? = withContext(dispatchers.io) {
        readState()?.announcementMessageId
    }

    // --------------- internal ---------------

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

    private suspend fun updateState(transform: (SyncState) -> SyncState): SyncState =
        remoteStateMutex.withLock {
            val updated = transform(readState() ?: SyncState()).copy(v = SyncState.CURRENT_VERSION)
            writeState(updated)
            readState() ?: updated
        }

    private fun SyncState.currentCsvSnapshot(): CsvSnapshot? {
        val fileId = csvFileId ?: return null
        val messageId = csvMessageId ?: return null
        return CsvSnapshot(fileId, messageId, csvDate, csvUpdatedAt)
    }

    private fun SyncState.previousCsvSnapshot(): CsvSnapshot? {
        val fileId = previousCsvFileId ?: return null
        val messageId = previousCsvMessageId ?: return null
        return CsvSnapshot(fileId, messageId, previousCsvDate, previousCsvUpdatedAt)
    }

    /**
     * Telegram documents are immutable. The current pointer can advance repeatedly during the
     * 10-hour editing window while the previous recovery version remains fixed.
     */
    private fun SyncState.withPublishedCsv(
        uploaded: CsvSnapshot,
        rotateRecovery: Boolean
    ): SyncState {
        if (!rotateRecovery && currentCsvSnapshot() != null) {
            return copy(
                csvFileId = uploaded.fileId,
                csvMessageId = uploaded.messageId,
                csvDate = uploaded.date,
                csvUpdatedAt = uploaded.updatedAt
            )
        }
        val snapshots = listOfNotNull(currentCsvSnapshot(), previousCsvSnapshot(), uploaded)
            .distinctBy { it.messageId }
            .sortedByDescending { it.messageId }
        val latest = snapshots.first()
        val previous = snapshots.getOrNull(1)
        return copy(
            csvFileId = latest.fileId,
            csvMessageId = latest.messageId,
            csvDate = latest.date,
            csvUpdatedAt = latest.updatedAt,
            previousCsvFileId = previous?.fileId,
            previousCsvMessageId = previous?.messageId,
            previousCsvDate = previous?.date,
            previousCsvUpdatedAt = previous?.updatedAt ?: 0L
        )
    }

    private suspend fun readState(): SyncState? {
        val chat = telegram.getChat(topics.chatId)
        val pinned = chat.pinnedMessage ?: return null
        val text = pinned.text ?: return null
        return runCatching { json.decodeFromString<SyncState>(text) }
            .getOrNull()
            ?.takeIf { it.isRecognized }
    }

    private suspend fun writeState(state: SyncState) {
        val chat = telegram.getChat(topics.chatId)
        val existing = chat.pinnedMessage?.takeIf { message ->
            val text = message.text ?: return@takeIf false
            runCatching { json.decodeFromString<SyncState>(text) }
                .getOrNull()
                ?.isRecognized == true
        }

        val existingState = existing?.text?.let { text ->
            runCatching { json.decodeFromString<SyncState>(text) }.getOrNull()
        }
        val stateToWrite = if (existingState == null) state else {
            val snapshots = listOfNotNull(
                state.currentCsvSnapshot(), state.previousCsvSnapshot(),
                existingState.currentCsvSnapshot(), existingState.previousCsvSnapshot()
            ).distinctBy { it.messageId }.sortedByDescending { it.messageId }
            val latest = snapshots.firstOrNull()
            val previous = snapshots.getOrNull(1)
            val doctorsState = listOf(state, existingState).maxWithOrNull(
                compareBy<SyncState> { it.doctorsMessageId ?: Long.MIN_VALUE }
                    .thenBy { it.doctorsUpdatedAt }
            ) ?: state
            val announcementState = listOf(state, existingState).maxWithOrNull(
                compareBy<SyncState> { it.announcementUpdatedAt }
                    .thenBy { it.announcementMessageId ?: Long.MIN_VALUE }
            ) ?: state
            state.copy(
                csvFileId = latest?.fileId,
                csvMessageId = latest?.messageId,
                csvDate = latest?.date,
                csvUpdatedAt = latest?.updatedAt ?: 0L,
                previousCsvFileId = previous?.fileId,
                previousCsvMessageId = previous?.messageId,
                previousCsvDate = previous?.date,
                previousCsvUpdatedAt = previous?.updatedAt ?: 0L,
                publicationJournal = (state.publicationJournal + existingState.publicationJournal)
                    .distinctBy { it.messageId }
                    .sortedByDescending { it.messageId }
                    .take(12),
                doctorsData = doctorsState.doctorsData,
                doctorsFileId = doctorsState.doctorsFileId,
                doctorsMessageId = doctorsState.doctorsMessageId,
                doctorsUpdatedAt = doctorsState.doctorsUpdatedAt,
                announcementMessageId = announcementState.announcementMessageId,
                announcementUpdatedAt = announcementState.announcementUpdatedAt
            )
        }
        val encoded = json.encodeToString(SyncState.serializer(), stateToWrite)
        if (existing == null) {
            val msg = telegram.sendMessage(
                chatId = topics.chatId,
                text = encoded,
                parseMode = null,
                disableNotification = true
            )
            telegram.pinMessage(topics.chatId, msg.messageId, disableNotification = true)
        } else {
            try {
                telegram.editMessageText(
                    chatId = topics.chatId,
                    messageId = existing.messageId,
                    text = encoded,
                    parseMode = null
                )
            } catch (e: TelegramException) {
                if (!e.message.contains("not modified", ignoreCase = true)) throw e
            }
        }
    }
}
