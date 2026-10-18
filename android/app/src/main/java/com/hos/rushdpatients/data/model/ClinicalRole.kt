package com.hos.rushdpatients.data.model

enum class ClinicalRole(val code: String, val arabicLabel: String) {
    RESIDENT("RESIDENT", "مقيم"),
    SUPERVISOR("SUPERVISOR", "مشرف");

    fun canBeResident(): Boolean = this == RESIDENT
    fun canBeSupervisor(): Boolean = this == SUPERVISOR

    companion object {
        fun fromCode(value: String?): ClinicalRole = entries.firstOrNull {
            it.code.equals(value?.trim(), ignoreCase = true)
        } ?: RESIDENT
    }
}
