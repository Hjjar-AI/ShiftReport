package com.hos.rushdpatients.ui.theme

enum class AppThemePreset(val arabicLabel: String) {
    SYSTEM("ألوان النظام"),
    SAGE("زيتوني دافئ"),
    COASTAL("محيطي سريري"),
    SUNSET("كحلي وذهبي"),
    FUCHSIA("بنفسجي هادئ");

    companion object {
        fun fromSetting(value: String?): AppThemePreset =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
