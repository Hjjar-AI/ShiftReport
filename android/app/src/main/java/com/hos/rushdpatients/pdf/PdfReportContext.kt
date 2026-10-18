package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.domain.report.ReportSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Public report context only; never takes connection settings or credentials. */
internal object PdfReportContext {
    fun lines(options: PdfExportOptions, summary: ReportSummary): List<String> = listOf(
        "المشفى: ${options.hospitalName.ifBlank { "غير محدد" }}",
        "الجناح: ${options.wardName.ifBlank { "غير محدد" }}",
        "تاريخ المناوبة: ${summary.shiftDate}",
        "أُنشئ في: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale("ar"))
            .format(Instant.ofEpochMilli(options.generatedAtEpochMillis).atZone(ZoneId.systemDefault()))
    )
}
