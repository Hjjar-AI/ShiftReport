package com.hos.rushdpatients.data.mapper

import com.hos.rushdpatients.data.model.PatientTaskCodec
import com.hos.rushdpatients.data.db.entity.PatientEntity
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadgeCodec
import java.time.Instant
import java.time.LocalDate

object PatientMapper {

    fun toEntity(patient: Patient, shiftId: String): PatientEntity = PatientEntity(
        id = patient.id,
        shiftId = shiftId,
        admittanceNumber = patient.admittanceNumber,
        admittanceDateEpochDay = patient.admittanceDate?.toEpochDay(),
        gender = patient.gender.code,
        name = patient.name,
        birthDateEpochDay = patient.birthDate?.toEpochDay(),
        hasCompanion = patient.hasCompanion,
        diagnosisType = patient.diagnosisType.code,
        initialDiagnosis = patient.initialDiagnosis,
        treatmentPlan = patient.treatmentPlan,
        followUp = patient.followUp,
        labs = patient.labs,
        tasksJson = PatientTaskCodec.encode(patient.tasks),
        responsibleResidentId = patient.responsibleResidentId,
        responsibleSpecialistId = patient.responsibleSpecialistId,
        // Existing columns are intentionally reused so a fresh database does not need
        // another schema solely for the free-text badge feature.
        warningFlagsCsv = patient.badges.mapNotNull { it.priority?.code }.joinToString("|"),
        warningDetailsEncoded = PatientBadgeCodec.encode(patient.badges),
        isPriority = patient.isPriority,
        lastEditedByDoctorId = patient.lastEditedByDoctorId,
        lastEditedByName = patient.lastEditedByName,
        sortOrder = patient.sortOrder,
        revision = patient.revision,
        updatedAtEpochMillis = patient.updatedAt.toEpochMilli(),
        deletedAtEpochMillis = patient.deletedAt?.toEpochMilli()
    )

    fun fromEntity(entity: PatientEntity): Patient {
        return Patient(
        id = entity.id,
        admittanceNumber = entity.admittanceNumber,
        admittanceDate = entity.admittanceDateEpochDay?.let(LocalDate::ofEpochDay),
        gender = Gender.fromCode(entity.gender),
        name = entity.name,
        birthDate = entity.birthDateEpochDay?.let(LocalDate::ofEpochDay),
        hasCompanion = entity.hasCompanion,
        diagnosisType = DiagnosisType.fromCode(entity.diagnosisType),
        initialDiagnosis = entity.initialDiagnosis,
        treatmentPlan = entity.treatmentPlan,
        followUp = entity.followUp,
        labs = entity.labs,
        tasks = PatientTaskCodec.decode(entity.tasksJson),
        responsibleResidentId = entity.responsibleResidentId,
        responsibleSpecialistId = entity.responsibleSpecialistId,
        badges = PatientBadgeCodec.decode(entity.warningDetailsEncoded, entity.warningFlagsCsv),
        isPriority = entity.isPriority,
        lastEditedByDoctorId = entity.lastEditedByDoctorId,
        lastEditedByName = entity.lastEditedByName,
        sortOrder = entity.sortOrder,
        revision = entity.revision,
        updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis),
        deletedAt = entity.deletedAtEpochMillis?.let(Instant::ofEpochMilli)
        )
    }

}
