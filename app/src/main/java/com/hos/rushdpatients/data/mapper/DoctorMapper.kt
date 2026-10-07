package com.hos.rushdpatients.data.mapper

import com.hos.rushdpatients.data.db.entity.DoctorEntity
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Gender
import java.time.Instant

object DoctorMapper {

    private const val CLINICAL_ROLE_PREFIX = "clinicalRole:"
    private const val SUPERVISOR_GROUP_PREFIX = "supervisorGroupChatId:"

    fun toEntity(doctor: Doctor): DoctorEntity = DoctorEntity(
        id = doctor.id,
        fullName = doctor.fullName,
        firstName = doctor.firstName,
        lastName = doctor.lastName,
        gender = doctor.gender.code,
        telegramId = doctor.telegramId,
        telegramUsername = doctor.telegramUsername,
        customTitle = doctor.customTitle,
        rank = doctor.rank,
        isPermanentAdmin = doctor.isPermanentAdmin,
        extraOptions = (
            doctor.extraOptions.filterNot {
                it.startsWith(CLINICAL_ROLE_PREFIX) || it.startsWith(SUPERVISOR_GROUP_PREFIX)
            } + "$CLINICAL_ROLE_PREFIX${doctor.clinicalRole.code}" +
                listOfNotNull(
                    doctor.supervisorGroupChatId?.let { "$SUPERVISOR_GROUP_PREFIX$it" }
                )
            ).joinToString(","),
        updatedAtEpochMillis = doctor.updatedAt.toEpochMilli(),
        deletedAtEpochMillis = doctor.deletedAt?.toEpochMilli()
    )

    fun fromEntity(entity: DoctorEntity): Doctor {
        val options = entity.extraOptions
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()
        val clinicalRole = options.firstOrNull { it.startsWith(CLINICAL_ROLE_PREFIX) }
            ?.removePrefix(CLINICAL_ROLE_PREFIX)
            .let(ClinicalRole::fromCode)
        val supervisorGroupChatId = options
            .firstOrNull { it.startsWith(SUPERVISOR_GROUP_PREFIX) }
            ?.removePrefix(SUPERVISOR_GROUP_PREFIX)
            ?.toLongOrNull()
        return Doctor(
            id = entity.id,
            fullName = entity.fullName,
            firstName = entity.firstName,
            lastName = entity.lastName,
            gender = Gender.fromCode(entity.gender),
            clinicalRole = clinicalRole,
            supervisorGroupChatId = supervisorGroupChatId,
            telegramId = entity.telegramId,
            telegramUsername = entity.telegramUsername,
            customTitle = entity.customTitle,
            rank = entity.rank,
            isPermanentAdmin = entity.isPermanentAdmin,
            extraOptions = options.filterNot {
                it.startsWith(CLINICAL_ROLE_PREFIX) || it.startsWith(SUPERVISOR_GROUP_PREFIX)
            }.toSet(),
            updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis),
            deletedAt = entity.deletedAtEpochMillis?.let(Instant::ofEpochMilli)
        )
    }
}
