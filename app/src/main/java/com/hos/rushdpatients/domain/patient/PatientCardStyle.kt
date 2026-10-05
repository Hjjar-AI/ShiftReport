package com.hos.rushdpatients.domain.patient

/** Shared visual vocabulary for patient cards on screen and in card-based PDFs. */
enum class PatientCardStyle(val arabicLabel: String, val shortLabel: String) {
    BANNER("شريط علوي", "F"),
    ALERT("تنبيه بارز", "P"),
    ICON_ROWS("صفوف رمزية", "Z"),
    BADGE_HEADER("شارة في العنوان", "AH"),
    COLOR_BAR("شريط جانبي", "AJ"),
    WRISTBAND("سوار المريض", "AL"),
    INITIALS("مربع الأحرف", "AS"),
    FIELD_MONOGRAMS("رموز الحقول", "AY"),
    AVATAR_CHIPS("صورة وشرائح", "AW");

    companion object {
        fun fromSetting(value: String?): PatientCardStyle =
            entries.firstOrNull { it.name == value } ?: BADGE_HEADER
    }
}
