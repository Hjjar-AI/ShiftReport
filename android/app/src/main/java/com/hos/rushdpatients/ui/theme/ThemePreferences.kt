package com.hos.rushdpatients.ui.theme

/** Cosmetic display state only; never connection, identity or clinical settings. */
data class ThemePreferences(
    val preset: AppThemePreset = AppThemePreset.VANILLA,
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val fontScale: AppFontScale = AppFontScale.NORMAL,
    val uiPreferences: UiPreferences = UiPreferences()
)

data class ThemeUiState(
    val preferences: ThemePreferences? = null,
    val loadFailed: Boolean = false,
    val needsInitialization: Boolean = false
)
