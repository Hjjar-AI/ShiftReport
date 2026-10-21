package com.hos.rushdpatients.sync

import android.content.Context
import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
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
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

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

/** Shares SyncService's patient mutex and singleton remote state store. */
internal class PatientRetrievalCoordinator(
    private val patientSyncMutex: Mutex,
    private val context: Context,
    private val telegram: TelegramClient,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val doctorRepository: DoctorRepository,
    private val syncStateRepository: SyncStateRepository,
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val auditRepository: AuditRepository,
    private val dispatchers: DispatcherProvider,
    private val remoteState: RemoteSyncStateStore,
    private val downloadUndo: LocalDownloadUndo
) {

    suspend fun fetchLatestCsv(): Result<CsvFetchResult> = patientSyncMutex.withLock {
        fetchCsv(previous = false)
    }

    suspend fun fetchPreviousCsv(): Result<CsvFetchResult> = patientSyncMutex.withLock {
        fetchCsv(previous = true)
    }

    suspend fun describeLocalChanges(shiftId: String): HandoverChangeSummary =
        withContext(dispatchers.io) {
            val current = patientRepository.getForShift(shiftId).associateBy(Patient::id)
            val base = settingsRepository.get(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + shiftId)
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
                val beforeDownload = prepared.map { item ->
                    shiftRepository.getByDate(item.parsed.shiftDate)?.let { shift ->
                        ShiftCsvSnapshot(shift, patientRepository.getForShift(shift.id))
                    }
                }
                val downloaded = prepared.map { preparedShift ->
                    applyPreparedShift(preparedShift).also { appliedShift ->
                        // Restoring an older bundle must not redefine the last known current publication.
                        if (!previous) settingsRepository.put(
                            AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + appliedShift.shift.id,
                            CsvCodec.encode(appliedShift.shift, appliedShift.patients))
                        shiftRepository.updateCsvMessageId(appliedShift.shift.id, snapshot.messageId)
                        settingsRepository.put(AppConstants.SETTING_PATIENT_DOWNLOAD_AT_PREFIX + appliedShift.shift.id,
                            java.time.Instant.now().toEpochMilli().toString())
                        settingsRepository.put(AppConstants.SETTING_PATIENT_DOWNLOAD_MESSAGE_PREFIX + appliedShift.shift.id,
                            snapshot.messageId.toString())
                    }
                }
                downloadUndo.record(beforeDownload, downloaded.map { it.shift.id })
                downloaded
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

    private fun sameEditableShift(left: Shift?, right: Shift?): Boolean {
        if (left == null || right == null) return left == right
        return left.id == right.id &&
            left.date == right.date &&
            left.doctorIds == right.doctorIds &&
            left.multiDoctorMode == right.multiDoctorMode &&
            left.sortSpecJson == right.sortSpecJson
    }
}
