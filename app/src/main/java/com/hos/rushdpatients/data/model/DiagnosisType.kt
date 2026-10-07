package com.hos.rushdpatients.data.model

enum class DiagnosisType(val code: String, val arabicLabel: String) {
    PSYCHIATRIC("PSYCHIATRIC", "نفسي"),
    ADDICTION("ADDICTION", "إدمان"),
    DUAL("DUAL", "مزدوج");

    companion object {
        fun fromCode(value: String?): DiagnosisType = when (value?.trim()?.uppercase()) {
            "ADDICTION", "إدمان" -> ADDICTION
            "DUAL", "مزدوج", "مشترك" -> DUAL
            else -> PSYCHIATRIC
        }
    }
}
