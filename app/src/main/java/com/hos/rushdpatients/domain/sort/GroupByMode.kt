package com.hos.rushdpatients.domain.sort

enum class GroupByMode(val arabicLabel: String) {
    NONE("بدون تجميع"),
    SUPERVISOR("حسب الاختصاصي"),
    RESIDENT("حسب المقيم المسؤول"),
    DIAGNOSIS("حسب نوع التشخيص"),
    GENDER("حسب الجنس"),
    ADMISSION_DATE("حسب تاريخ الدخول");

    companion object {
        fun fromSetting(value: String?): GroupByMode =
            entries.firstOrNull { it.name == value } ?: SUPERVISOR
    }
}