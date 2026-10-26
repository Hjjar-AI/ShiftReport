package com.hos.rushdpatients.sync

import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/** One local rollback in encrypted Room settings, bound to the exact downloaded rows. */
@Singleton
class LocalDownloadUndo @Inject constructor(
    private val database: AppDatabase,
    private val shifts: ShiftRepository,
    private val patients: PatientRepository,
    private val settings: SettingsRepository,
    private val audit: AuditRepository,
    private val sessions: SessionManager,
    private val dispatchers: DispatcherProvider,
    private val json: Json
) {
    private val key = "local_patient_download_undo"
    val available = settings.observe(key).map { it != null }

    // Called in the same transaction as the download application.
    suspend fun record(before: List<ShiftCsvSnapshot?>, appliedShiftIds: List<String>) {
        val entries = appliedShiftIds.mapIndexed { index, id ->
            val shift = requireNotNull(shifts.getById(id))
            val after = CsvCodec.encode(shift, patients.getForShift(id))
            buildJsonObject {
                put("id", id)
                put("after", after)
                put("before", before[index]?.let { JsonPrimitive(CsvCodec.encode(it.shift, it.patients)) } ?: JsonNull)
            }
        }
        settings.put(key, JsonArray(entries).toString())
    }

    suspend fun undo(): String = withContext(dispatchers.io) {
        database.withTransaction {
            val entries = json.parseToJsonElement(requireNotNull(settings.get(key)) { "لا يوجد تنزيل للتراجع عنه" }).jsonArray
            require(entries.isNotEmpty()) { "لا يوجد تنزيل للتراجع عنه" }
            // Validate every affected shift before changing any row.
            entries.forEach { element ->
                val entry = element.jsonObject
                val id = entry.getValue("id").jsonPrimitive.content
                val shift = requireNotNull(shifts.getById(id)) { "تغيرت المناوبة منذ التنزيل" }
                require(CsvCodec.encode(shift, patients.getForShift(id)) == entry.getValue("after").jsonPrimitive.content) {
                    "تغيرت البيانات منذ التنزيل؛ لن يستبدل التراجع تعديلات أو نشراً لاحقاً"
                }
            }
            entries.forEach { element ->
                val entry = element.jsonObject
                val id = entry.getValue("id").jsonPrimitive.content
                val current = requireNotNull(shifts.getById(id))
                val saved = entry["before"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.let(CsvCodec::decode)
                val rows = saved?.patients.orEmpty()
                rows.forEach { patient ->
                    require(patients.getShiftId(patient.id)?.let { it == id } != false) { "تغير موقع سجل مريض؛ أوقف التراجع" }
                }
                patients.softDeleteMissingFromShift(id, rows.map(Patient::id))
                rows.forEach { patient ->
                    val latest = patients.getById(patient.id)
                    patients.upsert(patient.copy(revision = maxOf(patient.revision, latest?.revision ?: 0) + 1), id)
                }
                // Keep delivery/publication evidence; restored clinical rows are local edits.
                shifts.upsert(current.copy(doctorIds = saved?.doctorIds.orEmpty(),
                    multiDoctorMode = saved?.doctorIds.orEmpty().size > 1,
                    sortSpecJson = saved?.sortSpecJson ?: SortSpecCodec.encode(SortSpec.EMPTY),
                    revision = current.revision + 1))
            }

            settings.delete(key)
            val actor = sessions.current()
            audit.record(actor?.doctorId, actor?.doctorName, "download_undone", "shifts=${entries.size}")
            entries.first().jsonObject.getValue("id").jsonPrimitive.content
        }
    }
}
