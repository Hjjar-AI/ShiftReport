package com.hos.rushdpatients.data.model

enum class Role {
    NON_ADMIN,
    ADMIN,
    PERMANENT_ADMIN;

    val isAdmin: Boolean
        get() = this == ADMIN || this == PERMANENT_ADMIN

    companion object {
        fun fromRank(rank: Int?, isPermanent: Boolean): Role = when {
            isPermanent -> PERMANENT_ADMIN
            rank != null && rank > 0 -> ADMIN
            else -> NON_ADMIN
        }
    }
}
