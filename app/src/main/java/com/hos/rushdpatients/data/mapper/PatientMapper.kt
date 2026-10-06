package com.hos.rushdpatients.data.mapper

import com.hos.rushdpatients.data.db.entity.PatientEntity
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadgePriority
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
        // Existing columns are intentionally reused so a fresh database does not need
        // another schema solely for the free-text badge feature.
        warningFlagsCsv = patient.badgePriority?.code.orEmpty(),
        warningDetailsEncoded = encodeBadgeText(patient.badgeText),
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
        badgeText = decodeBadgeText(entity.warningDetailsEncoded, entity.warningFlagsCsv),
        badgePriority = decodeBadgePriority(entity.warningFlagsCsv),
        isPriority = entity.isPriority,
        lastEditedByDoctorId = entity.lastEditedByDoctorId,
        lastEditedByName = entity.lastEditedByName,
        sortOrder = entity.sortOrder,
        revision = entity.revision,
        updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis),
        deletedAt = entity.deletedAtEpochMillis?.let(Instant::ofEpochMilli)
        )
    }

    private fun encodeBadgeText(text: String): String = if (text.isBlank()) "" else {
        "badge:" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(text.trim().toByteArray(Charsets.UTF_8))
    }

    private fun decodeBadgePriority(value: String): PatientBadgePriority? {
        PatientBadgePriority.fromCode(value)?.let { return it }
        val legacy = value.split(',').mapNotNull(PatientWarningFlag::fromCode)
        return when {
            PatientWarningFlag.URGENT_REVIEW in legacy -> PatientBadgePriority.HIGH
            legacy.isNotEmpty() -> PatientBadgePriority.MEDIUM
            else -> null
        }
    }

    private fun decodeBadgeText(encoded: String, legacyFlags: String): String {
        if (encoded.startsWith("badge:")) {
            return runCatching {
                String(
                    Base64.getUrlDecoder().decode(encoded.removePrefix("badge:")),
                    Charsets.UTF_8
                )
            }.getOrDefault("")
        }
        val legacyDetails = encoded.split(';').firstNotNullOfOrNull { item ->
            val separator = item.indexOf(':')
            if (separator <= 0) return@firstNotNullOfOrNull null
            runCatching {
                String(Base64.getUrlDecoder().decode(item.substring(separator + 1)), Charsets.UTF_8)
            }.getOrNull()?.takeIf(String::isNotBlank)
        }
        if (legacyDetails != null) return legacyDetails
        return legacyFlags.split(',').mapNotNull(PatientWarningFlag::fromCode)
            .firstOrNull()?.arabicLabel.orEmpty()
    }
}
