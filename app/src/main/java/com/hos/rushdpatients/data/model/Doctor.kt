package com.hos.rushdpatients.data.model

import java.time.Instant
import java.util.UUID

data class Doctor(
    val id: String = UUID.randomUUID().toString(),
    val fullName: String,
    val firstName: String,
    val lastName: String,
    val gender: Gender,
    val clinicalRole: ClinicalRole = ClinicalRole.RESIDENT,
    val supervisorGroupChatId: Long? = null,
    val telegramId: Long? = null,
    val telegramUsername: String? = null,
    val customTitle: String? = null,
    val rank: Int = 0,
    val isPermanentAdmin: Boolean = false,
    val extraOptions: Set<String> = emptySet(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
) {
    val isDeleted: Boolean get() = deletedAt != null
    val role: Role get() = Role.fromRank(rank, isPermanentAdmin)
    val isAdmin: Boolean get() = role.isAdmin
    val telegramDeepLink: String?
        get() = telegramId?.let { "tg://user?id=$it" }
}
