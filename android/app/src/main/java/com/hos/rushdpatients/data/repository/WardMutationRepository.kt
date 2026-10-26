package com.hos.rushdpatients.data.repository

import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.room.withTransaction
import com.hos.rushdpatients.domain.patient.PatientAssignmentChange
import com.hos.rushdpatients.util.ShiftDate
import com.hos.rushdpatients.domain.patient.PatientDoctorAssignments
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.util.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Clinical last-save-wins mutations commit atomically; pending state derives from their content. */
@Singleton
class WardMutationRepository @Inject constructor(
    private val database: AppDatabase,
    private val patients: PatientRepository,
    private val doctors: DoctorRepository,
    private val shifts: ShiftRepository,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider
) {
    private suspend fun <T> mutate(shiftId: String, block: suspend () -> T): T =
        withContext(dispatchers.io) {
            database.withTransaction {
                require(shifts.getById(shiftId) != null) {
                    "قائمة المرضى لم تعد متاحة"
                }
                block()
            }
        }

    suspend fun addPatient(patient: Patient, shiftId: String,
        position: Int? = null) =
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
                require(position in 1..current.size + 1) { "موضع المريض خارج القائمة" }
                val priorityCount = current.count { it.isPriority }
                require(if (patient.isPriority) position <= priorityCount + 1 else position > priorityCount) {
                    "مرضى الأولوية أولاً؛ اختر موضعاً ضمن مجموعة أولوية المريض"
                }
                // Explicit placement switches to manual order inside this transaction.
                shifts.updateSortSpec(shiftId, SortSpecCodec.encode(SortSpec.EMPTY))
            }
            val order = position ?: ((current.maxOfOrNull { it.sortOrder } ?: 0) + 1)
            val prepared = PatientAssignmentChange.onSave(prepareTasks(patient), null, ShiftDate.current())
            val added = prepared.copy(
                sortOrder = order,
                revision = 1,
                lastEditedByDoctorId = null,
                lastEditedByName = null
            )
            patients.upsert(added, shiftId)
            if (position != null) {
                current.toMutableList().apply { add(position - 1, added) }.forEachIndexed { index, row ->
                    if (row.id != added.id && storedOrders[row.id] != index + 1)
                        patients.save(row.copy(sortOrder = index + 1), shiftId)
                }
            }
            added
        }

    suspend fun updatePatient(patient: Patient, shiftId: String): Patient =
        mutate(shiftId) {
            patients.save(
                prepareTasks(patient).copy(
                    lastEditedByDoctorId = null,
                    lastEditedByName = null
                ),
                shiftId
            )
        }

    suspend fun deletePatient(patient: Patient, shiftId: String): Patient =
        mutate(shiftId) {
            patients.softDelete(patient.id, shiftId)
        }

    suspend fun restorePatient(patient: Patient, shiftId: String): Patient =
        mutate(shiftId) {
            val restored = patients.restore(patient.id, shiftId)
            PatientDoctorAssignments.requireValid(restored, doctors.getAll().associateBy { it.id })
            restored
        }

    suspend fun setShiftDoctors(
        shiftId: String,
        doctorIds: List<String>,
        actor: Session?
    ) = mutate(shiftId) {
        require(doctorIds.isNotEmpty() && doctorIds.distinct().size == doctorIds.size) {
            "اختر طبيباً واحداً على الأقل دون تكرار"
        }
        doctorIds.forEach { require(doctors.getActiveById(it) != null) { "أحد الأطباء لم يعد نشطاً" } }
        val before = requireNotNull(shifts.getById(shiftId))
        shifts.updateDoctorIds(shiftId, doctorIds)
        audit.record(
            actor?.doctorId, actor?.doctorName, "shift_doctors_edited", shiftId,
            beforeValue = before.doctorIds.joinToString(","),
            afterValue = doctorIds.joinToString(",")
        )
    }

    suspend fun setShiftSort(
        shiftId: String,
        sortSpecJson: String?,
        actor: Session?
    ) = mutate(shiftId) {
        val before = requireNotNull(shifts.getById(shiftId))
        shifts.updateSortSpec(shiftId, sortSpecJson)
        val current = patients.getForShift(shiftId)
        val ordered = PatientComparators.ordered(
            current, SortSpecCodec.decode(sortSpecJson),
            doctors.getAllIncludingDeleted().associate { it.id to it.fullName }
        )
        val previousOrders = current.associate { it.id to it.sortOrder }
        ordered.filter { previousOrders[it.id] != it.sortOrder }.forEach { patient ->
            patients.save(patient, shiftId)
        }
        audit.record(
            actor?.doctorId, actor?.doctorName, "shift_sort_edited", shiftId,
            beforeValue = before.sortSpecJson,
            afterValue = sortSpecJson
        )
    }

    suspend fun movePatientToTop(patient: Patient, shiftId: String, actor: Session?) = mutate(shiftId) {
        val shift = requireNotNull(shifts.getById(shiftId))
        val rows = PatientComparators.ordered(patients.getForShift(shiftId), SortSpecCodec.decode(shift.sortSpecJson),
            doctors.getAllIncludingDeleted().associate { it.id to it.fullName })
        val saved = rows.firstOrNull { it.id == patient.id } ?: throw StalePatientEditException()
        val rest = rows.filterNot { it.id == saved.id }
        // Keep the established priority section first; move to the start of the patient's section.
        val ordered = if (saved.isPriority) listOf(saved) + rest
            else rest.filter { it.isPriority } + saved + rest.filterNot { it.isPriority }
        // Moving a row explicitly selects manual order; all renumbering commits atomically.
        shifts.updateSortSpec(shiftId, SortSpecCodec.encode(SortSpec.EMPTY))
        ordered.forEachIndexed { index, row ->
            if (row.sortOrder != index + 1) patients.save(row.copy(sortOrder = index + 1), shiftId)
        }
        audit.record(actor?.doctorId, actor?.doctorName, "patient_moved_to_top", patientId = saved.id)
    }

    private suspend fun prepareTasks(patient: Patient): Patient {
        PatientDoctorAssignments.requireValid(patient, doctors.getAll().associateBy { it.id })
        PatientTasks.requireValid(patient.tasks)
        val tasks = patient.tasks.map { task ->
            task.copy(description = task.description.trim(), completedByDoctorId = null,
                completedByName = null, completedAtEpochMillis = null)
        }
        PatientTasks.requireValid(tasks)
        return patient.copy(tasks = tasks)
    }

}
