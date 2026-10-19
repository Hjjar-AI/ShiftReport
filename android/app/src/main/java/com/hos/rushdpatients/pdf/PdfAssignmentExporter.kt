package com.hos.rushdpatients.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.domain.doctor.DoctorTeams
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import javax.inject.Inject

/** Admin-only A4 grid: right-to-left team placement and right-side Arabic numbering. */
class PdfAssignmentExporter @Inject constructor(private val adminAuthorizer: AdminAuthorizer) {
    private data class Row(val lines: List<String>, val height: Float, val number: Int? = null)
    private data class Team(val supervisor: Row, val residents: Row, val patients: List<Row>, val color: Int) {
        val height: Float get() = supervisor.height + residents.height + patients.sumOf { it.height.toDouble() }.toFloat()
    }

    suspend fun export(
        patients: List<Patient>, doctorNames: Map<String, String>, registry: List<Doctor>, summary: ReportSummary,
        options: PdfExportOptions, outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val actorId = adminAuthorizer.requireAdmin().id
        DoctorTeams.validate(registry)
        require(patients.isNotEmpty()) { "لا يوجد مرضى لتصدير توزيعهم" }
        val width = PdfPaperSize.A4.widthPoints
        val height = PdfPaperSize.A4.heightPoints
        val tableWidth = 56.7f * 72f / 25.4f
        val columnGap = 3f // Thin vertical gutter between adjacent supervisor tables.
        val margin = (width - 3 * tableWidth - 2 * columnGap) / 2f
        val top = 20f
        val gap = 12f
        val padding = 3f
        val numberWidth = 7f * 72f / 25.4f
        val palette = PdfPalette.resolve(options)
        val bidi = BidiFormatter.getInstance()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; textAlign = Paint.Align.RIGHT; color = palette.text }
        val heading = Paint(body).apply { textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val residentPaint = Paint(body).apply { textSize = 8.5f }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .5f; color = palette.border }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun row(text: String, paint: Paint, availableWidth: Float, number: Int? = null): Row {
            val lines = PdfTextWrapper.wrap(text, paint, availableWidth - 2 * padding)
            return Row(lines, lines.size * PdfTextWrapper.lineHeight(paint) + 2 * padding, number)
        }
        val rosterNote = if (summary.shiftDate != com.hos.rushdpatients.util.ShiftDate.current())
            "مناوبة محفوظة · الفرق حسب السجل الحالي\n" else ""
        val documentTitlePaint = Paint(heading).apply { textSize = 12f }
        val documentTitle = row("توزيع المرضى حسب المشرف", documentTitlePaint, 3 * tableWidth + 2 * columnGap)
        val context = row(rosterNote +
            PdfReportContext.lines(summary, includeShiftDate = false).joinToString("\n"), body, 3 * tableWidth)
        val contextHeight = if (rosterNote.isBlank()) 0f else context.height
        val timestampPaint = Paint(body).apply { textSize = 8.5f }
        val timestampSpace = gap + PdfTextWrapper.lineHeight(timestampPaint)
        val tableTop = top + documentTitle.height + contextHeight + gap
        val maxTableHeight = height - tableTop - 24f - timestampSpace
        val fragments = mutableListOf<Team>()
        var number = 1
        val teams = patients.groupBy { it.responsibleSpecialistId }.toMutableMap()
        registry.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.SUPERVISOR }.forEach { teams.getOrPut(it.id) { emptyList() } }
        teams.entries.forEachIndexed { index, (supervisorId, members) ->
            coroutineContext.ensureActive()
            val supervisor = row(supervisorId?.let { doctorNames[it] ?: "مشرف محفوظ" } ?: "بدون مشرف", heading, tableWidth)
            val residents = registry.filter { !it.isDeleted && it.clinicalRole == ClinicalRole.RESIDENT &&
                    supervisorId != null && it.teamSupervisorId == supervisorId }
                .joinToString("، ") { it.fullName }.ifBlank { "لم يُحدد مقيم في الفريق" }
            val residentRow = row(residents, residentPaint, tableWidth)
            require(supervisor.height + residentRow.height <= maxTableHeight) { "أسماء الفريق أطول من مساحة جدول A4" }
            var rows = mutableListOf<Row>()
            var used = supervisor.height + residentRow.height
            members.forEach { patient ->
                val patientRow = row(patient.name, body, tableWidth - numberWidth, number++)
                require(supervisor.height + residentRow.height + patientRow.height <= maxTableHeight) {
                    "اسم المريض أو أسماء الفريق أطول من مساحة جدول A4؛ راجع الأسماء قبل التصدير"
                }
                if (used + patientRow.height > maxTableHeight && rows.isNotEmpty()) {
                    fragments += Team(supervisor, residentRow, rows.toList(), palette.group(index))
                    rows = mutableListOf()
                    used = supervisor.height + residentRow.height
                }
                rows += patientRow
                used += patientRow.height
            }
            fragments += Team(supervisor, residentRow, rows.toList(), palette.group(index))
        }
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
                    page.canvas.drawText(bidi.unicodeWrap(line, TextDirectionHeuristics.FIRSTSTRONG_LTR), width - margin, baseline, documentTitlePaint)
                    baseline += PdfTextWrapper.lineHeight(documentTitlePaint)
                }
                baseline = top + documentTitle.height + padding - body.fontMetrics.top
                (if (contextHeight > 0f) context.lines else emptyList()).forEach { line ->
                    page.canvas.drawText(bidi.unicodeWrap(line, TextDirectionHeuristics.FIRSTSTRONG_LTR), width - margin, baseline, body)
                    baseline += PdfTextWrapper.lineHeight(body)
                }
                val footer = Paint(body).apply { textSize = 8f; textAlign = Paint.Align.CENTER }
                page.canvas.drawText("$pageNumber", width / 2f, height - 10f, footer)
                y = tableTop
            }
            newPage()
            fragments.chunked(3).forEach { tableRow ->
                coroutineContext.ensureActive()
                val rowHeight = tableRow.maxOf { it.height }
                if (y + rowHeight + timestampSpace > height - 24f) newPage()
                tableRow.forEachIndexed { column, team ->
                    val canvas = checkNotNull(openPage).canvas
                    val x = width - margin - tableWidth - column * (tableWidth + columnGap)
                    var cellY = y
                    fun draw(value: Row, paint: Paint, background: Int, textColor: Int) {
                        fill.color = background
                        canvas.drawRect(x, cellY, x + tableWidth, cellY + value.height, fill)
                        val p = Paint(paint).apply { color = textColor }
                        var baseline = cellY + padding - p.fontMetrics.top
                        value.lines.forEach { line ->
                            canvas.drawText(bidi.unicodeWrap(line, TextDirectionHeuristics.FIRSTSTRONG_LTR),
                                x + tableWidth - (if (value.number != null) numberWidth else 0f) - padding, baseline, p)
                            baseline += PdfTextWrapper.lineHeight(p)
                        }
                        value.number?.let { n ->
                            canvas.drawLine(x + tableWidth - numberWidth, cellY, x + tableWidth - numberWidth, cellY + value.height, border)
                            val numberPaint = Paint(body).apply { textAlign = Paint.Align.CENTER }
                            canvas.drawText(com.hos.rushdpatients.util.ArabicNumbers.toArabicDigits(n.toString()), x + tableWidth - numberWidth / 2f,
                                cellY + padding - numberPaint.fontMetrics.top, numberPaint)
                        }
                        canvas.drawRect(x, cellY, x + tableWidth, cellY + value.height, border)
                        cellY += value.height
                    }
                    draw(team.supervisor, heading, palette.titleHeader, Color.WHITE)
                    draw(team.residents, residentPaint, team.color, palette.text)
                    team.patients.forEachIndexed { i, patient ->
                        draw(patient, body, if (i % 2 == 0) palette.pageBackground else palette.zebra, palette.text)
                    }
                }
                y += rowHeight + gap
            }
            checkNotNull(openPage).canvas.drawText(
                bidi.unicodeWrap(PdfReportContext.createdAt(options), TextDirectionHeuristics.FIRSTSTRONG_LTR),
                width - margin, y - timestampPaint.fontMetrics.top, timestampPaint)
            openPage?.let { document.finishPage(it) }
            openPage = null
            require(adminAuthorizer.requireAdmin().id == actorId) { "تغيّر المستخدم؛ أعد تصدير توزيع المرضى" }
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { document.writeTo(it) }
            outputFile
        } finally {
            openPage?.let { document.finishPage(it) }
            document.close()
        }
    }
}
