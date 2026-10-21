package com.hos.rushdpatients.ui.theme

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

/** Appearance is independent of the selected accent palette. */
enum class AppAppearance(@StringRes private val arabicLabelRes: Int) {
    SYSTEM(R.string.ui_app_appearance_001), LIGHT(R.string.ui_app_appearance_002), DARK(R.string.ui_app_appearance_003), AMOLED(R.string.ui_app_appearance_004);

    companion object {
        fun fromSetting(value: String?): AppAppearance =
            entries.firstOrNull { it.name == value } ?: LIGHT
    }

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}
