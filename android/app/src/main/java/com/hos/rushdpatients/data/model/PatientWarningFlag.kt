package com.hos.rushdpatients.data.model

enum class PatientWarningFlag(val code: String, val arabicLabel: String) {
    URGENT_REVIEW("urgentReview", "مراجعة عاجلة"),
    ALLERGY("allergy", "حساسية"),
    ISOLATION("isolation", "عزل"),
    FALL_RISK("fallRisk", "خطر سقوط");

    companion object {
        fun fromCode(code: String): PatientWarningFlag? =
            entries.firstOrNull { it.code == code }
    }
}
