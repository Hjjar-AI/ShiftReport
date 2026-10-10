package com.hos.rushdpatients.ui.theme

enum class AppThemePreset(val arabicLabel: String) {
    SYSTEM("سماوي · Dusk"),
    SAGE("نعناع · Mint"),
    COASTAL("محيطي · Ocean"),
    SUNSET("ليموني · Lemonade"),
    FUCHSIA("ياقوتي · Ruby"),
    VANILLA("فانيلا · Vanilla"),
    CHOCOLATE("شوكولا · Chocolate"),
    CREAM("سكري سمني · Cream");

    companion object {
        fun fromSetting(value: String?): AppThemePreset =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
