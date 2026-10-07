package com.hos.rushdpatients.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object ShiftDate {
    private val rolloverTime: LocalTime = LocalTime.of(8, 30)

    fun endOf(date: LocalDate): java.time.Instant = date.plusDays(1).atTime(rolloverTime)
        .atZone(java.time.ZoneId.systemDefault()).toInstant()

    /** The previous calendar date remains active until 08:30 local time. */
    fun current(now: LocalDateTime = LocalDateTime.now()): LocalDate =
        if (now.toLocalTime().isBefore(rolloverTime)) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
}
