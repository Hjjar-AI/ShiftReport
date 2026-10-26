package com.hos.rushdpatients.data.model

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientBadgeCodecTest {
    @Test fun `current representation preserves Unicode delimiters and severity`() {
        val badges = listOf(PatientBadge("تنبيه، ; | : \"متعدد\"\nسطر", PatientBadgePriority.HIGH),
            PatientBadge("ملاحظة", null), PatientBadge("ثالث", PatientBadgePriority.LOW))
        assertEquals(badges, PatientBadgeCodec.decode(PatientBadgeCodec.encode(badges)))
    }
    @Test fun `blank badges are dropped and outer whitespace is normalized`() {
        assertEquals(listOf(PatientBadge("ملاحظة")), PatientBadgeCodec.decode(
            PatientBadgeCodec.encode(listOf(PatientBadge("  ملاحظة  "), PatientBadge(" ")))))
        assertEquals("", PatientBadgeCodec.encode(emptyList()))
    }
}
