package com.hos.rushdpatients.data.repository

import androidx.room.withTransaction
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.db.AppDatabase
import com.hos.rushdpatients.data.model.Patient
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
            val added = patient.copy(
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
                patient.copy(
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
        audit.record(
            actor?.doctorId, actor?.doctorName, "shift_sort_edited", shiftId,
            beforeValue = before.sortSpecJson,
            afterValue = sortSpecJson
        )
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
        append(";resident=").append(patient.responsibleResidentId.orEmpty())
        append(";supervisor=").append(patient.responsibleSpecialistId.orEmpty())
        append(";badges=").append(patient.badges.joinToString("|") { badge ->
            "${badge.priority?.code.orEmpty()}:${badge.text}"
        })
        append(";priority=").append(patient.isPriority)
        append(";revision=").append(patient.revision)
    }
}
