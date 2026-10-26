package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.R

import com.hos.rushdpatients.util.ArabicNumbers
import java.time.Duration
import java.time.Instant

/** Presentation only: technical identifiers and stored/exported values keep their original form. */
fun uiNumber(value: Any): String = ArabicNumbers.toAscii(value.toString())

fun relativeFreshness(uiText: UiStrings, updatedAt: Instant, now: Instant = Instant.now()): String {
    val minutes = Duration.between(updatedAt, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> uiText(R.string.ui_ui_text_001)
        minutes < 60 -> uiText(R.string.ui_ui_text_002, uiNumber(minutes))
        minutes < 1440 -> uiText(R.string.ui_ui_text_003, uiNumber(minutes / 60))
        else -> uiText(R.string.ui_ui_text_004, uiNumber(minutes / 1440))
    }
}

fun uiDateTime(instant: Instant, pattern: String = "yyyy-MM-dd HH:mm"): String =
    uiNumber(java.time.format.DateTimeFormatter.ofPattern(pattern).format(instant.atZone(java.time.ZoneId.systemDefault())))
