package com.hos.rushdpatients.ui.theme

/** Stored enum IDs stay stable when display names and palettes are refreshed. */
enum class AppThemePreset(val arabicLabel: String) {
    SYSTEM("لازورد · Lapis"),
    SAGE("زمرد · Emerald"),
    COASTAL("سوسن · Iris"),
    SUNSET("زعفران · Saffron"),
    FUCHSIA("تنين وردي · Dragonfruit"),
    VANILLA("فيروز · Turquoise"),
    CHOCOLATE("غرافيت · Graphite"),
    CREAM("خشخاش · Poppy");

    companion object {
        fun fromSetting(value: String?): AppThemePreset =
            entries.firstOrNull { it.name == value } ?: VANILLA
    }
}
