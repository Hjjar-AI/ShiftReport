package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.data.model.PatientTaskCodec
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpecCodec
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

    suspend fun addPatient(patient: Patient, shiftId: String, actor: Session?) =
        mutate(shiftId) {
            require(patients.getById(patient.id) == null) {
                "المريض موجود بالفعل؛ أعد مراجعة القائمة قبل الإضافة"
            }
            require(patients.countForShift(shiftId) < AppConstants.MAX_PATIENTS_PER_SHIFT) {
                "تم الوصول إلى الحد الأقصى لعدد المرضى"
            }
            val order = (patients.getForShift(shiftId).maxOfOrNull { it.sortOrder } ?: 0) + 1
            val prepared = prepareTasks(patient, null, actor)
            val added = prepared.copy(
                sortOrder = order,
                revision = 1,
                lastEditedByDoctorId = actor?.doctorId,
                lastEditedByName = actor?.doctorName
            )
            patients.upsert(added, shiftId)
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
            recordPatientChange(actor, AppConstants.AUDIT_PATIENT_RESTORED, before, restored)
            restored
        }

    suspend fun rollover(
        patientsToInsert: List<Patient>,
        shiftId: String,
        decisions: List<Pair<Patient, String>>,
        actor: Session?
    ) = mutate(shiftId) {
        patients.insertRolloverIfShiftEmpty(patientsToInsert, shiftId)
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

    private suspend fun prepareTasks(patient: Patient, before: Patient?, actor: Session?): Patient {
        PatientTasks.requireValid(patient.tasks, requireCompletion = false)
        val previous = before?.tasks.orEmpty().associateBy { it.id }
        val tasks = patient.tasks.map { draft ->
            val old = previous[draft.id]
            val owner = draft.ownerDoctorId?.let { doctors.getById(it) }
            if (draft.ownerDoctorId != null && (!draft.done || old?.ownerDoctorId != draft.ownerDoctorId)) {
                require(owner != null && !owner.isDeleted) { "مسؤول المهمة غير موجود أو غير فعال" }
            }
            val task = draft.copy(ownerName = if (draft.ownerDoctorId == null) null else owner?.fullName ?: old?.ownerName)
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
