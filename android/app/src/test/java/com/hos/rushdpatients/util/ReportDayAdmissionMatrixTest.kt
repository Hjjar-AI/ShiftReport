package com.hos.rushdpatients.util

import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Calendar, exact 09:30 and missing/future-admission combinations; no wall-clock dependency. */
@RunWith(Parameterized::class)
class ReportDayAdmissionMatrixTest(
    private val caseName: String,
    private val now: LocalDateTime,
    private val admission: LocalDate?,
    private val expectedReportDate: LocalDate,
    private val expectedDays: Int?
) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any?>> = buildList {
            val times = listOf(LocalTime.MIDNIGHT to true, LocalTime.of(8, 30) to true,
                LocalTime.of(9, 29, 59, 999_999_999) to true, LocalTime.of(9, 30) to false,
                LocalTime.NOON to false, LocalTime.MAX to false)
            for (date in listOf("2026-01-01", "2024-03-01", "2026-04-01", "2026-11-15").map(LocalDate::parse)) {
                for ((time, beforeBoundary) in times) {
                    val admissions = listOf(date.minusDays(7), date.minusDays(1), date, date.plusDays(1), null)
                    val days = if (beforeBoundary) listOf(7, 1, 1, 1, null) else listOf(8, 2, 1, 1, null)
                    admissions.forEachIndexed { index, admission ->
                        add(arrayOf<Any?>("$date/$time/admission=$admission", date.atTime(time), admission,
                            if (beforeBoundary) date.minusDays(1) else date, days[index]))
                    }
                }
            }
        }
    }
    @Test fun displayDayAndInclusiveStayAgreeWithoutRewritingAdmission() {
        val patient = Fixtures.patient(admittanceDate = admission)
        val original = patient.copy()
        val reportDate = ShiftDate.current(now)
        assertEquals(caseName, expectedReportDate, reportDate)
        assertEquals(caseName, expectedDays, patient.admittanceDaysOn(reportDate))
        assertEquals(caseName, original, patient)
        assertEquals(caseName, admission, patient.admittanceDate)
    }
}
