package com.hos.rushdpatients.migration

import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.sync.CsvCodec
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VbaCsvImporter @Inject constructor(
    private val database: AppDatabase,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider
) {

    /**
     * Analyse a VBA-exported CSV without writing anything.
     *
     * A row is considered "unchanged" only when every field the editor can
     * mutate is identical to the stored patient. sortOrder is included so a
     * re-import after a CSV regeneration updates the badge number rather than
     * silently skipping the row.
     */
    suspend fun preview(file: File): VbaImportPreview = withContext(dispatchers.io) {
        val text = file.readText(Charsets.UTF_8)
        val parsed = CsvCodec.decode(text)
        val warnings = mutableListOf<String>()

        if (parsed.patients.isEmpty()) {
            warnings += "الملف لا يحتوي على مرضى"
        }
        if (parsed.shiftId == null) {
            warnings += "لا يوجد معرف للوردية — سيتم إنشاء واحد جديد"
        }

        val existingShift = shiftRepository.getByDate(parsed.shiftDate)
        val existing = existingShift
            ?.let { patientRepository.getForShift(it.id).associateBy(Patient::id) }
            ?: emptyMap()

        val newOnes = mutableListOf<Patient>()
        val updated = mutableListOf<Patient>()
        val unchanged = mutableListOf<Patient>()

        parsed.patients.forEach { p ->
            val current = existing[p.id]
            when {
                current == null -> newOnes += p
                current.copy(updatedAt = p.updatedAt, deletedAt = p.deletedAt) == p -> unchanged += p
                else -> updated += p
            }
        }

        VbaImportPreview(
            fileName = file.name,
            shiftDate = parsed.shiftDate,
            doctors = parsed.doctorIds,
            newPatients = newOnes,
            updatedPatients = updated,
            unchangedPatients = unchanged,
            warnings = warnings
        )
    }

    /**
     * Apply the previewed import. Upserts by external id.
     */
    suspend fun apply(
        preview: VbaImportPreview,
        mode: ImportMode = ImportMode.REPLACE
    ): ImportResult = withContext(dispatchers.io) {
        val toUpsert = preview.newPatients + preview.updatedPatients
        val retainedIds = (
            preview.newPatients + preview.updatedPatients + preview.unchangedPatients
        ).map(Patient::id)
        val retainedIdSet = retainedIds.toSet()
        database.withTransaction {
            val shift = shiftRepository.getOrCreateForDate(preview.shiftDate)
            val conflicting = retainedIds.firstOrNull { patientId ->
                patientRepository.getShiftId(patientId)?.let { ownerId -> ownerId != shift.id } == true
            }
            require(conflicting == null) {
                "معرّف المريض $conflicting مستخدم في مناوبة أخرى"
            }
            val removedCount = if (mode == ImportMode.REPLACE) {
                patientRepository.getForShift(shift.id).count { it.id !in retainedIdSet }
            } else {
                0
            }
            if (mode == ImportMode.REPLACE) {
                patientRepository.softDeleteMissingFromShift(shift.id, retainedIds)
            }
            if (toUpsert.isNotEmpty()) {
                // Upsert intentionally implements last-write-wins for matching patient IDs.
                patientRepository.upsertAll(toUpsert, shift.id)
            }
            if (preview.shiftDate == ShiftDate.current()) {
                settingsRepository.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true)
            }
            ImportResult(
                shiftId = shift.id,
                inserted = preview.newPatients.size,
                updated = preview.updatedPatients.size,
                skipped = preview.unchangedPatients.size,
                removed = removedCount,
                mode = mode
            )
        }
    }

    data class ImportResult(
        val shiftId: String,
        val inserted: Int,
        val updated: Int,
        val skipped: Int,
        val removed: Int,
        val mode: ImportMode
    )
}
