package com.hos.rushdpatients.ui.theme

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

enum class AppFontScale(@StringRes private val arabicLabelRes: Int, val multiplier: Float) {
    SMALL(R.string.ui_app_font_scale_001, 0.85f),
    NORMAL(R.string.ui_app_font_scale_002, 1.0f),
    LARGE(R.string.ui_app_font_scale_003, 1.15f),
    EXTRA_LARGE(R.string.ui_app_font_scale_004, 1.3f),
    HUGE(R.string.ui_app_font_scale_005, 1.5f);

    companion object {
        fun fromSetting(value: String?): AppFontScale =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}
