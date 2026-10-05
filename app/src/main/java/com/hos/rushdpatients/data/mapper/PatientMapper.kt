package com.hos.rushdpatients.data.mapper

import com.hos.rushdpatients.data.db.entity.PatientEntity
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientWarningFlag
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

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
        responsibleResidentId = patient.responsibleResidentId,
        responsibleSpecialistId = patient.responsibleSpecialistId,
        warningFlagsCsv = patient.warningFlags.sortedBy { it.ordinal }.joinToString(",") { it.code },
        warningDetailsEncoded = encodeWarningDetails(patient.warningDetails),
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
        responsibleResidentId = entity.responsibleResidentId,
        responsibleSpecialistId = entity.responsibleSpecialistId,
        warningFlags = entity.warningFlagsCsv.split(',')
            .mapNotNull(PatientWarningFlag::fromCode)
            .toSet(),
        warningDetails = decodeWarningDetails(entity.warningDetailsEncoded),
        isPriority = entity.isPriority,
        lastEditedByDoctorId = entity.lastEditedByDoctorId,
        lastEditedByName = entity.lastEditedByName,
        sortOrder = entity.sortOrder,
        revision = entity.revision,
        updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis),
        deletedAt = entity.deletedAtEpochMillis?.let(Instant::ofEpochMilli)
        )
    }

    private fun encodeWarningDetails(details: Map<PatientWarningFlag, String>): String =
        details.entries.joinToString(";") { (flag, detail) ->
            "${flag.code}:${Base64.getUrlEncoder().withoutPadding().encodeToString(detail.toByteArray())}"
        }

    private fun decodeWarningDetails(value: String): Map<PatientWarningFlag, String> =
        value.split(';').mapNotNull { item ->
            val separator = item.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val flag = PatientWarningFlag.fromCode(item.substring(0, separator)) ?: return@mapNotNull null
            val detail = runCatching {
                String(Base64.getUrlDecoder().decode(item.substring(separator + 1)))
            }.getOrNull() ?: return@mapNotNull null
            flag to detail
        }.toMap()
}
