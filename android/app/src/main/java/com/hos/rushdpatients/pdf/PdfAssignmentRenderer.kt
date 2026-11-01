package com.hos.rushdpatients.pdf

import android.graphics.Color
import android.graphics.Paint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.graphics.pdf.PdfDocument
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.patient.PatientAssignmentChange
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.domain.doctor.DoctorTeams
import com.hos.rushdpatients.domain.report.ReportSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import javax.inject.Inject

/** Shared pure A5 renderer: right-to-left team placement and right-side Arabic numbering. */
class PdfAssignmentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private data class Row(val lines: List<String>, val height: Float, val number: Int? = null, val highlighted: Boolean = false)
    private data class Team(val supervisor: Row, val residents: Row, val patients: List<Row>) {
        val height: Float get() = supervisor.height + residents.height + patients.sumOf { it.height.toDouble() }.toFloat()
    }

    suspend fun export(
        patients: List<Patient>, doctorNames: Map<String, String>, registry: List<Doctor>, summary: ReportSummary,
        options: PdfExportOptions, outputFile: File, documentLabel: String? = null
    ): File = withContext(Dispatchers.IO) {
        DoctorTeams.validate(registry)
        require(patients.isNotEmpty()) { "لا يوجد مرضى لتصدير توزيعهم" }
        val width = PdfPaperSize.A5.widthPoints
        val height = PdfPaperSize.A5.heightPoints
        val columnsPerRow = 3 // Three RTL team columns; long names wrap at the retained 11pt size.
        val margin = 10f
        val contentWidth = width - 2 * margin
        val columnGap = 3f
        val tableWidth = (contentWidth - (columnsPerRow - 1) * columnGap) / columnsPerRow
        val top = 8f
        val bottomMargin = 14f
        val gap = 6f
        val headerGap = 2f
        val padding = 2f
        val numberWidth = 16f
        val palette = PdfPalette.resolve(options)
        val fonts = PdfFonts(context)
        val bidi = BidiFormatter.getInstance()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.regular; textSize = 10f; textAlign = Paint.Align.RIGHT; color = palette.text }
        val patientPaint = Paint(body).apply { textSize = 11f; typeface = fonts.bold }
        val highlightedPatient = Paint(patientPaint).apply { typeface = fonts.bold }
        val heading = Paint(body).apply { textSize = 11f; typeface = fonts.bold }
        val residentPaint = Paint(body).apply { textSize = 9.5f; typeface = fonts.bold }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = palette.ruleWidth; color = palette.ruleColor }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun row(text: String, paint: Paint, availableWidth: Float, number: Int? = null, highlighted: Boolean = false): Row {
            val lines = PdfTextWrapper.wrap(text, paint, availableWidth - 2 * padding)
            return Row(lines, lines.size * PdfTextWrapper.lineHeight(paint) + 2 * padding, number, highlighted)
        }
        val rosterNote = if (summary.shiftDate != com.hos.rushdpatients.util.ShiftDate.current())
            "مناوبة محفوظة • الفرق حسب السجل الحالي\n" else ""
        val documentTitlePaint = Paint(heading).apply { textSize = 13f; textAlign = Paint.Align.CENTER; color = palette.titleHeader }
        val documentTitle = row("توزيع المرضى حسب المشرف", documentTitlePaint, contentWidth)
        val legendPaint = Paint(body).apply { textSize = 7.5f }
        val legend = row(context.getString(R.string.pdf_assignment_highlight_legend), legendPaint, contentWidth)
        val reportContext = row(rosterNote +
            PdfReportContext.lines(summary, includeShiftDate = false).joinToString("\n"), body, contentWidth)
        val contextHeight = if (rosterNote.isBlank()) 0f else reportContext.height
        val timestampPaint = Paint(body).apply { textSize = 7f }
        val timestampGap = 4f
        val timestampSpace = timestampGap + PdfTextWrapper.lineHeight(timestampPaint)
        val tableTop = top + documentTitle.height + contextHeight + legend.height + headerGap
        val maxTableHeight = height - tableTop - bottomMargin - timestampSpace
        val fragments = mutableListOf<Team>()
        var number = 1
        val teams = patients.groupBy { it.responsibleSpecialistId }.toMutableMap()
        registry.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.SUPERVISOR }.forEach { teams.getOrPut(it.id) { emptyList() } }
        teams.entries.forEach { (supervisorId, members) ->
            coroutineContext.ensureActive()
            val supervisor = row(supervisorId?.let { doctorNames[it] ?: "مشرف محفوظ" } ?: "بدون مشرف", heading, tableWidth)
            val residents = registry.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.RESIDENT &&
                    supervisorId != null && it.teamSupervisorId == supervisorId }
                .joinToString("، ") { it.fullName }.ifBlank { "لم يُحدد مقيم في الفريق" }
            val residentRow = row(residents, residentPaint, tableWidth)
            require(supervisor.height + residentRow.height <= maxTableHeight) { "أسماء الفريق أطول من مساحة جدول A5" }
            var rows = mutableListOf<Row>()
            var used = supervisor.height + residentRow.height
            members.forEach { patient ->
                val highlighted = PatientAssignmentChange.highlighted(patient, summary.shiftDate)
                val patientRow = row(patient.displayName, if (highlighted) highlightedPatient else patientPaint,
                    tableWidth - numberWidth, number++, highlighted)
                require(supervisor.height + residentRow.height + patientRow.height <= maxTableHeight) {
                    "اسم المريض أو أسماء الفريق أطول من مساحة جدول A5؛ راجع الأسماء قبل التصدير"
                }
                if (used + patientRow.height > maxTableHeight && rows.isNotEmpty()) {
                    fragments += Team(supervisor, residentRow, rows.toList())
                    rows = mutableListOf()
                    used = supervisor.height + residentRow.height
                }
                rows += patientRow
                used += patientRow.height
            }
            fragments += Team(supervisor, residentRow, rows.toList())
        }
        // Measure and paginate before drawing; keep the compact header at the top of every page.
        val pages = mutableListOf<MutableList<List<Team>>>()
        var pageRows = mutableListOf<List<Team>>()
        var usedHeight = 0f
        fragments.chunked(columnsPerRow).forEach { tableRow ->
            val rowHeight = tableRow.maxOf { it.height }
            val required = rowHeight + if (pageRows.isEmpty()) 0f else gap
            if (pageRows.isNotEmpty() && usedHeight + required > maxTableHeight) {
                pages += pageRows
                pageRows = mutableListOf()
                usedHeight = 0f
            }
            if (pageRows.isNotEmpty()) usedHeight += gap
            pageRows += tableRow
            usedHeight += rowHeight
        }
        if (pageRows.isNotEmpty()) pages += pageRows
        val document = PdfDocument()
        var openPage: PdfDocument.Page? = null
        try {
            var pageNumber = 0
            var y = tableTop
            fun newPage() {
                openPage?.let { document.finishPage(it) }
                pageNumber++
                val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
                openPage = page
                page.canvas.drawColor(palette.pageBackground)
                var baseline = top + padding - documentTitlePaint.fontMetrics.top
                documentTitle.lines.forEach { line ->
                    page.canvas.drawText(bidi.unicodeWrap(line, TextDirectionHeuristics.FIRSTSTRONG_LTR), width / 2f, baseline, documentTitlePaint)
                    baseline += PdfTextWrapper.lineHeight(documentTitlePaint)
                }
                baseline = top + documentTitle.height + padding - body.fontMetrics.top
                (if (contextHeight > 0f) reportContext.lines else emptyList()).forEach { line ->
                    page.canvas.drawText(bidi.unicodeWrap(line, TextDirectionHeuristics.FIRSTSTRONG_LTR), width - margin, baseline, body)
                    baseline += PdfTextWrapper.lineHeight(body)
                }
                baseline = top + documentTitle.height + contextHeight + padding - legendPaint.fontMetrics.top
                legend.lines.forEach { line ->
                    page.canvas.drawText(bidi.unicodeWrap(line, TextDirectionHeuristics.FIRSTSTRONG_LTR),
                        width - margin, baseline, legendPaint)
                    baseline += PdfTextWrapper.lineHeight(legendPaint)
                }
                val footer = Paint(body).apply { textSize = 6.5f; textAlign = Paint.Align.CENTER }
                page.canvas.drawText(bidi.unicodeWrap(listOfNotNull(documentLabel, "$pageNumber").joinToString(" • "),
                    TextDirectionHeuristics.FIRSTSTRONG_LTR), width / 2f, height - 6f, footer)
                y = tableTop
            }
            pages.forEachIndexed { pageIndex, rows ->
                coroutineContext.ensureActive()
                val lastPage = pageIndex == pages.lastIndex
                newPage()
                rows.forEach { tableRow ->
                    coroutineContext.ensureActive()
                    val rowHeight = tableRow.maxOf { it.height }
                    val bandWidth = tableRow.size * tableWidth + (tableRow.size - 1) * columnGap
                    val bandLeft = (width - bandWidth) / 2f
                    tableRow.forEachIndexed { column, team ->
                        val canvas = checkNotNull(openPage).canvas
                        val x = bandLeft + bandWidth - tableWidth - column * (tableWidth + columnGap)
                        var cellY = y
                        fun draw(value: Row, paint: Paint, background: Int, textColor: Int) {
                            fill.color = background
                            canvas.drawRect(x, cellY, x + tableWidth, cellY + value.height, fill)
                            if (value.highlighted) {
                                fill.color = Color.rgb(255, 244, 184)
                                canvas.drawRect(x, cellY, x + tableWidth - numberWidth, cellY + value.height, fill)
                            }
                            val p = Paint(paint).apply { color = textColor; if (value.number == null) textAlign = Paint.Align.CENTER }
                            var baseline = cellY + padding - p.fontMetrics.top
                            value.lines.forEach { line ->
                                canvas.drawText(bidi.unicodeWrap(line, TextDirectionHeuristics.FIRSTSTRONG_LTR),
                                    if (value.number == null) x + tableWidth / 2f else x + tableWidth - numberWidth - padding, baseline, p)
                                baseline += PdfTextWrapper.lineHeight(p)
                            }
                            value.number?.let { n ->
                                val numberPaint = Paint(patientPaint).apply { textAlign = Paint.Align.CENTER }
                                canvas.drawText(com.hos.rushdpatients.util.ArabicNumbers.toAscii(n.toString()), x + tableWidth - numberWidth / 2f,
                                    cellY + padding - numberPaint.fontMetrics.top, numberPaint)
                            }
                            canvas.drawLine(x, cellY + value.height, x + tableWidth, cellY + value.height, border)
                            cellY += value.height
                        }
                        draw(team.supervisor, heading, palette.titleHeader, Color.WHITE)
                        draw(team.residents, residentPaint, palette.teamSubheader, palette.text)
                        team.patients.forEachIndexed { i, patient ->
                            draw(patient, if (patient.highlighted) highlightedPatient else patientPaint, if (options.rowShading == PdfRowShading.NONE || i % 2 == 0) palette.pageBackground else palette.zebra, palette.text)
                        }
                        var fillerIndex = team.patients.size
                        while (cellY < y + rowHeight - .01f) {
                            val fillerHeight = minOf(PdfTextWrapper.lineHeight(patientPaint) + 2 * padding, y + rowHeight - cellY)
                            draw(Row(emptyList(), fillerHeight), patientPaint,
                                if (options.rowShading == PdfRowShading.NONE || fillerIndex++ % 2 == 0) palette.pageBackground else palette.zebra, palette.text)
                        }
                    }
                    y += rowHeight + gap
                }
                if (lastPage) checkNotNull(openPage).canvas.drawText(
                    bidi.unicodeWrap(PdfReportContext.createdAt(options), TextDirectionHeuristics.FIRSTSTRONG_LTR),
                    width - margin, y - gap + timestampGap - timestampPaint.fontMetrics.top, timestampPaint)
            }
            openPage?.let { document.finishPage(it) }
            openPage = null
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { document.writeTo(it) }
            outputFile
        } finally {
            openPage?.let { document.finishPage(it) }
            document.close()
        }
    }
}
