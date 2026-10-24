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

/** Inset A5 cards with a three-column introduction and readable continuation pages. */
internal class PdfPatientCardsRenderer(private val fonts: PdfFonts) {
    private val width = PdfPaperSize.A5.widthPoints
    private val height = PdfPaperSize.A5.heightPoints
    private val margin = 5f * 72f / 25.4f
    private val topMargin = 20f * 72f / 25.4f
    private val bottomMargin = 10f * 72f / 25.4f
    private val padding = 8f
    private val titlePadding = 3f
    private val navigationPadding = 3f
    private val navigationInset = 3f
    private val gap = 6f
    private val left = margin + padding
    private val right = width - margin - padding
    private val cardBottom = height - bottomMargin
    private val bidi = BidiFormatter.getInstance()
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11.5f
        typeface = fonts.regular
        textAlign = Paint.Align.RIGHT
    }
    private val heading = Paint(body).apply {
        typeface = fonts.bold
        textSize = 12f
    }
    private val name = Paint(heading).apply { textSize = 15f; textAlign = Paint.Align.CENTER }
    private val navigation = Paint(heading).apply { textSize = 12f; textAlign = Paint.Align.CENTER }
    private val urgentHeading = Paint(heading)
    private val footer = Paint(body).apply { textSize = 8.5f; textAlign = Paint.Align.CENTER }
    private val bottom: Float get() = if (isIndexPage) cardBottom - padding else
        cardBottom - navigationInset - PdfTextWrapper.lineHeight(navigation) - 2 * navigationPadding - gap
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
    private data class BadgeCell(val width: Float, val lines: List<String>, val foreground: Int, val background: Int, val border: Int)
    private data class Section(val title: String, val text: String, val urgent: Boolean = false, val keepTogether: Boolean = false, val alwaysShow: Boolean = false)

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
            val metadataPaint = Paint(body).apply { textSize = 11.5f }
            indexText("${summary.shiftDate} · ${summary.patientCount} مريض · ${summary.psychoCount} حالة نفسية · ${summary.escortCount} مرافق", metadataPaint, documentLabel)
            if (doctors.isNotEmpty()) indexText(ReportRosterLabel.forDoctors(doctors) + ": " +
                doctors.joinToString(" / ") { it.fullName }, metadataPaint, documentLabel)
            val indexGap = 8f
            val tilePadding = 2f
            val rowHeight = 28f
            val tileWidth = (right - left - 2 * indexGap) / 3f
            patients.chunked(3).forEach { row ->
                coroutineContext.ensureActive()
                val fitted = row.map { patient ->
                    val text = "${patient.sortOrder} · ${patient.name}"
                    val paint = Paint(heading).apply { textSize = 14f }
                    val availableWidth = tileWidth - 2 * tilePadding
                    val availableHeight = rowHeight - 2 * tilePadding
                    fun fits(): Boolean = PdfTextWrapper.wrap(text, paint, availableWidth).size *
                        PdfTextWrapper.lineHeight(paint) <= availableHeight
                    if (!fits()) {
                        var low = 1f
                        var high = 14f
                        repeat(18) {
                            paint.textSize = (low + high) / 2f
                            if (fits()) low = paint.textSize else high = paint.textSize
                        }
                        paint.textSize = low
                    }
                    check(fits()) { "اسم المريض أطول من مساحة خلية الفهرس" }
                    paint to PdfTextWrapper.wrap(text, paint, availableWidth)
                }
                if (y + rowHeight > bottom) {
                    newPage(documentLabel, index = true)
                }
                check(y + rowHeight <= bottom) { "اسم المريض أطول من مساحة صفحة الفهرس" }
                val top = y
                fitted.forEachIndexed { column, (indexLink, wrapped) ->
                    val end = right - column * (tileWidth + indexGap)
                    val rect = RectF(end - tileWidth, top, end, top + rowHeight)
                    canvas.drawRoundRect(rect, 3f, 3f, outline)
                    val textHeight = wrapped.size * PdfTextWrapper.lineHeight(indexLink)
                    canvas.save()
                    canvas.clipRect(rect.left + tilePadding, rect.top + tilePadding,
                        rect.right - tilePadding, rect.bottom - tilePadding)
                    wrapped.forEachIndexed { lineIndex, line ->
                        canvas.drawText(directed(line), end - tilePadding,
                            top + (rowHeight - textHeight) / 2f + lineIndex * PdfTextWrapper.lineHeight(indexLink) - indexLink.fontMetrics.top, indexLink)
                    }
                    canvas.restore()
                    entries += IndexEntry(pageIndex, rect)
                }
                y = top + rowHeight + 1f
            }
            patients.forEachIndexed { index, patient ->
                coroutineContext.ensureActive()
                val entry = entries[index]
                fun cardHeader() {
                    val lines = PdfTextWrapper.wrap(patient.name, name, right - left)
                    val h = lines.size * PdfTextWrapper.lineHeight(name) + 2 * titlePadding
                    check(y + h + gap + 2 * PdfTextWrapper.lineHeight(heading) <= bottom) {
                        "اسم المريض أطول من مساحة البطاقة"
                    }
                    val top = y
                    fill.color = palette.titleHeader
                    canvas.drawRect(margin, y, width - margin, y + h, fill)
                    y += titlePadding
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
                // RTL introduction: patient identity, admission, responsible team.
                val columnGap = 10f
                val columnWidth = (right - left - 2 * columnGap) / 3f
                val introColumns = listOf(
                    Triple("الفريق المسؤول", PdfPatientContent.buildSupervisorResidentCell(patient, residentNames, supervisorNames), left + columnWidth),
                    Triple("القبول", PdfPatientContent.buildAdmitCell(patient), left + 2 * columnWidth + columnGap),
                    Triple("بيانات المريض", PdfPatientContent.buildPatientCell(patient, includeName = false), right)
                )
                val introLines = introColumns.map { PdfTextWrapper.wrap(it.second, body, columnWidth) }
                val lineHeight = PdfTextWrapper.lineHeight(body)
                fun introHeadings() {
                    if (y + PdfTextWrapper.lineHeight(heading) + gap + lineHeight > bottom) {
                        error("اسم المريض أطول من مساحة البطاقة")
                    }
                    introColumns.forEach { (title, _, end) ->
                        canvas.drawText(directed(title), end, y - heading.fontMetrics.top, heading)
                    }
                    y += PdfTextWrapper.lineHeight(heading) + gap
                }
                introHeadings()
                for (lineIndex in 0 until (introLines.maxOfOrNull { it.size } ?: 0)) {
                    if (y + lineHeight > bottom) {
                        newPage(documentLabel, index = false)
                        cardHeader()
                        introHeadings()
                    }
                    introLines.forEachIndexed { column, lines ->
                        lines.getOrNull(lineIndex)?.let { line ->
                            canvas.drawText(directed(line), introColumns[column].third, y - body.fontMetrics.top, body)
                        }
                    }
                    y += lineHeight
                }
                y += gap
                val tasks = PatientTasks.orderedForDisplay(patient.tasks, options.generatedAtEpochMillis)
                val taskText = tasks.groupBy { PatientTasks.statusLabel(it, options.generatedAtEpochMillis) }
                    .entries.joinToString("\n") { (status, group) ->
                        "$status (${group.size})\n" + group.joinToString("\n") { task ->
                            val metadata = PatientTasks.metadata(task, residentNames + supervisorNames,
                                includeUnassigned = false, includeRoutinePriority = false)
                            "• " + listOf(task.description, metadata).filter { it.isNotBlank() }.joinToString(" — ")
                        }
                    }
                val badgePaint = Paint(body).apply { typeface = fonts.bold }
                val badgeLineHeight = PdfTextWrapper.lineHeight(badgePaint)
                val badgeXPadding = 6f
                val badgeYPadding = 4f
                val badgeGap = 6f
                val visibleBadges = patient.badges.filter { it.text.isNotBlank() }
                val maximumBadgeWidth = if (visibleBadges.size > 1) (right - left - badgeGap) / 2f else right - left
                suspend fun drawBadgeRow(cells: List<BadgeCell>) {
                    val count = cells.maxOf { it.lines.size }
                    val fullHeight = count * badgeLineHeight + 2 * badgeYPadding
                    if (y + fullHeight > bottom) { newPage(documentLabel, index = false); cardHeader() }
                    var offset = 0
                    while (offset < count) {
                        var capacity = ((bottom - y - 2 * badgeYPadding) / badgeLineHeight).toInt()
                        if (capacity < 1) {
                            newPage(documentLabel, index = false); cardHeader()
                            capacity = ((bottom - y - 2 * badgeYPadding) / badgeLineHeight).toInt()
                        }
                        check(capacity > 0) { "لا توجد مساحة لعرض شارة المريض" }
                        val take = minOf(capacity, count - offset)
                        val height = take * badgeLineHeight + 2 * badgeYPadding
                        var end = right
                        cells.forEach { cell ->
                            val lines = cell.lines.drop(offset).take(take)
                            if (lines.isNotEmpty()) {
                                val rect = RectF(end - cell.width, y, end, y + height)
                                val background = Paint(fill).apply { color = cell.background }
                                val border = Paint(outline).apply { color = cell.border }
                                val textPaint = Paint(badgePaint).apply { color = cell.foreground }
                                canvas.drawRoundRect(rect, 5f, 5f, background)
                                canvas.drawRoundRect(rect, 5f, 5f, border)
                                lines.forEachIndexed { lineIndex, line ->
                                    canvas.drawText(directed(line), end - badgeXPadding,
                                        y + (height - lines.size * badgeLineHeight) / 2f + lineIndex * badgeLineHeight - textPaint.fontMetrics.top, textPaint)
                                }
                            }
                            end -= cell.width + badgeGap
                        }
                        y += height + 4f
                        offset += take
                        if (offset < count) { newPage(documentLabel, index = false); cardHeader() }
                    }
                }
                var badgeRow = mutableListOf<BadgeCell>()
                var usedWidth = 0f
                visibleBadges.forEach { badge ->
                    val high = badge.priority == com.hos.rushdpatients.data.model.PatientBadgePriority.HIGH
                    val label = badge.text + if (high) " · عالية" else ""
                    val cellWidth = minOf(maximumBadgeWidth, label.lineSequence().maxOf { badgePaint.measureText(directed(it)) } + 2 * badgeXPadding)
                    if (badgeRow.isNotEmpty() && usedWidth + badgeGap + cellWidth > right - left + 0.01f) {
                        drawBadgeRow(badgeRow); badgeRow = mutableListOf(); usedWidth = 0f
                    }
                    badgeRow += BadgeCell(cellWidth, PdfTextWrapper.wrap(label, badgePaint, cellWidth - 2 * badgeXPadding),
                        if (high) urgentHeading.color else palette.text,
                        if (high) (if (options.darkMode) 0xFF4D2525.toInt() else 0xFFFDE8E8.toInt()) else palette.summary,
                        if (high) urgentHeading.color else palette.border)
                    usedWidth += (if (badgeRow.size > 1) badgeGap else 0f) + cellWidth
                }
                if (badgeRow.isNotEmpty()) drawBadgeRow(badgeRow)
                val sections = listOf(
                    Section("التشخيص · ${patient.diagnosisType.arabicLabel}", patient.initialDiagnosis, alwaysShow = true),
                    Section(PdfStrings.HEADER_TREATMENT, patient.treatmentPlan),
                    Section("المتابعة", patient.followUp),
                    Section("المهام (${tasks.size})", taskText,
                        urgent = tasks.any { PatientTasks.overdue(it, options.generatedAtEpochMillis) ||
                            (!it.done && it.priority == TaskPriority.HIGH) }, keepTogether = true),
                    Section("التحاليل", patient.labs)
                )
                sections.filter { section -> section.alwaysShow || section.text.isNotBlank() }.forEach { section ->
                    val title = section.title
                    val text = section.text
                    val titlePaint = if (section.urgent) urgentHeading else heading
                    val lines = if (text.isBlank()) emptyList() else PdfTextWrapper.wrap(text, body, right - left)
                    val labelHeight = PdfTextWrapper.lineHeight(heading)
                    val requiredHeight = gap + labelHeight + lines.size * PdfTextWrapper.lineHeight(body) + gap
                    val freshTop = topMargin + padding +
                        PdfTextWrapper.wrap(patient.name, name, right - left).size * PdfTextWrapper.lineHeight(name) + 2 * titlePadding + gap
                    val keepOnFreshPage = section.keepTogether && requiredHeight <= bottom - freshTop && y + requiredHeight > bottom
                    if (keepOnFreshPage || y + gap + labelHeight + (if (lines.isEmpty()) 0f else PdfTextWrapper.lineHeight(body)) > bottom) {
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
        val pageTop = if (index) 10f * 72f / 25.4f else topMargin
        if (index) {
            val titlePaint = Paint(heading).apply { textSize = 15f }
            val titleBaseline = pageTop / 2f - (titlePaint.fontMetrics.top + titlePaint.fontMetrics.bottom) / 2f
            canvas.drawText(directed("فهرس المرضى"), right, titleBaseline, titlePaint)
        }
        val card = RectF(margin, pageTop, width - margin, cardBottom)
        canvas.drawRoundRect(card, 4f, 4f, fill)
        canvas.drawRoundRect(card, 4f, 4f, outline)
        y = pageTop + padding
        footer.color = palette.text
        val label = listOfNotNull(documentLabel, "ShiftReport", "${pageIndex + 1}/$pageTotal", generatedLabel).joinToString(" · ")
        val footerPaint = Paint(footer)
        val textWidth = footerPaint.measureText(directed(label))
        if (textWidth > width - 2 * margin) footerPaint.textSize *= (width - 2 * margin) / textWidth
        val baseline = height - bottomMargin / 2f - (footerPaint.fontMetrics.top + footerPaint.fontMetrics.bottom) / 2f
        canvas.drawText(directed(label), width / 2f, baseline, footerPaint)
    }

    /** Same patient-to-patient destinations on the first and every continuation page. */
    private fun drawNavigation(patientIndex: Int, total: Int): List<RectF> {
        val navBottom = cardBottom - navigationInset
        val navTop = navBottom - PdfTextWrapper.lineHeight(navigation) - 2 * navigationPadding
        val cellWidth = (right - left) / 3f
        val labels = listOf("السابق", "الفهرس", "التالي")
        return labels.mapIndexed { index, label ->
            val end = right - index * cellWidth
            val rect = RectF(end - cellWidth, navTop, end, navBottom)
            val active = when (index) { 0 -> patientIndex > 0; 2 -> patientIndex < total - 1; else -> true }
            val paint = Paint(navigation).apply { isUnderlineText = active }
            canvas.drawText(directed(if (active) label else "—"), rect.centerX(),
                navTop + navigationPadding - paint.fontMetrics.top, paint)
            rect
        }
    }

    private suspend fun indexText(text: String, paint: Paint, documentLabel: String?) {
        PdfTextWrapper.wrap(text, paint, right - left).forEach { line ->
            if (y + PdfTextWrapper.lineHeight(paint) > bottom) newPage(documentLabel, index = true)
            drawLine(line, paint)
        }
        y += 3f
    }

    private fun drawLine(text: String, paint: Paint) {
        canvas.drawText(directed(text), right, y - paint.fontMetrics.top, paint)
        y += PdfTextWrapper.lineHeight(paint)
    }

    private fun directed(text: String): String = bidi.unicodeWrap(text, TextDirectionHeuristics.FIRSTSTRONG_LTR)
}
