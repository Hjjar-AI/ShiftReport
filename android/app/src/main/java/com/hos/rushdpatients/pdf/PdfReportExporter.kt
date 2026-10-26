package com.hos.rushdpatients.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.graphics.pdf.PdfDocument
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.domain.report.ReportRosterLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReportExporter @Inject constructor(@ApplicationContext context: Context) {

    private val fonts by lazy { PdfFonts(context) }

    private val exportMutex = Mutex()
    private var documentLabel: String? = null
    private var reportDate: java.time.LocalDate = com.hos.rushdpatients.util.ShiftDate.current()

    private var pageWidth = 595f
    private var pageHeight = 842f
    private val marginLeft = 8.5f
    private val marginRight = 8.5f
    private val marginTop = 8.5f
    private val marginBottom = 8.5f

    private var contentLeft = marginLeft
    private var contentRight = pageWidth - marginRight
    private var contentWidth = contentRight - contentLeft

    // Physical left-to-right order is reversed so the logical table reads right-to-left.
    private val colWeights = floatArrayOf(4f, 16f, 13f, 12f, 15f, 30f, 34f).reversedArray()
    private var colWidths = FloatArray(colWeights.size)
    private var colX = FloatArray(colWeights.size)
    private var palette = PdfPalette.resolve(PdfExportOptions())
    private val bidiFormatter = BidiFormatter.getInstance()

    private val titleBandHeight = 20f
    private val summaryBandHeight: Float
        get() = PdfTextWrapper.lineHeight(summaryPaint) + 2 * cellPadding
    private val headerMinHeight = 14f
    private val rowMinHeight = 14f
    // One PDF point clears even the outer table stroke while maximizing clinical text space.
    private val cellPadding = 1f

    private val dateFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar"))

    private val titlePaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f
        typeface = fonts.bold
        textAlign = Paint.Align.CENTER
    } }
    private val summaryPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 10f
        typeface = fonts.bold
        textAlign = Paint.Align.CENTER
    } }
    private val headerPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9.5f
        typeface = fonts.bold
        textAlign = Paint.Align.CENTER
    } }
    private val bodyPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 9f
        typeface = fonts.regular
        textAlign = Paint.Align.CENTER
    } }
    private val bodyCenterPaint by lazy { Paint(bodyPaint).apply {
        textAlign = Paint.Align.CENTER
    } }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = PdfRuleWeight.MEDIUM.points
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    suspend fun export(
        patients: List<Patient>,
        doctors: List<Doctor>,
        summary: ReportSummary,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>,
        options: PdfExportOptions = PdfExportOptions(),
        elegant: Boolean = false,
        outputFile: File,
        documentLabel: String? = null
    ): File = exportMutex.withLock {
        withContext(Dispatchers.IO) {
            if (elegant) {
                return@withContext PdfPatientCardsRenderer(fonts).export(
                    patients, doctors, summary, residentNames, supervisorNames,
                    options, outputFile, documentLabel
                )
            }
            configure(options)
            reportDate = summary.shiftDate
            this@PdfReportExporter.documentLabel = documentLabel
            val document = PdfDocument()
            try {
                var pageNumber = 1
                var page = document.startPage(
                    PdfDocument.PageInfo.Builder(
                        pageWidth.toInt(),
                        pageHeight.toInt(),
                        pageNumber
                    ).create()
                )
                var canvas = page.canvas
                drawPageBackground(canvas)

                var y = marginTop

                val rosterTitle = "${ReportRosterLabel.forDoctors(doctors)}: " + doctors.joinToString(" + ") { it.fullName }
                val contextPaint = Paint(titlePaint).apply { textSize = 8.5f }
                val contextLines = PdfTextWrapper.wrap(PdfReportContext.lines(summary).joinToString(" · "),
                    contextPaint, contentWidth - 2 * cellPadding)
                val contextHeight = contextLines.size * PdfTextWrapper.lineHeight(contextPaint) + 2 * cellPadding
                val titleLines = PdfTextWrapper.wrap(rosterTitle, titlePaint, contentWidth - 2 * cellPadding)
                val titleLineHeight = PdfTextWrapper.lineHeight(titlePaint)
                val titleLinesPerPage = ((pageHeight - marginTop - marginBottom -
                    2 * summaryBandHeight - headerMinHeight - contextHeight - 4 * cellPadding) / titleLineHeight)
                    .toInt().coerceAtLeast(1)
                titleLines.chunked(titleLinesPerPage).forEachIndexed { index, lines ->
                    if (index > 0) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(
                            pageWidth.toInt(), pageHeight.toInt(), pageNumber).create())
                        canvas = page.canvas
                        drawPageBackground(canvas)
                        y = marginTop
                    }
                    y += drawTitleBand(canvas, y, lines)
                }
                y += drawTitleBand(canvas, y, contextLines, contextPaint)

                drawSummaryBand(canvas, y, summary)
                y += summaryBandHeight

                val headerHeight = drawHeaderRow(canvas, y)
                y += headerHeight

                val timestampPaint = Paint(summaryPaint).apply { textSize = 8.5f; textAlign = Paint.Align.RIGHT }
                val timestampGap = 30f
                val timestampSpace = timestampGap + PdfTextWrapper.lineHeight(timestampPaint)
                val bottomLimit = pageHeight - marginBottom - summaryBandHeight - 2f - timestampSpace

                val stripes = PdfTableStriping.colors(patients, palette)
                patients.forEachIndexed { index, patient ->
                    val bgColor: Int? = stripes[index]
                    val cells = layoutPatientRow(patient, residentNames, supervisorNames)
                    val lineHeight = PdfTextWrapper.lineHeight(bodyPaint)
                    val totalLines = cells.maxOf { it.lines.size }.coerceAtLeast(1)
                    val totalHeight = maxOf(rowMinHeight, totalLines * lineHeight + 2 * cellPadding)
                    val freshPageHeight = bottomLimit - marginTop - headerHeight
                    var offset = 0
                    while (offset < totalLines) {
                        val remainingHeight = bottomLimit - y
                        // Keep ordinary rows together; continue only rows taller than a page.
                        var capacity = if (remainingHeight < rowMinHeight ||
                            (offset == 0 && totalHeight <= freshPageHeight && totalHeight > remainingHeight)) 0
                        else ((remainingHeight - 2 * cellPadding) / lineHeight).toInt()
                        if (capacity < 1) {
                            document.finishPage(page)
                            pageNumber++
                            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create())
                            canvas = page.canvas
                            drawPageBackground(canvas)
                            y = marginTop
                            y += drawHeaderRow(canvas, y)
                            capacity = ((bottomLimit - y - 2 * cellPadding) / lineHeight).toInt()
                        }
                        check(capacity > 0) { "حجم الورق لا يتسع للجدول؛ اختر ورقاً أكبر" }
                        val count = minOf(capacity, totalLines - offset)
                        val height = maxOf(rowMinHeight, count * lineHeight + 2 * cellPadding)
                        drawPatientRowSlice(canvas, y, height, patient, cells, offset, count, bgColor)
                        y += height
                        offset += count
                    }
                }

                if (y + summaryBandHeight + timestampSpace > pageHeight - marginBottom - 2f) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(
                        PdfDocument.PageInfo.Builder(
                            pageWidth.toInt(),
                            pageHeight.toInt(),
                            pageNumber
                        ).create()
                    )
                    canvas = page.canvas
                    drawPageBackground(canvas)
                    y = marginTop
                }
                drawSummaryBand(canvas, y, summary)
                y += summaryBandHeight + timestampGap
                canvas.drawText(bidiFormatter.unicodeWrap(PdfReportContext.createdAt(options)), contentRight,
                    y - timestampPaint.fontMetrics.top, timestampPaint)

                document.finishPage(page)
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { document.writeTo(it) }
                outputFile
            } finally {
                document.close()
            }
        }
    }

    private fun configure(options: PdfExportOptions) {
        val paper = options.paperSize
        val portraitWidth = paper.widthPoints.toFloat()
        val portraitHeight = paper.heightPoints.toFloat()
        pageWidth = portraitWidth
        pageHeight = portraitHeight
        contentLeft = marginLeft
        contentRight = pageWidth - marginRight
        contentWidth = contentRight - contentLeft
        val total = colWeights.sum()
        colWidths = FloatArray(colWeights.size) { i -> contentWidth * colWeights[i] / total }
        colX = FloatArray(colWeights.size).also { positions ->
            positions[0] = contentLeft
            for (i in 1 until colWeights.size) {
                positions[i] = positions[i - 1] + colWidths[i - 1]
            }
        }

        palette = PdfPalette.resolve(options)
        titlePaint.textSize = 11f
        summaryPaint.textSize = 10f
        headerPaint.textSize = 9.5f
        bodyPaint.textSize = 9f
        bodyCenterPaint.textSize = bodyPaint.textSize
        summaryPaint.color = palette.text
        bodyPaint.color = palette.text
        bodyCenterPaint.color = palette.text
        borderPaint.color = palette.ruleColor
        borderPaint.strokeWidth = palette.ruleWidth
    }

    private fun drawPageBackground(canvas: Canvas) {
        canvas.drawColor(palette.pageBackground)
        documentLabel?.let { label ->
            val paint = Paint(bodyPaint).apply { textSize = 8f; textAlign = Paint.Align.CENTER }
            canvas.drawText(bidiFormatter.unicodeWrap(label), pageWidth / 2f, pageHeight - 3f, paint)
        }
    }

    private fun drawTitleBand(canvas: Canvas, y: Float, lines: List<String>, paint: Paint = titlePaint): Float {
        val lineHeight = PdfTextWrapper.lineHeight(paint)
        val height = maxOf(if (paint === titlePaint) titleBandHeight else 0f, lines.size * lineHeight + 2 * cellPadding)
        fillPaint.color = palette.titleHeader
        canvas.drawRect(contentLeft, y, contentRight, y + height, fillPaint)
        lines.forEachIndexed { index, line ->
            drawCenteredFittedText(
                canvas = canvas, text = line, centerX = contentLeft + contentWidth / 2f,
                baseline = y + cellPadding - paint.fontMetrics.top + index * lineHeight,
                paint = paint, maxWidth = contentWidth - 2 * cellPadding
            )
        }
        canvas.drawLine(contentLeft, y + height, contentRight, y + height, borderPaint)
        return height
    }

    private fun drawSummaryBand(
        canvas: Canvas,
        y: Float,
        summary: ReportSummary
    ) {
        fillPaint.color = palette.summary
        canvas.drawRect(contentLeft, y, contentRight, y + summaryBandHeight, fillPaint)

        val c1 = contentLeft
        val c1End = contentLeft + contentWidth / 3f
        val c2 = c1End
        val c2End = contentLeft + contentWidth * 2f / 3f
        val c3 = c2End
        val c3End = contentRight

        val baseline = y + summaryBandHeight / 2f -
                (summaryPaint.fontMetrics.ascent + summaryPaint.fontMetrics.descent) / 2f

        val countText = buildString {
            append(PdfStrings.PATIENTS_COUNT_LABEL)
            append(": ")
            append(summary.patientCount)
            if (summary.psychoCount > 0) {
                append(" (")
                append(PdfStrings.PSYCHO_SHORT)
                append(": ")
                append(summary.psychoCount)
                append(')')
            }
        }
        val escortsText = "${PdfStrings.ESCORTS_COUNT_LABEL}: ${summary.escortCount}"
        val dateText = "${PdfStrings.DATE_LABEL}:    ${summary.shiftDate.format(dateFmt)}"

        drawCenteredFittedText(
            canvas, countText, (c1 + c1End) / 2f, baseline, summaryPaint,
            c1End - c1 - 2 * cellPadding
        )
        drawCenteredFittedText(
            canvas, escortsText, (c2 + c2End) / 2f, baseline, summaryPaint,
            c2End - c2 - 2 * cellPadding
        )
        drawCenteredFittedText(
            canvas, dateText, (c3 + c3End) / 2f, baseline, summaryPaint,
            c3End - c3 - 2 * cellPadding
        )

        canvas.drawLine(contentLeft, y + summaryBandHeight, contentRight, y + summaryBandHeight, borderPaint)
    }

    private fun drawHeaderRow(canvas: Canvas, y: Float): Float {
        val headerTexts = arrayOf(
            PdfStrings.HEADER_ID,
            PdfStrings.HEADER_PATIENT,
            PdfStrings.HEADER_CONDITION,
            "${PdfStrings.HEADER_ADMIT_NUM}\n${PdfStrings.SEPARATOR}\n${PdfStrings.HEADER_ADMIT_DAYS}",
            "${PdfStrings.HEADER_SUPERVISOR}\n${PdfStrings.SEPARATOR}\n${PdfStrings.HEADER_RESIDENT}",
            PdfStrings.HEADER_TREATMENT,
            PdfStrings.HEADER_NOTES
        ).reversedArray()

        val lineHeight = PdfTextWrapper.lineHeight(headerPaint)
        val maxLines = headerTexts.maxOf { it.count { c -> c == '\n' } + 1 }
        val height = (maxLines * lineHeight + cellPadding * 2)
            .coerceAtLeast(headerMinHeight)

        fillPaint.color = palette.tableHeader
        canvas.drawRect(contentLeft, y, contentRight, y + height, fillPaint)

        for (i in headerTexts.indices) {
            val xStart = colX[i]
            val xEnd = xStart + colWidths[i]
            val lines = headerTexts[i].split('\n')
            val totalTextHeight = lines.size * lineHeight
            var baseline = y + (height - totalTextHeight) / 2f - headerPaint.fontMetrics.top
            for (line in lines) {
                drawCenteredFittedText(
                    canvas = canvas,
                    text = line,
                    centerX = (xStart + xEnd) / 2f,
                    baseline = baseline,
                    paint = headerPaint,
                    maxWidth = xEnd - xStart - 2 * cellPadding
                )
                baseline += lineHeight
            }
        }
        canvas.drawLine(contentLeft, y + height, contentRight, y + height, borderPaint)
        return height
    }

    private data class CellLines(val lines: List<String>, val paint: Paint)

    private fun layoutPatientRow(
        patient: Patient,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>
    ): List<CellLines> {
        val cells = listOf(
            patient.sortOrder.toString() to bodyCenterPaint,
            PdfPatientContent.buildPatientCell(patient, reportDate = reportDate) to bodyCenterPaint,
            PdfPatientContent.buildDiagnosisCell(patient) to bodyCenterPaint,
            PdfPatientContent.buildAdmitCell(patient, reportDate) to bodyCenterPaint,
            PdfPatientContent.buildSupervisorResidentCell(patient, residentNames, supervisorNames) to bodyCenterPaint,
            patient.treatmentPlan to bodyCenterPaint,
            PdfPatientContent.buildNotesCell(patient) to bodyCenterPaint
        ).asReversed()
        return cells.mapIndexed { index, (text, paint) ->
            val maxWidth = colWidths[index] - 2 * cellPadding
            CellLines(PdfTextWrapper.wrap(text, paint, maxWidth), paint)
        }
    }

    private fun drawPatientRowSlice(
        canvas: Canvas,
        y: Float,
        height: Float,
        patient: Patient,
        cells: List<CellLines>,
        lineOffset: Int,
        lineCount: Int,
        bgColor: Int?
    ) {
        if (bgColor != null) {
            fillPaint.color = bgColor
            canvas.drawRect(contentLeft, y, contentRight, y + height, fillPaint)
        }

        val lineHeight = PdfTextWrapper.lineHeight(bodyPaint)
        for (i in cells.indices) {
            val paint = cells[i].paint
            val xStart = colX[i]
            val xEnd = xStart + colWidths[i]
            val lines = if (i == cells.lastIndex) {
                listOf(if (lineOffset == 0) patient.sortOrder.toString() else "↳${patient.sortOrder}")
            } else {
                cells[i].lines.drop(lineOffset).take(lineCount)
            }
            val totalHeight = lines.size * lineHeight
            var baseline = y + (height - totalHeight) / 2f - paint.fontMetrics.top
            val xAnchor = when (paint.textAlign) {
                Paint.Align.CENTER -> (xStart + xEnd) / 2f
                Paint.Align.RIGHT -> xEnd - cellPadding
                else -> xStart + cellPadding
            }
            for (line in lines) {
                if (i == cells.lastIndex) drawCenteredFittedText(canvas, line, xAnchor, baseline, paint, xEnd - xStart - 2 * cellPadding)
                else canvas.drawText(autoDirection(line), xAnchor, baseline, paint)
                baseline += lineHeight
            }
        }
        canvas.drawLine(contentLeft, y + height, contentRight, y + height, borderPaint)
    }

    private fun autoDirection(text: String): String = bidiFormatter.unicodeWrap(
        text,
        TextDirectionHeuristics.FIRSTSTRONG_LTR
    )

    private fun drawCenteredFittedText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baseline: Float,
        paint: Paint,
        maxWidth: Float
    ) {
        val directed = autoDirection(text)
        val originalSize = paint.textSize
        val measuredWidth = paint.measureText(directed)
        if (measuredWidth > maxWidth && measuredWidth > 0f) {
            paint.textSize = (originalSize * maxWidth / measuredWidth).coerceAtLeast(5.5f)
        }
        canvas.drawText(directed, centerX, baseline, paint)
        paint.textSize = originalSize
    }
}
