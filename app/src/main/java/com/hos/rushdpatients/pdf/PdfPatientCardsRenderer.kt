package com.hos.rushdpatients.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportSummary
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/** Full-width, vertically stacked patient sections; long records paginate at readable size. */
internal class PdfPatientCardsRenderer {
    private val width = PdfPaperSize.A5.widthPoints
    private val height = PdfPaperSize.A5.heightPoints
    private val margin = 8.5f
    private val padding = 1f
    private val gap = 3f
    private val left = margin + padding
    private val right = width - margin - padding
    private val cardBottom = height - margin
    private val bidi = BidiFormatter.getInstance()
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        textAlign = Paint.Align.RIGHT
    }
    private val heading = Paint(body).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val name = Paint(heading).apply { textSize = 15f; isUnderlineText = true }
    private val footer = Paint(body).apply { textSize = 8f; textAlign = Paint.Align.CENTER }
    private val bottom: Float get() = cardBottom - padding - PdfTextWrapper.lineHeight(footer) - gap
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
    }
    private lateinit var palette: PdfPalette.Resolved
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageIndex = -1
    private var y = margin
    private val canvas: Canvas get() = checkNotNull(page).canvas

    private data class Anchor(val page: Int, val top: Float)
    private data class IndexEntry(val page: Int, val rect: RectF)

    suspend fun export(
        patients: List<Patient>, doctors: List<Doctor>, summary: ReportSummary,
        residentNames: Map<String, String>, supervisorNames: Map<String, String>,
        options: PdfExportOptions, outputFile: File, documentLabel: String?
    ): File {
        // A5 portrait is enforced here for every caller, independently of saved table options.
        palette = PdfPalette.resolve(options)
        body.color = palette.text
        heading.color = palette.text
        name.color = Color.WHITE
        outline.color = palette.border
        val entries = mutableListOf<IndexEntry>()
        val destinations = mutableListOf<Anchor>()
        val links = mutableListOf<PdfInternalLinks.Link>()
        try {
            newPage(documentLabel, index = true)
            indexText("${PdfStrings.REPORT_TITLE_PREFIX} · ${summary.shiftDate}", heading, documentLabel)
            indexText("${summary.patientCount} مريض · ${summary.psychoCount} حالة نفسية · ${summary.escortCount} مرافق", body, documentLabel)
            indexText("أطباء المناوبة", heading, documentLabel)
            doctors.forEach { indexText(it.fullName, body, documentLabel) }
            indexText("اضغط اسم المريض لفتح بطاقته؛ اضغط اسمه في البطاقة للعودة إلى الفهرس.", body, documentLabel)
            patients.forEach { patient ->
                coroutineContext.ensureActive()
                val lines = PdfTextWrapper.wrap("${patient.sortOrder} · ${patient.name}", heading, right - left)
                val rowHeight = lines.size * PdfTextWrapper.lineHeight(heading) + 2 * padding
                if (y + rowHeight > bottom) newPage(documentLabel, index = true)
                check(y + rowHeight <= bottom) { "اسم المريض أطول من مساحة صفحة الفهرس" }
                val top = y
                fill.color = palette.summary
                canvas.drawRect(margin, top, width - margin, top + rowHeight, fill)
                lines.forEach { drawLine(it, heading) }
                y = top + rowHeight
                entries += IndexEntry(pageIndex, RectF(margin, top, width - margin, y))
                y += gap
            }
            patients.forEachIndexed { index, patient ->
                coroutineContext.ensureActive()
                val entry = entries[index]
                fun cardHeader() {
                    val lines = PdfTextWrapper.wrap("${patient.sortOrder} · ${patient.name}", name, right - left)
                    val h = lines.size * PdfTextWrapper.lineHeight(name) + 2 * padding
                    check(y + h + gap + 2 * PdfTextWrapper.lineHeight(heading) <= bottom) {
                        "اسم المريض أطول من مساحة البطاقة"
                    }
                    val top = y
                    fill.color = palette.titleHeader
                    canvas.drawRect(margin, y, width - margin, y + h, fill)
                    lines.forEach { drawLine(it, name) }
                    y = top + h
                    links += PdfInternalLinks.Link(pageIndex, RectF(margin, top, width - margin, y), entry.page, entry.rect.top)
                    y += gap
                }
                newPage(documentLabel, index = false)
                destinations += Anchor(pageIndex, margin)
                cardHeader()
                val sections = listOf(
                    "بيانات المريض" to PdfPatientContent.buildPatientCell(patient),
                    "القبول" to PdfPatientContent.buildAdmitCell(patient),
                    "الفريق المسؤول" to PdfPatientContent.buildSupervisorResidentCell(patient, residentNames, supervisorNames),
                    PdfStrings.HEADER_CONDITION to PdfPatientContent.buildDiagnosisCell(patient),
                    PdfStrings.HEADER_TREATMENT to patient.treatmentPlan,
                    "المتابعة / المهام / التحاليل" to PdfPatientContent.buildNotesCell(patient, residentNames + supervisorNames)
                )
                sections.filter { it.second.isNotBlank() }.forEach { (title, text) ->
                    val lines = PdfTextWrapper.wrap(text, body, right - left)
                    val labelHeight = PdfTextWrapper.lineHeight(heading)
                    if (y + labelHeight + PdfTextWrapper.lineHeight(body) > bottom) {
                        newPage(documentLabel, index = false)
                        cardHeader()
                    }
                    drawLine(title, heading)
                    lines.forEach { line ->
                        if (y + PdfTextWrapper.lineHeight(body) > bottom) {
                            newPage(documentLabel, index = false)
                            cardHeader()
                            drawLine("$title (تابع)", heading)
                        }
                        drawLine(line, body)
                    }
                    y += gap
                }
            }
            entries.forEachIndexed { index, entry ->
                val destination = destinations[index]
                links += PdfInternalLinks.Link(entry.page, entry.rect, destination.page, destination.top)
            }
            finishPage()
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { document.writeTo(it) }
            coroutineContext.ensureActive()
            PdfInternalLinks.append(outputFile, links, height.toFloat())
            return outputFile
        } finally {
            finishPage()
            document.close()
        }
    }

    private fun finishPage() {
        page?.let { document.finishPage(it) }
        page = null
    }

    private suspend fun newPage(documentLabel: String?, index: Boolean) {
        coroutineContext.ensureActive()
        finishPage()
        pageIndex++
        page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageIndex + 1).create())
        canvas.drawColor(if (palette.pageBackground == Color.WHITE) palette.cream else palette.pageBackground)
        fill.color = palette.cream
        val card = RectF(margin, margin, width - margin, cardBottom)
        canvas.drawRoundRect(card, 4f, 4f, fill)
        canvas.drawRoundRect(card, 4f, 4f, outline)
        y = margin + padding
        if (index) {
            drawLine("فهرس المرضى", heading)
            y += gap
        }
        footer.color = palette.text
        val label = documentLabel?.let { "$it · ${pageIndex + 1}" } ?: "${pageIndex + 1}"
        canvas.drawText(directed(label), width / 2f, cardBottom - padding - footer.fontMetrics.bottom, footer)
    }

    private suspend fun indexText(text: String, paint: Paint, documentLabel: String?) {
        PdfTextWrapper.wrap(text, paint, right - left).forEach { line ->
            if (y + PdfTextWrapper.lineHeight(paint) > bottom) newPage(documentLabel, index = true)
            drawLine(line, paint)
        }
        y += gap
    }

    private fun drawLine(text: String, paint: Paint) {
        canvas.drawText(directed(text), right, y - paint.fontMetrics.top, paint)
        y += PdfTextWrapper.lineHeight(paint)
    }

    private fun directed(text: String): String = bidi.unicodeWrap(text, TextDirectionHeuristics.FIRSTSTRONG_LTR)
}
