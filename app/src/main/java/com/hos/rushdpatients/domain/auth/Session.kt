package com.hos.rushdpatients.domain.auth

import com.hos.rushdpatients.data.model.Role
import java.time.Instant

data class Session(
    val doctorId: String,
    val doctorName: String,
    val telegramId: Long?,
    val role: Role,
    val unlockedAt: Instant
) {
    val isAdmin: Boolean get() = role.isAdmin
}