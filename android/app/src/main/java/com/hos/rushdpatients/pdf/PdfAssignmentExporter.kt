package com.hos.rushdpatients.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import javax.inject.Inject

/** Names-only team overview; one portrait page grows vertically without dropping rows. */
class PdfAssignmentExporter @Inject constructor(private val adminAuthorizer: AdminAuthorizer) {
    private data class Row(val lines: List<String>, val height: Float, val header: Boolean = false)
    private data class Team(val supervisor: Row, val residents: Row, val patients: List<Row>)

    suspend fun export(
        patients: List<Patient>, doctorNames: Map<String, String>, summary: ReportSummary,
        options: PdfExportOptions, outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val actorId = adminAuthorizer.requireAdmin().id
        require(patients.isNotEmpty()) { "لا يوجد مرضى لتصدير توزيعهم" }
        val margin = 18f
        val padding = 5f
        val width = options.paperSize.widthPoints
        val contentWidth = width - 2 * margin
        val palette = PdfPalette.resolve(options)
        val bidi = BidiFormatter.getInstance()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            textAlign = Paint.Align.RIGHT
            color = palette.text
        }
        val heading = Paint(body).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = .5f
            color = palette.border
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun row(text: String, header: Boolean = false): Row {
            val paint = if (header) heading else body
            val lines = PdfTextWrapper.wrap(text, paint, contentWidth - 2 * padding)
            return Row(lines, lines.size * PdfTextWrapper.lineHeight(paint) + 2 * padding, header)
        }
        fun doctor(id: String?, missing: String, saved: String): String =
            if (id == null) missing else doctorNames[id]?.takeIf { it.isNotBlank() } ?: saved
        val teams = patients.groupBy { it.responsibleSpecialistId }.map { (supervisorId, members) ->
            coroutineContext.ensureActive()
            val residentIds = members.mapNotNull { it.responsibleResidentId }.distinct()
            val residents = residentIds.joinToString("، ") { doctor(it, "", "مقيم محفوظ") }
                .ifBlank { "لم يُحدد مقيم" }
            Team(
                row("المشرف: " + doctor(supervisorId, "بدون مشرف", "مشرف محفوظ"), true),
                row("المقيمون: $residents"),
                members.map { row(it.name) }
            )
        }
        val context = listOf(row("توزيع المرضى حسب المشرف — الأسماء فقط", true)) +
            PdfReportContext.lines(options, summary).map { row(it) }
        val gap = 14f
        val requiredHeight = 2 * margin + context.sumOf { it.height.toDouble() } + gap +
            teams.sumOf { team ->
                (team.supervisor.height + team.residents.height +
                    team.patients.sumOf { it.height.toDouble() } + gap)
            }
        // Maintain portrait orientation even with A5/Letter and a long single-page overview.
        val pageHeight = ceil(maxOf(requiredHeight, options.paperSize.heightPoints.toDouble(), width + 1.0)).toInt()
        val document = PdfDocument()
        var openPage: PdfDocument.Page? = null
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, pageHeight, 1).create())
            openPage = page
            val canvas: Canvas = page.canvas
            canvas.drawColor(palette.pageBackground)
            var y = margin
            fun draw(row: Row, background: Int, textColor: Int) {
                fill.color = background
                canvas.drawRect(margin, y, width - margin, y + row.height, fill)
                val paint = Paint(if (row.header) heading else body).apply { color = textColor }
                var baseline = y + padding - paint.fontMetrics.top
                row.lines.forEach { line ->
                    canvas.drawText(bidi.unicodeWrap(line, TextDirectionHeuristics.FIRSTSTRONG_LTR),
                        width - margin - padding, baseline, paint)
                    baseline += PdfTextWrapper.lineHeight(paint)
                }
                canvas.drawRect(margin, y, width - margin, y + row.height, border)
                y += row.height
            }
            context.forEach { draw(it, palette.pageBackground, palette.text) }
            y += gap
            teams.forEachIndexed { index, team ->
                coroutineContext.ensureActive()
                draw(team.supervisor, palette.titleHeader, Color.WHITE)
                draw(team.residents, palette.group(index), palette.text)
                team.patients.forEachIndexed { patientIndex, patient ->
                    draw(patient, if (patientIndex % 2 == 0) palette.pageBackground else palette.zebra, palette.text)
                }
                y += gap
            }
            document.finishPage(page)
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
