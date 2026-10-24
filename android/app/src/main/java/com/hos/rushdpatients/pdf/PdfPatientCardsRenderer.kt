package com.hos.rushdpatients.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.data.model.TaskPriority
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.domain.report.ReportRosterLabel
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/** Inset A5 cards with a two-column introduction and readable continuation pages. */
internal class PdfPatientCardsRenderer(private val fonts: PdfFonts) {
    private val width = PdfPaperSize.A5.widthPoints
    private val height = PdfPaperSize.A5.heightPoints
    private val margin = 15f * 72f / 25.4f
    private val topMargin = 20f * 72f / 25.4f
    private val bottomMargin = 10f * 72f / 25.4f
    private val padding = 8f
    private val gap = 6f
    private val left = margin + padding
    private val right = width - margin - padding
    private val cardBottom = height - bottomMargin
    private val bidi = BidiFormatter.getInstance()
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f
        typeface = fonts.regular
        textAlign = Paint.Align.RIGHT
    }
    private val heading = Paint(body).apply {
        typeface = fonts.bold
        textSize = 11f
    }
    private val name = Paint(heading).apply { textSize = 14f; textAlign = Paint.Align.CENTER }
    private val navigation = Paint(heading).apply { textSize = 11f; textAlign = Paint.Align.CENTER }
    private val urgentHeading = Paint(heading)
    private val footer = Paint(body).apply { textSize = 8f; textAlign = Paint.Align.CENTER }
    private val bottom: Float get() = cardBottom - padding - 2 * PdfTextWrapper.lineHeight(footer) - gap -
        (if (isIndexPage) 0f else PdfTextWrapper.lineHeight(navigation) + 2 * padding + gap) -
        gap
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
    }
    private val divider = Paint(outline).apply { strokeWidth = PdfRuleWeight.MEDIUM.points }
    private lateinit var palette: PdfPalette.Resolved
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageIndex = -1
    private var isIndexPage = true
    private var pageTotal = 0
    private var generatedLabel = ""
    private var y = margin
    private val canvas: Canvas get() = checkNotNull(page).canvas

    private data class Anchor(val page: Int, val top: Float)
    private data class IndexEntry(val page: Int, val rect: RectF)
    private data class CardPage(val patientIndex: Int, val page: Int, val rects: List<RectF>)
    private data class Section(val title: String, val text: String, val urgent: Boolean = false)

    suspend fun export(
        patients: List<Patient>, doctors: List<Doctor>, summary: ReportSummary,
        residentNames: Map<String, String>, supervisorNames: Map<String, String>,
        options: PdfExportOptions, outputFile: File, documentLabel: String?
    ): File {
        val count = render(patients, doctors, summary, residentNames, supervisorNames,
            options, outputFile, documentLabel, totalPages = 0)
        PdfPatientCardsRenderer(fonts).render(patients, doctors, summary, residentNames,
            supervisorNames, options, outputFile, documentLabel, totalPages = count)
        return outputFile
    }

    private suspend fun render(
        patients: List<Patient>, doctors: List<Doctor>, summary: ReportSummary,
        residentNames: Map<String, String>, supervisorNames: Map<String, String>,
        options: PdfExportOptions, outputFile: File, documentLabel: String?, totalPages: Int
    ): Int {
        pageTotal = totalPages
        generatedLabel = PdfReportContext.createdAt(options)
        // A5 portrait is enforced here for every caller, independently of saved table options.
        palette = PdfPalette.resolve(options)
        body.color = palette.text
        heading.color = if (options.darkMode) palette.text else palette.titleHeader
        urgentHeading.color = if (options.darkMode) 0xFFFFB4AB.toInt() else 0xFF8B1E1E.toInt()
        navigation.color = palette.text
        name.color = Color.WHITE
        outline.color = palette.border
        divider.color = palette.ruleColor
        divider.strokeWidth = palette.ruleWidth
        val entries = mutableListOf<IndexEntry>()
        val destinations = mutableListOf<Anchor>()
        val links = mutableListOf<PdfInternalLinks.Link>()
        val cardPages = mutableListOf<CardPage>()
        try {
            newPage(documentLabel, index = true)
            indexText("فهرس المرضى", Paint(heading).apply { textSize = 14f }, documentLabel)
            val metadataPaint = Paint(body).apply { textSize = 9f }
            indexText(PdfReportContext.lines(summary).joinToString(" · "), metadataPaint, documentLabel)
            indexText("${summary.patientCount} مريض · ${summary.psychoCount} حالة نفسية · ${summary.escortCount} مرافق", body, documentLabel)
            indexText(ReportRosterLabel.forDoctors(doctors), heading, documentLabel)
            indexText(doctors.joinToString(" / ") { it.fullName }, body, documentLabel)
            indexText("اضغط المريض لفتح البطاقة؛ زر الفهرس يعيدك إلى صفحته.", metadataPaint, documentLabel)
            patients.forEach { patient ->
                coroutineContext.ensureActive()
                val lines = PdfTextWrapper.wrap("${patient.sortOrder} · ${patient.name}", heading, right - left)
                val rowHeight = lines.size * PdfTextWrapper.lineHeight(heading) + 2 * padding
                if (y + rowHeight > bottom) newPage(documentLabel, index = true)
                check(y + rowHeight <= bottom) { "اسم المريض أطول من مساحة صفحة الفهرس" }
                val top = y
                lines.forEach { drawLine(it, heading) }
                y = top + rowHeight
                entries += IndexEntry(pageIndex, RectF(margin, top, width - margin, y))
                y += gap
            }
            patients.forEachIndexed { index, patient ->
                coroutineContext.ensureActive()
                val entry = entries[index]
                fun cardHeader() {
                    val lines = PdfTextWrapper.wrap(patient.name, name, right - left)
                    val h = lines.size * PdfTextWrapper.lineHeight(name) + 2 * padding
                    check(y + h + gap + 2 * PdfTextWrapper.lineHeight(heading) <= bottom) {
                        "اسم المريض أطول من مساحة البطاقة"
                    }
                    val top = y
                    fill.color = palette.titleHeader
                    canvas.drawRect(margin, y, width - margin, y + h, fill)
                    lines.forEach { line ->
                        canvas.drawText(directed(line), width / 2f, y - name.fontMetrics.top, name)
                        y += PdfTextWrapper.lineHeight(name)
                    }
                    y = top + h
                    links += PdfInternalLinks.Link(pageIndex, RectF(margin, top, width - margin, y), entry.page, entry.rect.top, fitPage = true)
                    y += gap
                    cardPages += CardPage(index, pageIndex, drawNavigation(index, patients.size))
                }
                newPage(documentLabel, index = false)
                destinations += Anchor(pageIndex, topMargin)
                cardHeader()
                // Admission on the physical left, patient identity on the right.
                val columnGap = 12f
                val columnWidth = (right - left - columnGap) / 2f
                val admitLines = PdfTextWrapper.wrap(PdfPatientContent.buildAdmitCell(patient), body, columnWidth)
                val personalLines = PdfTextWrapper.wrap(PdfPatientContent.buildPatientCell(patient), body, columnWidth)
                val lineHeight = PdfTextWrapper.lineHeight(body)
                fun introHeadings() {
                    if (y + PdfTextWrapper.lineHeight(heading) + gap + lineHeight > bottom) {
                        error("اسم المريض أطول من مساحة البطاقة")
                    }
                    canvas.drawText(directed("القبول"), left + columnWidth, y - heading.fontMetrics.top, heading)
                    canvas.drawText(directed("بيانات المريض"), right, y - heading.fontMetrics.top, heading)
                    y += PdfTextWrapper.lineHeight(heading) + gap
                }
                introHeadings()
                for (lineIndex in 0 until maxOf(admitLines.size, personalLines.size)) {
                    if (y + lineHeight > bottom) {
                        newPage(documentLabel, index = false)
                        cardHeader()
                        introHeadings()
                    }
                    admitLines.getOrNull(lineIndex)?.let { line ->
                        canvas.drawText(directed(line), left + columnWidth, y - body.fontMetrics.top, body)
                    }
                    personalLines.getOrNull(lineIndex)?.let { line ->
                        canvas.drawText(directed(line), right, y - body.fontMetrics.top, body)
                    }
                    y += lineHeight
                }
                y += gap
                val sections = listOf(
                    Section("الفريق المسؤول", PdfPatientContent.buildSupervisorResidentCell(patient, residentNames, supervisorNames)),
                    Section(PdfStrings.HEADER_CONDITION, PdfPatientContent.buildDiagnosisCell(patient)),
                    Section(PdfStrings.HEADER_TREATMENT, patient.treatmentPlan),
                    Section("المتابعة", patient.followUp)
                ) + PatientTasks.orderedForDisplay(patient.tasks, options.generatedAtEpochMillis).map { task ->
                    val metadata = PatientTasks.metadata(task, residentNames + supervisorNames,
                        includeUnassigned = false, includeRoutinePriority = false)
                    Section("المهام · ${PatientTasks.statusLabel(task, options.generatedAtEpochMillis)}",
                        listOf(task.description, metadata).filter { it.isNotBlank() }.joinToString("\n"),
                        urgent = PatientTasks.overdue(task, options.generatedAtEpochMillis) ||
                            (!task.done && task.priority == TaskPriority.HIGH))
                } + Section("التحاليل", patient.labs)
                sections.filter { it.text.isNotBlank() }.forEach { section ->
                    val title = section.title
                    val text = section.text
                    val titlePaint = if (section.urgent) urgentHeading else heading
                    val lines = PdfTextWrapper.wrap(text, body, right - left)
                    val labelHeight = PdfTextWrapper.lineHeight(heading)
                    if (y + gap + labelHeight + PdfTextWrapper.lineHeight(body) > bottom) {
                        newPage(documentLabel, index = false)
                        cardHeader()
                    }
                    canvas.drawLine(left, y, right, y, divider)
                    y += gap
                    drawLine(title, titlePaint)
                    lines.forEach { line ->
                        if (y + PdfTextWrapper.lineHeight(body) > bottom) {
                            newPage(documentLabel, index = false)
                            cardHeader()
                            drawLine("$title (تابع)", titlePaint)
                        }
                        drawLine(line, body)
                    }
                    y += gap
                }
            }
            entries.forEachIndexed { index, entry ->
                val destination = destinations[index]
                links += PdfInternalLinks.Link(entry.page, entry.rect, destination.page, destination.top, fitPage = true)
            }
            cardPages.forEach { card ->
                val indexEntry = entries[card.patientIndex]
                val previous = destinations.getOrNull(card.patientIndex - 1)
                val next = destinations.getOrNull(card.patientIndex + 1)
                previous?.let { links += PdfInternalLinks.Link(card.page, card.rects[0], it.page, it.top, fitPage = true) }
                links += PdfInternalLinks.Link(card.page, card.rects[1], indexEntry.page, indexEntry.rect.top, fitPage = true)
                next?.let { links += PdfInternalLinks.Link(card.page, card.rects[2], it.page, it.top, fitPage = true) }
            }
            finishPage()
            if (pageTotal > 0) {
                check(pageTotal == pageIndex + 1) { "تغير عدد صفحات التقرير؛ أعد التصدير" }
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { document.writeTo(it) }
                coroutineContext.ensureActive()
                PdfInternalLinks.append(outputFile, links, height.toFloat())
            }
            return pageIndex + 1
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
        isIndexPage = index
        page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageIndex + 1).create())
        canvas.drawColor(palette.pageBackground)
        fill.color = palette.pageBackground
        val card = RectF(margin, topMargin, width - margin, cardBottom)
        canvas.drawRoundRect(card, 4f, 4f, fill)
        canvas.drawRoundRect(card, 4f, 4f, outline)
        y = topMargin + padding
        footer.color = palette.text
        val label = listOfNotNull(documentLabel, "ShiftReport", "صفحة ${pageIndex + 1} من $pageTotal").joinToString(" · ")
        val firstBaseline = cardBottom - padding - PdfTextWrapper.lineHeight(footer) - footer.fontMetrics.bottom
        canvas.drawText(directed(label), width / 2f, firstBaseline, footer)
        canvas.drawText(directed(generatedLabel), width / 2f, firstBaseline + PdfTextWrapper.lineHeight(footer), footer)
    }

    /** Same patient-to-patient destinations on the first and every continuation page. */
    private fun drawNavigation(patientIndex: Int, total: Int): List<RectF> {
        val navBottom = cardBottom - padding - 2 * PdfTextWrapper.lineHeight(footer) - gap
        val navTop = navBottom - PdfTextWrapper.lineHeight(navigation) - 2 * padding
        val cellWidth = (right - left) / 3f
        val labels = listOf("السابق", "الفهرس", "التالي")
        return labels.mapIndexed { index, label ->
            val end = right - index * cellWidth
            val rect = RectF(end - cellWidth, navTop, end, navBottom)
            val active = when (index) { 0 -> patientIndex > 0; 2 -> patientIndex < total - 1; else -> true }
            val paint = Paint(navigation).apply { isUnderlineText = active }
            canvas.drawText(directed(if (active) label else "—"), rect.centerX(),
                navTop + padding - paint.fontMetrics.top, paint)
            rect
        }
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
