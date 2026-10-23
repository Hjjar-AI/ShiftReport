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
}
