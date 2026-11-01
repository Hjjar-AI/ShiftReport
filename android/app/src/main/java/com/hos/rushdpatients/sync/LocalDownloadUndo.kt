package com.hos.rushdpatients.sync

import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.domain.patient.PatientDoctorAssignments
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.db.AppDatabase
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
    private val doctors: DoctorRepository,
    private val settings: SettingsRepository,
    private val dispatchers: DispatcherProvider,
    private val json: Json
) {
    private val key = AppConstants.SETTING_PATIENT_DOWNLOAD_UNDO
    val available = settings.observe(key).map { it != null }

    // Called in the same transaction as the download application.
    suspend fun record(before: List<ShiftCsvSnapshot?>, appliedShiftIds: List<String>, previousCurrentShiftId: String? = null,
        beforeDoctors: List<Doctor>, afterDoctors: List<Doctor>) {
        val entries = appliedShiftIds.mapIndexed { index, id ->
            val shift = requireNotNull(shifts.getById(id))
            val after = CsvCodec.encode(shift, patients.getForShift(id))
            buildJsonObject {
                put("id", id)
                previousCurrentShiftId?.let { put("previousCurrentId", it) }
                put("after", after)
                put("before", before[index]?.let { JsonPrimitive(CsvCodec.encode(it.shift, it.patients)) } ?: JsonNull)
            }
        }
        val registry = buildJsonObject {
            put("beforeDoctors", registryData(beforeDoctors))
            put("afterDoctors", registryData(afterDoctors))
            previousCurrentShiftId?.let { put("previousCurrentId", it) }
        }
        settings.put(key, JsonArray(entries + registry).toString())
    }

    private fun registryData(rows: List<Doctor>) = DoctorsRegistryCodec.encode(rows.sortedBy { it.id })

    // Standalone doctor Fetch shares the same one-step local Undo; it never republishes.
    suspend fun recordRegistry(before: List<Doctor>, after: List<Doctor>) {
        val current = shifts.getLatest()
        record(emptyList(), emptyList(), current?.id, before, after)
    }

    suspend fun undo(): String = withContext(dispatchers.io) {
        database.withTransaction {
            val entries = json.parseToJsonElement(requireNotNull(settings.get(key)) { "لا يوجد تنزيل للتراجع عنه" }).jsonArray
            val registry = entries.single { "beforeDoctors" in it.jsonObject }.jsonObject
            require(registryData(doctors.getAll()) == registry.getValue("afterDoctors").jsonPrimitive.content) {
                "تغير سجل الأطباء منذ الجلب؛ لن يستبدل التراجع تعديلات لاحقة"
            }
            val restoredDoctors = DoctorsRegistryCodec.decode(registry.getValue("beforeDoctors").jsonPrimitive.content).doctors
            val doctorsById = restoredDoctors.associateBy { it.id }
            val clinical = entries.filter { "id" in it.jsonObject }
            // Validate every restored assignment and affected shift before writing anything.
            clinical.forEach { element ->
                val entry = element.jsonObject
                val id = entry.getValue("id").jsonPrimitive.content
                val shift = requireNotNull(shifts.getById(id)) { "تغير القسم منذ التنزيل" }
                require(CsvCodec.encode(shift, patients.getForShift(id)) == entry.getValue("after").jsonPrimitive.content) {
                    "تغيرت البيانات منذ التنزيل؛ لن يستبدل التراجع تعديلات أو نشراً لاحقاً"
                }
                val saved = entry["before"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.let(CsvCodec::decode)
                require(saved?.doctorIds.orEmpty().all(doctorsById::containsKey)) { "تعذر استعادة أطباء القسم" }
                saved?.patients.orEmpty().forEach { patient ->
                    PatientDoctorAssignments.requireValid(patient, doctorsById)
                    require(patients.getShiftId(patient.id)?.let { it == id } != false) { "تغير موقع سجل مريض؛ أوقف التراجع" }
                }
            }
            var restoredId = ""
            doctors.applyPublishedRegistry(restoredDoctors) {
                clinical.forEach { element ->
                    val entry = element.jsonObject
                    val id = entry.getValue("id").jsonPrimitive.content
                    val current = requireNotNull(shifts.getById(id))
                    val saved = entry["before"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.let(CsvCodec::decode)
                    val rows = saved?.patients.orEmpty()
                    patients.softDeleteMissingFromShift(id, rows.map(Patient::id))
                    rows.forEach { patient ->
                        val latest = patients.getById(patient.id)
                        patients.upsert(patient.copy(revision = maxOf(patient.revision, latest?.revision ?: 0) + 1), id)
                    }
                    // Delivery/publication evidence remains; restored rows are local edits.
                    shifts.upsert(current.copy(date = saved?.shiftDate ?: current.date,
                        doctorIds = saved?.doctorIds.orEmpty(),
                        multiDoctorMode = saved?.doctorIds.orEmpty().size > 1,
                        sortSpecJson = saved?.sortSpecJson ?: SortSpecCodec.encode(SortSpec.EMPTY),
                        revision = current.revision + 1))
                }
                val previousId = registry["previousCurrentId"]?.jsonPrimitive?.content
                restoredId = previousId?.takeIf { shifts.getById(it) != null }
                    ?: clinical.firstOrNull()?.jsonObject?.getValue("id")?.jsonPrimitive?.content
                    ?: shifts.getLatest()?.id ?: error("لا يوجد قسم محلي للاستعادة")
                shifts.selectCurrent(restoredId)
            }
            settings.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING,
                registryData(restoredDoctors) != settings.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY))
            settings.delete(key)
            restoredId
        }
    }
}
