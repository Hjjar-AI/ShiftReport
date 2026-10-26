package com.hos.rushdpatients.ui.theme

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

/** Stored enum IDs stay stable when display names and palettes are refreshed. */
enum class AppThemePreset(@StringRes private val arabicLabelRes: Int) {
    SYSTEM(R.string.ui_app_theme_preset_001),
    SAGE(R.string.ui_app_theme_preset_002),
    COASTAL(R.string.ui_app_theme_preset_003),
    SUNSET(R.string.ui_app_theme_preset_004),
    FUCHSIA(R.string.ui_app_theme_preset_005),
    VANILLA(R.string.ui_app_theme_preset_006),
    CHOCOLATE(R.string.ui_app_theme_preset_007),
    CREAM(R.string.ui_app_theme_preset_008);

    companion object {
        val availablePresets = listOf(VANILLA, SYSTEM, COASTAL, CREAM)

        fun fromSetting(value: String?): AppThemePreset =
            (entries.firstOrNull { it.name == value } ?: VANILLA).simplified()
    }

    /** Keep stored IDs readable while merging neighboring color families. */
    fun simplified(): AppThemePreset = when (this) {
        SAGE -> VANILLA
        SUNSET -> CREAM
        FUCHSIA -> COASTAL
        CHOCOLATE -> SYSTEM
        else -> this
    }

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}
