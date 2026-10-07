package com.hos.rushdpatients.ui.theme

enum class AppThemePreset(val arabicLabel: String) {
    SYSTEM("فيروزي (افتراضي)"),
    SAGE("زيتوني"),
    COASTAL("أزرق"),
    SUNSET("كحلي"),
    FUCHSIA("بنفسجي");

    companion object {
        fun fromSetting(value: String?): AppThemePreset =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
