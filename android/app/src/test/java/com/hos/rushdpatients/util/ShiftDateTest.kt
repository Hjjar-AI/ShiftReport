package com.hos.rushdpatients.util

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

import java.time.LocalDateTime
import java.time.ZoneId

class ShiftDateTest {
    @Test fun `shift date switches exactly at 0830 including year boundary`() {
        assertEquals(LocalDate.of(2025, 12, 31), ShiftDate.current(LocalDateTime.of(2026, 1, 1, 8, 29, 59)))
        assertEquals(LocalDate.of(2026, 1, 1), ShiftDate.current(LocalDateTime.of(2026, 1, 1, 8, 30)))
        assertEquals(LocalDate.of(2026, 1, 1), ShiftDate.current(LocalDateTime.of(2026, 1, 1, 23, 59)))
    }
    @Test fun `task shift deadline is next calendar day at local rollover`() {
        val date = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDateTime.of(2024, 3, 1, 8, 30), ShiftDate.endOf(date).atZone(ZoneId.systemDefault()).toLocalDateTime())
    }
    @Test fun `deadline uses local calendar rollover across spring and autumn offset changes`() {
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
            for (date in listOf(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 10, 24))) {
                val end = ShiftDate.endOf(date)
                val local = end.atZone(ZoneId.of("Europe/Berlin"))
                assertEquals(date.plusDays(1), local.toLocalDate())
                assertEquals(java.time.LocalTime.of(8, 30), local.toLocalTime())
            }
        } finally { java.util.TimeZone.setDefault(original) }
    }
    @Test fun `timezone switch recomputes deadline without rewriting its clinical shift date`() {
        val original = java.util.TimeZone.getDefault()
        val date = LocalDate.of(2026, 1, 1)
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Damascus"))
            val damascus = ShiftDate.endOf(date)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            val utc = ShiftDate.endOf(date)
            assertNotEquals(damascus, utc)
            assertEquals(date.plusDays(1).atTime(8, 30).toInstant(java.time.ZoneOffset.UTC), utc)
        } finally { java.util.TimeZone.setDefault(original) }
    }
}
