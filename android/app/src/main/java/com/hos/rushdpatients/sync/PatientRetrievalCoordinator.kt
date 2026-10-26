package com.hos.rushdpatients.sync

import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.R
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
    private val downloadUndo: LocalDownloadUndo,
    private val doctorSync: DoctorSyncCoordinator,
    private val publicationStatus: PatientPublicationStatus
) {

    suspend fun fetchLatestCsv(replaceLocal: Boolean = false, allowOlder: Boolean = false): Result<CsvFetchResult> = patientSyncMutex.withLock {
        fetchCsv(replaceLocal = replaceLocal, allowOlder = allowOlder)
    }

    suspend fun describeLocalChanges(shiftId: String): HandoverChangeSummary =
        withContext(dispatchers.io) {
            val changes = KnownShiftSnapshot.changes(shiftRepository.getById(shiftId),
                patientRepository.getForShift(shiftId),
                settingsRepository.get(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + shiftId),
                doctorRepository.getAllIncludingDeleted().associate { it.id to it.fullName })
            HandoverChangeSummary(changes.added.map { it.displayName }, changes.changed.map { it.displayName },
                changes.removed.map { it.displayName }, changes.settingsChanged)
        }

    private suspend fun fetchCsv(replaceLocal: Boolean = false, allowOlder: Boolean = false): Result<CsvFetchResult> =
        withContext(dispatchers.io) {
            try {
                if (!replaceLocal) {
                    if (publicationStatus.hasPending()) {
                        throw LocalPatientChangesPendingException()
                    }
                }
                val state = remoteState.readState()
                    ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.patients_publication_absent)))
                if (allowOlder) doctorSync.requirePublishedAdmin(state)
                val snapshot = state.currentCsvSnapshot()
                if (snapshot == null) {
                    val message = context.getString(R.string.patients_publication_absent)
                    return@withContext Result.failure(IllegalStateException(message))
                }
                val lastKnownMessageId = syncStateRepository.csvMessageHighWater()
                if (!allowOlder && snapshot.messageId < lastKnownMessageId) throw OlderRemoteSnapshotException()


                val guardedShift = shiftRepository.getLatest()
                val guardedPatients = guardedShift
                    ?.let { patientRepository.getForShift(it.id) }
                    .orEmpty()

                val remote = telegram.getFile(snapshot.fileId)
                val path = remote.filePath
                    ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.sync_file_path_unavailable)))

                val local = File.createTempFile("remote_shift_", ".csv", context.cacheDir)
                val parsedShifts = try {
                    telegram.downloadFile(path, local)
                    CsvBundleCodec.decode(local.readText(Charsets.UTF_8)).take(1)
                } finally {
                    local.delete()
                }

                val activeDoctors = doctorRepository.getAll()
                val doctorsById = buildMap {
                    activeDoctors.forEach { doctor ->
                        put(doctor.id, doctor)
                        doctor.options.syncAliases.asSequence()
                            .filter { it.isNotBlank() }
                            .forEach { alias -> put(alias, doctor) }
                    }
                }
                val prepared = parsedShifts.map { parsed ->
                    prepareShift(parsed, activeDoctors, doctorsById)
                }
                val overrideActor = if (allowOlder) doctorSync.requirePublishedAdmin(requireNotNull(remoteState.readState())) else null
                val applied = database.withTransaction {
                    val currentShift = shiftRepository.getLatest()
                    val currentPatients = currentShift
                        ?.let { patientRepository.getForShift(it.id) }
                        .orEmpty()
                    require(
                        (replaceLocal || !publicationStatus.hasPending()) &&
                        sameEditableShift(currentShift, guardedShift) &&
                            samePublishedPatients(currentPatients, guardedPatients)
                    ) {
                        context.getString(R.string.patients_local_changed_download)
                    }

                    val beforeDownload = prepared.map { item ->
                        shiftRepository.getByDate(item.parsed.shiftDate)?.let { shift ->
                            ShiftCsvSnapshot(shift, patientRepository.getForShift(shift.id))
                        }
                    }
                    val downloaded = prepared.map { preparedShift ->
                        applyPreparedShift(preparedShift).also { appliedShift ->
                            settingsRepository.put(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + appliedShift.shift.id,
                                CsvCodec.encode(appliedShift.shift, appliedShift.patients))
                            settingsRepository.put(AppConstants.SETTING_PATIENT_PUBLISHED_AT_PREFIX + appliedShift.shift.id,
                                snapshot.updatedAt.toString())

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
                settingsRepository.pruneShiftSyncCache(shiftRepository.getRecent(12).map { it.id }.toSet() + applied.map { it.shift.id })
                val latest = applied.first()
                val inserted = applied.sumOf { it.inserted }
                val updated = applied.sumOf { it.updated }

                syncStateRepository.update(
                    channel = SyncChannel.CSV,
                    offset = snapshot.updatedAt,
                    messageId = snapshot.messageId,
                    fileId = snapshot.fileId
                )

                auditRepository.record(
                    actorDoctorId = overrideActor?.id,
                    actorName = overrideActor?.fullName,
                    action = AppConstants.AUDIT_CSV_DOWNLOADED,
                    detail = "shifts=${applied.size} latest=${latest.shift.date} " +
                        "source=latest forcedOlder=$allowOlder inserted=$inserted updated=$updated"
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
                        context.getString(R.string.patient_resident_missing, patient.displayName)
                    !patient.responsibleSpecialistId.isNullOrBlank() && specialist == null ->
                        context.getString(R.string.patient_specialist_missing, patient.displayName)
                    resident != null && !resident.clinicalRole.canBeResident() ->
                        context.getString(R.string.patient_resident_role_invalid, patient.displayName)
                    specialist != null && !specialist.clinicalRole.canBeSupervisor() ->
                        context.getString(R.string.patient_specialist_role_invalid, patient.displayName)
                    else -> null
                }
                is PatientValidationResult.Invalid ->
                    context.getString(R.string.patient_download_content_invalid, patient.displayName, result.errors.joinToString())
            }
        }
        require(validationErrors.isEmpty()) { validationErrors.joinToString("\n") }
        require(normalizedPatients.size <= AppConstants.MAX_PATIENTS_PER_SHIFT) {
            context.getString(R.string.patients_download_count_invalid, parsed.shiftDate)
        }
        require(normalizedDoctorIds.all { id -> activeDoctors.any { it.id == id } }) {
            context.getString(R.string.patients_download_roster_unknown, parsed.shiftDate)
        }
        require(normalizedDoctorIds.distinct().size >= AppConstants.MIN_SHIFT_DOCTORS
        ) { context.getString(R.string.patients_download_roster_count_invalid, parsed.shiftDate) }
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
        val incoming = prepared.patients.mapIndexed { index, patient -> patient.copy(sortOrder = index + 1) }
        val existingOrder = PatientComparators.ordered(existing.values.toList(),
            SortSpecCodec.decode(existingShift?.sortSpecJson),
            doctorRepository.getAllIncludingDeleted().associate { it.id to it.fullName })
        val changedIds = KnownShiftSnapshot.changes(incoming, existingOrder).changed.mapTo(mutableSetOf()) { it.id }
        val toUpsert = incoming.map { patient ->
            val previous = existing[patient.id]
            if (previous != null && patient.id !in changedIds) previous.copy(sortOrder = patient.sortOrder)
            else {
                if (previous == null) inserted++ else updated++
                patient.copy(revision = maxOf(patient.revision, previous?.revision ?: 0L) + 1)
            }
        }
        val shift = existingShift
            ?: shiftRepository.getOrCreateForDate(parsed.shiftDate, parsed.shiftId)
        val conflicting = toUpsert.firstOrNull { patient ->
            patientRepository.getShiftId(patient.id)?.let { owner -> owner != shift.id } == true
        }
        require(conflicting == null) {
            context.getString(R.string.patients_download_identity_collision, conflicting?.id)
        }
        patientRepository.softDeleteMissingFromShift(shift.id, toUpsert.map(Patient::id))
        patientRepository.upsertAll(toUpsert.filter { row -> row != existing[row.id] }, shift.id)
        val updatedShift = shift.copy(
            doctorIds = prepared.doctorIds,
            sortSpecJson = parsed.sortSpecJson,
            revision = maxOf(shift.revision, parsed.shiftRevision) + 1,
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
