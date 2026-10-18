package com.hos.rushdpatients.data.model

enum class PatientBadgePriority(val code: String, val arabicLabel: String) {
    LOW("low", "منخفضة"),
    MEDIUM("medium", "متوسطة"),
    HIGH("high", "عالية");

    companion object {
        fun fromCode(code: String): PatientBadgePriority? =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
    }
}
