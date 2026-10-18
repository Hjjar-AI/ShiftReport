package com.hos.rushdpatients.ui.theme

/** Appearance is independent of the selected accent palette. */
enum class AppAppearance(val arabicLabel: String) {
    SYSTEM("حسب النظام"), LIGHT("فاتح"), DARK("داكن"), AMOLED("ليلي أسود · Night");

    companion object {
        fun fromSetting(value: String?): AppAppearance =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
