package com.hos.rushdpatients.ui.theme

enum class AppFontScale(val arabicLabel: String, val multiplier: Float) {
    SMALL("صغير", 0.85f),
    NORMAL("عادي", 1.0f),
    LARGE("كبير", 1.15f),
    EXTRA_LARGE("كبير جداً", 1.3f),
    HUGE("ضخم", 1.5f);

    companion object {
        fun fromSetting(value: String?): AppFontScale =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}