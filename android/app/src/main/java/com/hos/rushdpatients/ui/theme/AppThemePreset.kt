package com.hos.rushdpatients.ui.theme

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

/** Stored enum IDs stay stable when display names and palettes are refreshed. */
enum class AppThemePreset(@StringRes private val arabicLabelRes: Int) {
    SYSTEM(R.string.ui_app_theme_preset_001),
    COASTAL(R.string.ui_app_theme_preset_003),
    VANILLA(R.string.ui_app_theme_preset_006),
    CREAM(R.string.ui_app_theme_preset_008);

    companion object {
        val availablePresets = listOf(VANILLA, SYSTEM, COASTAL, CREAM)

        fun fromSetting(value: String?): AppThemePreset =
            entries.firstOrNull { it.name == value } ?: VANILLA
    }

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}
