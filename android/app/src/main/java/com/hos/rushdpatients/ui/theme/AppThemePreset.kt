package com.hos.rushdpatients.ui.theme

/** Stored enum IDs stay stable when display names and palettes are refreshed. */
enum class AppThemePreset(val arabicLabel: String) {
    SYSTEM("أطلس · Atlas"),
    SAGE("غابة · Forest"),
    COASTAL("جمشت · Amethyst"),
    SUNSET("كهرمان · Amber"),
    FUCHSIA("كرز · Cherry"),
    VANILLA("فيروز · Turquoise"),
    CHOCOLATE("نحاس · Copper"),
    CREAM("زيتون · Olive");

    companion object {
        fun fromSetting(value: String?): AppThemePreset =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
