package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.data.model.PatientTaskCodec
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.room.withTransaction
import com.hos.rushdpatients.domain.patient.PatientDoctorAssignments
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.util.DispatcherProvider
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Local clinical mutations, their pending-sync marker, and audit records commit together. */
@Singleton
class WardMutationRepository @Inject constructor(
    private val database: AppDatabase,
    private val patients: PatientRepository,
    private val doctors: DoctorRepository,
    private val shifts: ShiftRepository,
    private val settings: SettingsRepository,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider
) {
    private suspend fun <T> mutate(shiftId: String, block: suspend () -> T): T =
        withContext(dispatchers.io) {
            database.withTransaction {
                require(shifts.getById(shiftId)?.date == ShiftDate.current()) {
                    "هذه مناوبة محفوظة للعرض والاستعادة فقط ولا يمكن تعديلها"
                }
                val result = block()
                settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true)
                result
            }
        }

    suspend fun addPatient(patient: Patient, shiftId: String, actor: Session?,
        position: Int? = null, expectedPatients: List<Patient>? = null, expectedShiftRevision: Long? = null) =
        mutate(shiftId) {
            require(patients.getById(patient.id) == null) {
                "المريض موجود بالفعل؛ أعد مراجعة القائمة قبل الإضافة"
            }
            require(patients.countForShift(shiftId) < AppConstants.MAX_PATIENTS_PER_SHIFT) {
                "تم الوصول إلى الحد الأقصى لعدد المرضى"
            }
            val stored = patients.getForShift(shiftId)
            val storedOrders = stored.associate { it.id to it.sortOrder }
            val current = if (position == null) stored.sortedBy { it.sortOrder } else PatientComparators.ordered(
                stored, SortSpecCodec.decode(shifts.getById(shiftId)?.sortSpecJson),
                doctors.getAllIncludingDeleted().associate { it.id to it.fullName })
            if (position != null) {
                val shift = requireNotNull(shifts.getById(shiftId))
                require(shift.revision == expectedShiftRevision && expectedPatients != null &&
                    current.map { Triple(it.id, it.revision, it.sortOrder) } ==
                        expectedPatients.sortedBy { it.sortOrder }.map { Triple(it.id, it.revision, it.sortOrder) }) {
                    "تغيرت قائمة المرضى؛ أعد فتح الإدراج وراجع الموضع"
                }
                require(position in 1..current.size + 1) { "موضع المريض خارج القائمة" }
                val priorityCount = current.count { it.isPriority }
                require(if (patient.isPriority) position <= priorityCount + 1 else position > priorityCount) {
                    "مرضى الأولوية أولاً؛ اختر موضعاً ضمن مجموعة أولوية المريض"
                }
                shifts.updateSortSpec(shiftId, SortSpecCodec.encode(SortSpec.EMPTY), shift.revision)
            }
            val order = position ?: ((current.maxOfOrNull { it.sortOrder } ?: 0) + 1)
            val prepared = prepareTasks(patient, null, actor)
            val added = prepared.copy(
                sortOrder = order,
                revision = 1,
                lastEditedByDoctorId = actor?.doctorId,
                lastEditedByName = actor?.doctorName
            )
            patients.upsert(added, shiftId)
            if (position != null) {
                current.toMutableList().apply { add(position - 1, added) }.forEachIndexed { index, row ->
                    if (row.id != added.id && storedOrders[row.id] != index + 1)
                        patients.updateOptimistically(row.copy(sortOrder = index + 1), shiftId, row.revision)
                }
            }
            recordPatientChange(
                actor, AppConstants.AUDIT_PATIENT_ADDED, null,
                requireNotNull(patients.getById(added.id))
            )
        }

    suspend fun updatePatient(patient: Patient, shiftId: String, actor: Session?): Patient =
        mutate(shiftId) {
            val before = patients.getById(patient.id)
            val updated = patients.updateOptimistically(
                prepareTasks(patient, before, actor).copy(
                    lastEditedByDoctorId = actor?.doctorId,
                    lastEditedByName = actor?.doctorName
                ),
                shiftId,
                patient.revision
            )
            recordPatientChange(actor, AppConstants.AUDIT_PATIENT_EDITED, before, updated)
            updated
        }

    suspend fun deletePatient(patient: Patient, shiftId: String, actor: Session?): Patient =
        mutate(shiftId) {
            val before = patients.getById(patient.id)
            val deleted = patients.softDeleteOptimistically(patient.id, shiftId, patient.revision)
            recordPatientChange(actor, AppConstants.AUDIT_PATIENT_DELETED, before, null)
            deleted
        }

    suspend fun restorePatient(patient: Patient, shiftId: String, actor: Session?): Patient =
        mutate(shiftId) {
            val before = patients.getById(patient.id)
            val restored = patients.restoreOptimistically(patient.id, shiftId, patient.revision)
            PatientDoctorAssignments.requireValid(restored, doctors.getAll().associateBy { it.id })
            recordPatientChange(actor, AppConstants.AUDIT_PATIENT_RESTORED, before, restored)
            restored
        }

    suspend fun rollover(
        patientsToInsert: List<Patient>,
        shiftId: String,
        expectedRevision: Long,
        decisions: List<Pair<Patient, String>>,
        actor: Session?
    ) = mutate(shiftId) {
        require(decisions.isNotEmpty() && patientsToInsert.size == decisions.size) { "يجب نسخ المناوبة كاملة" }
        val sourceIds = decisions.map { it.first.id }.toSet()
        require(sourceIds.size == decisions.size) { "سجلات المصدر مكررة" }
        val sourceShiftIds = decisions.map { patients.getShiftId(it.first.id) }.distinct()
        val sourceShiftId = sourceShiftIds.singleOrNull()
        require(sourceShiftId != null && sourceShiftId != shiftId &&
            patients.getForShift(sourceShiftId).map { it.id }.toSet() == sourceIds) {
            "تغيرت قائمة مرضى المناوبة السابقة؛ أعد فتح النسخ"
        }
        require(requireNotNull(shifts.getById(sourceShiftId)).date < requireNotNull(shifts.getById(shiftId)).date) {
            "يجب أن تكون مناوبة المصدر أقدم من مناوبة اليوم"
        }
        decisions.forEach { (source, _) ->
            val current = patients.getById(source.id)
            require(current != null && !current.isDeleted && current.revision == source.revision) {
                "تغيرت بيانات المناوبة السابقة؛ أعد فتح نسخ المناوبة"
            }
        }
        val target = requireNotNull(shifts.getById(shiftId))
        require(target.revision == expectedRevision) { "تغيرت مناوبة اليوم؛ أعد فتح نسخ المناوبة" }
        require(patientsToInsert.map { it.id }.toSet().size == patientsToInsert.size &&
            patientsToInsert.all { patients.getById(it.id) == null }) { "معرّفات النسخة الجديدة مستخدمة؛ أعد فتح النسخ" }
        val registry = doctors.getAll().associateBy { it.id }
        patientsToInsert.forEach { PatientDoctorAssignments.requireValid(it, registry) }
        patients.insertRolloverIfShiftEmpty(patientsToInsert, shiftId)
        shifts.updateSortSpec(shiftId, SortSpecCodec.encode(SortSpec.EMPTY), target.revision)
        decisions.forEach { (patient, decision) ->
            audit.record(
                actor?.doctorId, actor?.doctorName, "rollover_$decision",
                patient.name, patientId = patient.id
            )
        }
    }

    suspend fun setShiftDoctors(
        shiftId: String,
        doctorIds: List<String>,
        expectedRevision: Long,
        actor: Session?
    ) = mutate(shiftId) {
        require(doctorIds.isNotEmpty() && doctorIds.distinct().size == doctorIds.size) {
            "اختر طبيباً واحداً على الأقل دون تكرار"
        }
        doctorIds.forEach { require(doctors.getActiveById(it) != null) { "أحد الأطباء لم يعد نشطاً" } }
        val before = requireNotNull(shifts.getById(shiftId))
        shifts.updateDoctorIds(shiftId, doctorIds, expectedRevision)
        audit.record(
            actor?.doctorId, actor?.doctorName, "shift_doctors_edited", shiftId,
            beforeValue = before.doctorIds.joinToString(","),
            afterValue = doctorIds.joinToString(",")
        )
    }

    suspend fun setShiftSort(
        shiftId: String,
        sortSpecJson: String?,
        expectedRevision: Long,
        actor: Session?
    ) = mutate(shiftId) {
        val before = requireNotNull(shifts.getById(shiftId))
        shifts.updateSortSpec(shiftId, sortSpecJson, expectedRevision)
        val current = patients.getForShift(shiftId)
        val ordered = PatientComparators.ordered(
            current, SortSpecCodec.decode(sortSpecJson),
            doctors.getAllIncludingDeleted().associate { it.id to it.fullName }
        )
        val previousOrders = current.associate { it.id to it.sortOrder }
        ordered.filter { previousOrders[it.id] != it.sortOrder }.forEach { patient ->
            patients.updateOptimistically(patient, shiftId, patient.revision)
        }
        audit.record(
            actor?.doctorId, actor?.doctorName, "shift_sort_edited", shiftId,
            beforeValue = before.sortSpecJson,
            afterValue = sortSpecJson
        )
    }

    suspend fun movePatientToTop(patient: Patient, shiftId: String, expectedShiftRevision: Long, actor: Session?) = mutate(shiftId) {
        val shift = requireNotNull(shifts.getById(shiftId))
        require(shift.revision == expectedShiftRevision) { "تغير ترتيب المناوبة؛ أعد المحاولة" }
        val rows = PatientComparators.ordered(patients.getForShift(shiftId), SortSpecCodec.decode(shift.sortSpecJson),
            doctors.getAllIncludingDeleted().associate { it.id to it.fullName })
        val saved = rows.firstOrNull { it.id == patient.id } ?: throw StalePatientEditException()
        if (saved.revision != patient.revision) throw StalePatientEditException()
        val rest = rows.filterNot { it.id == saved.id }
        // Keep the established priority section first; move to the start of the patient's section.
        val ordered = if (saved.isPriority) listOf(saved) + rest
            else rest.filter { it.isPriority } + saved + rest.filterNot { it.isPriority }
        shifts.updateSortSpec(shiftId, SortSpecCodec.encode(SortSpec.EMPTY), shift.revision)
        ordered.forEachIndexed { index, row ->
            if (row.sortOrder != index + 1) patients.updateOptimistically(row.copy(sortOrder = index + 1), shiftId, row.revision)
        }
        audit.record(actor?.doctorId, actor?.doctorName, "patient_moved_to_top", patientId = saved.id)
    }

    private suspend fun prepareTasks(patient: Patient, before: Patient?, actor: Session?): Patient {
        PatientDoctorAssignments.requireValid(patient, doctors.getAll().associateBy { it.id })
        PatientTasks.requireValid(patient.tasks, requireCompletion = false)
        val previous = before?.tasks.orEmpty().associateBy { it.id }
        val tasks = patient.tasks.map { draft ->
            val old = previous[draft.id]
            val task = draft
            when {
                !task.done -> task.copy(description = task.description.trim(), completedByDoctorId = null,
                    completedByName = null, completedAtEpochMillis = null)
                old?.done == true -> task.copy(description = task.description.trim(),
                    completedByDoctorId = old.completedByDoctorId, completedByName = old.completedByName,
                    completedAtEpochMillis = old.completedAtEpochMillis)
                else -> {
                    val completer = actor?.doctorId?.let { doctors.getById(it) }
                    require(completer != null && !completer.isDeleted) { "سجّل الدخول لإتمام المهمة" }
                    task.copy(description = task.description.trim(), completedByDoctorId = completer.id,
                        completedByName = completer.fullName, completedAtEpochMillis = java.time.Instant.now().toEpochMilli())
                }
            }
        }
        PatientTasks.requireValid(tasks)
        return patient.copy(tasks = tasks)
    }

    private suspend fun recordPatientChange(
        actor: Session?, action: String, before: Patient?, after: Patient?
    ) {
        audit.record(
            actorDoctorId = actor?.doctorId,
            actorName = actor?.doctorName,
            action = action,
            detail = after?.name ?: before?.name.orEmpty(),
            patientId = after?.id ?: before?.id,
            beforeValue = before?.let(::patientHistorySummary),
            afterValue = after?.let(::patientHistorySummary)
        )
    }

    private fun patientHistorySummary(patient: Patient): String = buildString {
        append("name=").append(patient.name)
        append(";diagnosis=").append(patient.initialDiagnosis)
        append(";plan=").append(patient.treatmentPlan)
        append(";followUp=").append(patient.followUp)
        append(";labs=").append(patient.labs)
        append(";tasks=").append(PatientTaskCodec.encode(patient.tasks))
        append(";resident=").append(patient.responsibleResidentId.orEmpty())
        append(";supervisor=").append(patient.responsibleSpecialistId.orEmpty())
        append(";badges=").append(patient.badges.joinToString("|") { badge ->
            "${badge.priority?.code.orEmpty()}:${badge.text}"
        })
        append(";priority=").append(patient.isPriority)
        append(";revision=").append(patient.revision)
    }
}
