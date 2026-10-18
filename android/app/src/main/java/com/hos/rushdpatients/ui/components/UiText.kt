package com.hos.rushdpatients.ui.components

import com.hos.rushdpatients.util.ArabicNumbers
import java.time.Duration
import java.time.Instant

/** Presentation only: technical identifiers and stored/exported values keep their original form. */
fun uiNumber(value: Any): String = ArabicNumbers.toArabicDigits(value.toString())

fun relativeFreshness(updatedAt: Instant, now: Instant = Instant.now()): String {
    val minutes = Duration.between(updatedAt, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "تم التحديث الآن"
        minutes < 60 -> "تم التحديث منذ ${uiNumber(minutes)} دقيقة"
        minutes < 1440 -> "تم التحديث منذ ${uiNumber(minutes / 60)} ساعة"
        else -> "تم التحديث منذ ${uiNumber(minutes / 1440)} يوم"
    }
}

fun uiDateTime(instant: Instant, pattern: String = "yyyy-MM-dd HH:mm"): String =
    uiNumber(java.time.format.DateTimeFormatter.ofPattern(pattern).format(instant.atZone(java.time.ZoneId.systemDefault())))
