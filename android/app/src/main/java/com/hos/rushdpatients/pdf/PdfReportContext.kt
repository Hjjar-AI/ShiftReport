package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.domain.report.ReportSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Public report context only; never takes connection settings or credentials. */
internal object PdfReportContext {
    /** Compact date/totals row; callers measure and wrap it. */
    fun overview(summary: ReportSummary): String =
        "${PdfStrings.DATE_LABEL}: ${summary.shiftDate} • المرضى: ${summary.patientCount}" +
            " • نفسية: ${summary.psychoCount} • المرافقون: ${summary.escortCount}"

    fun lines(summary: ReportSummary, includeShiftDate: Boolean = true): List<String> =
        if (includeShiftDate) listOf("تاريخ المناوبة: ${summary.shiftDate}") else emptyList()

    fun createdAt(options: PdfExportOptions): String =
        "أُنشئ في: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale("ar"))
            .format(Instant.ofEpochMilli(options.generatedAtEpochMillis).atZone(ZoneId.systemDefault()))
}
