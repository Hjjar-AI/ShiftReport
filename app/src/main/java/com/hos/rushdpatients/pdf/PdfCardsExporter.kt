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
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.patient.PatientCardStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfCardsExporter @Inject constructor() {

    private val exportMutex = Mutex()
    private val bidi = BidiFormatter.getInstance()

    private var pageWidth = 595f
    private var pageHeight = 842f
    private val margin = 16f
    private var palette = PdfPalette.resolve(PdfExportOptions())
    private var cardStyle = PatientCardStyle.BADGE_HEADER

    private val cardRadius = 10f
    private val cardPadding = 10f
    private val cardGap = 10f
    private val titleBandHeight = 28f
    private val chipHeight = 14f
    private val chipRadius = 4f
    private val chipPadding = 5f
    private val badgeSize = 24f
    private val headerSpacing = 4f
    private val lineSpacing = 2f

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val badgeNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    suspend fun export(
        patients: List<Patient>,
        doctors: List<Doctor>,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>,
        options: PdfExportOptions = PdfExportOptions(),
        outputFile: File
    ): File = exportMutex.withLock {
        withContext(Dispatchers.IO) {
            configure(options)
            val document = PdfDocument()
            try {
                var pageNumber = 1
                var page = document.startPage(
                    PdfDocument.PageInfo.Builder(
                        pageWidth.toInt(), pageHeight.toInt(), pageNumber
                    ).create()
                )
                var canvas = page.canvas
                canvas.drawColor(palette.pageBackground)

                var y = margin
                drawTitleBand(canvas, y, doctors)
                y += titleBandHeight + 6f
                val bottomLimit = pageHeight - margin

                patients.forEach { patient ->
                    val cardHeight = measureCard(patient)
                    if (y + cardHeight > bottomLimit) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(
                            PdfDocument.PageInfo.Builder(
                                pageWidth.toInt(), pageHeight.toInt(), pageNumber
                            ).create()
                        )
                        canvas = page.canvas
                        canvas.drawColor(palette.pageBackground)
                        y = margin
                        drawTitleBand(canvas, y, doctors)
                        y += titleBandHeight + 6f
                    }
                    drawCard(canvas, y, cardHeight, patient, residentNames, supervisorNames)
                    y += cardHeight + cardGap
                }

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
        val pw = paper.widthPoints.toFloat()
        val ph = paper.heightPoints.toFloat()
        if (options.orientation == PdfOrientation.LANDSCAPE) {
            pageWidth = ph
            pageHeight = pw
        } else {
            pageWidth = pw
            pageHeight = ph
        }
        palette = PdfPalette.resolve(options)
        cardStyle = options.patientCardStyle
        namePaint.color = palette.text
        metaPaint.color = palette.text
        bodyPaint.color = palette.text
        sectionTitlePaint.color = palette.titleHeader
        borderPaint.color = palette.border
    }

    private fun drawTitleBand(canvas: Canvas, y: Float, doctors: List<Doctor>) {
        val left = margin
        val right = pageWidth - margin
        fillPaint.color = palette.titleHeader
        canvas.drawRect(left, y, right, y + titleBandHeight, fillPaint)

        val label = PdfStrings.REPORT_TITLE_PREFIX + " " +
                doctors.joinToString(" + ") { it.fullName }
        val baseline = y + titleBandHeight / 2f -
                (titlePaint.fontMetrics.ascent + titlePaint.fontMetrics.descent) / 2f
        canvas.drawText(autoDirection(label), (left + right) / 2f, baseline, titlePaint)
    }

    /** Height of the header area: name + meta + admit, or the badge, whichever is taller. */
    private fun headerHeight(patient: Patient): Float {
        val nameH = PdfTextWrapper.lineHeight(namePaint)
        val metaH = PdfTextWrapper.lineHeight(metaPaint)
        val admitH = if (admitLine(patient).isNotBlank()) metaH else 0f
        val textH = nameH + metaH + admitH
        val minimum = when (cardStyle) {
            PatientCardStyle.BANNER, PatientCardStyle.WRISTBAND -> 38f
            PatientCardStyle.INITIALS, PatientCardStyle.AVATAR_CHIPS -> 34f
            else -> badgeSize
        }
        return maxOf(minimum, textH)
    }

    private fun measureCard(patient: Patient): Float {
        val innerWidth = pageWidth - 2 * margin - 2 * cardPadding
        var h = cardPadding

        h += headerHeight(patient) + headerSpacing
        h += chipHeight + headerSpacing

        if (patient.warningFlags.isNotEmpty()) {
            h += measureSection(
                warningText(patient),
                innerWidth
            )
        }

        if (patient.initialDiagnosis.isNotBlank()) {
            h += measureSection(patient.initialDiagnosis, styledSectionWidth(innerWidth))
        }

        h += 8f

        h += 2 * PdfTextWrapper.lineHeight(metaPaint)
        h += 4f

        val sectionWidth = styledSectionWidth(innerWidth)
        h += measureSection(patient.treatmentPlan, sectionWidth)
        h += measureSection(patient.followUp, sectionWidth)
        h += measureSection(patient.labs, sectionWidth)

        h += cardPadding
        return h
    }

    private fun measureSection(text: String, width: Float): Float {
        if (text.isBlank()) return 0f
        val titleH = PdfTextWrapper.lineHeight(sectionTitlePaint)
        val bodyH = PdfTextWrapper.wrap(text, bodyPaint, width).size *
                PdfTextWrapper.lineHeight(bodyPaint)
        return 6f + titleH + 2f + bodyH + 6f
    }

    private fun drawCard(
        canvas: Canvas,
        y: Float,
        height: Float,
        patient: Patient,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>
    ) {
        val left = margin
        val right = pageWidth - margin
        val innerLeft = left + cardPadding
        val innerRight = right - cardPadding
        val innerWidth = innerRight - innerLeft

        val cardRect = RectF(left, y, right, y + height)
        fillPaint.color = when (cardStyle) {
            PatientCardStyle.ALERT -> palette.groups[3]
            PatientCardStyle.AVATAR_CHIPS -> palette.groups[0]
            else -> palette.cream
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, fillPaint)
        borderPaint.color = palette.border
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, borderPaint)

        if (cardStyle == PatientCardStyle.COLOR_BAR) {
            fillPaint.color = 0xFF9F1239.toInt()
            canvas.drawRoundRect(
                RectF(left, y, left + 7f, y + height),
                cardRadius,
                cardRadius,
                fillPaint
            )
        } else if (cardStyle == PatientCardStyle.INITIALS) {
            fillPaint.color = 0xFF5B3F8C.toInt()
            canvas.drawRect(left, y, left + 5f, y + height, fillPaint)
        }

        var cursor = drawStyledHeader(
            canvas = canvas,
            y = y + cardPadding,
            innerLeft = innerLeft,
            innerRight = innerRight,
            patient = patient
        ) + headerSpacing

        // Chips row, right to left
        var chipX = innerRight
        fun drawChip(text: String, fill: Int, textColor: Int) {
            val w = chipTextPaint.measureText(text) + 2 * chipPadding
            val chipLeft = chipX - w
            val chipRect = RectF(chipLeft, cursor, chipX, cursor + chipHeight)
            fillPaint.color = fill
            canvas.drawRoundRect(chipRect, chipRadius, chipRadius, fillPaint)
            chipTextPaint.color = textColor
            val baseline = cursor + chipHeight / 2f -
                    (chipTextPaint.fontMetrics.ascent + chipTextPaint.fontMetrics.descent) / 2f
            canvas.drawText(text, (chipLeft + chipX) / 2f, baseline, chipTextPaint)
            chipX = chipLeft - 4f
        }
        drawChip(patient.diagnosisType.arabicLabel, palette.tableHeader, Color.WHITE)
        if (patient.isPriority) {
            drawChip("أولوية", 0xFF9F1239.toInt(), Color.WHITE)
        }
        if (patient.hasCompanion) {
            drawChip("مرافق", palette.zebra, palette.text)
        }
        patient.responsibleSpecialistId?.let(supervisorNames::get)?.let {
            drawChip(it, palette.summary, palette.text)
        }
        patient.responsibleResidentId?.let(residentNames::get)?.let {
            drawChip(it, palette.groups[0], palette.text)
        }
        cursor += chipHeight + headerSpacing

        if (patient.warningFlags.isNotEmpty()) {
            cursor = drawSection(
                canvas,
                cursor,
                innerLeft,
                innerRight,
                innerWidth,
                "تنبيهات",
                warningText(patient)
            )
        }

        if (patient.initialDiagnosis.isNotBlank()) {
            cursor = drawSection(
                canvas, cursor, innerLeft, innerRight, innerWidth,
                "التشخيص", patient.initialDiagnosis, "Dx", "ت", 0xFF9F1239.toInt()
            )
        }

        // Divider
        borderPaint.color = palette.border
        canvas.drawLine(innerLeft, cursor + 2f, innerRight, cursor + 2f, borderPaint)
        cursor += 8f

        // Resident / supervisor
        val residentName = patient.responsibleResidentId?.let(residentNames::get) ?: "غير محدد"
        val specialistName = patient.responsibleSpecialistId?.let(supervisorNames::get) ?: "غير محدد"
        cursor = drawTextLine(canvas, cursor, innerRight, "المقيم: $residentName", metaPaint)
        cursor = drawTextLine(canvas, cursor, innerRight, "الاختصاصي: $specialistName", metaPaint)
        cursor += 4f

        // Sections
        cursor = drawSection(canvas, cursor, innerLeft, innerRight, innerWidth,
            "الخطة العلاجية", patient.treatmentPlan, "Rx", "ع", 0xFF5B3F8C.toInt())
        cursor = drawSection(canvas, cursor, innerLeft, innerRight, innerWidth,
            "المتابعة", patient.followUp, "F/U", "م", 0xFF9A6700.toInt())
        drawSection(canvas, cursor, innerLeft, innerRight, innerWidth,
            "التحاليل", patient.labs, "Labs", "خ", 0xFF174A7E.toInt())
    }

    private fun warningText(patient: Patient): String = patient.warningFlags
        .sortedBy { it.ordinal }
        .joinToString("\n") { flag ->
            val detail = patient.warningDetails[flag].orEmpty().trim()
            if (detail.isBlank()) "⚠ ${flag.arabicLabel}" else "⚠ ${flag.arabicLabel}: $detail"
        }

    private fun drawStyledHeader(
        canvas: Canvas,
        y: Float,
        innerLeft: Float,
        innerRight: Float,
        patient: Patient
    ): Float {
        val h = headerHeight(patient)
        val bandStyle = cardStyle == PatientCardStyle.BANNER ||
            cardStyle == PatientCardStyle.WRISTBAND
        if (bandStyle) {
            fillPaint.color = if (cardStyle == PatientCardStyle.WRISTBAND) {
                0xFF0F172A.toInt()
            } else palette.tableHeader
            canvas.drawRoundRect(RectF(innerLeft, y, innerRight, y + h), 7f, 7f, fillPaint)
        }

        val showLargeBadge = cardStyle in setOf(
            PatientCardStyle.BADGE_HEADER,
            PatientCardStyle.INITIALS,
            PatientCardStyle.AVATAR_CHIPS
        )
        val badge = if (showLargeBadge) 32f else 0f
        if (showLargeBadge) {
            val badgeRight = innerRight
            val badgeLeft = badgeRight - badge
            fillPaint.color = when (cardStyle) {
                PatientCardStyle.INITIALS -> 0xFF5B3F8C.toInt()
                PatientCardStyle.AVATAR_CHIPS -> 0xFF174A7E.toInt()
                else -> palette.titleHeader
            }
            canvas.drawRoundRect(RectF(badgeLeft, y, badgeRight, y + badge), 7f, 7f, fillPaint)
            val text = if (cardStyle == PatientCardStyle.BADGE_HEADER) {
                patient.sortOrder.toString()
            } else {
                patient.name.trim().firstOrNull()?.toString() ?: "م"
            }
            val baseline = y + badge / 2f -
                (badgeNumberPaint.fontMetrics.ascent + badgeNumberPaint.fontMetrics.descent) / 2f
            canvas.drawText(text, (badgeLeft + badgeRight) / 2f, baseline, badgeNumberPaint)
        }

        val oldName = namePaint.color
        val oldMeta = metaPaint.color
        if (bandStyle) {
            namePaint.color = Color.WHITE
            metaPaint.color = 0xFFE2E8F0.toInt()
        } else if (cardStyle == PatientCardStyle.ALERT) {
            namePaint.color = palette.text
        }
        val textRight = innerRight - badge - if (showLargeBadge) 8f else 6f
        var textY = y + if (bandStyle) 4f else 0f
        textY = drawTextLine(canvas, textY, textRight, patient.name, namePaint)
        textY = drawTextLine(canvas, textY, textRight, metaLine(patient), metaPaint)
        val admit = admitLine(patient)
        if (admit.isNotBlank()) textY = drawTextLine(canvas, textY, textRight, admit, metaPaint)
        namePaint.color = oldName
        metaPaint.color = oldMeta

        if (!showLargeBadge && !bandStyle) {
            val oldColor = badgeNumberPaint.color
            badgeNumberPaint.color = palette.text
            canvas.drawText(
                patient.sortOrder.toString(),
                innerLeft + 8f,
                y - badgeNumberPaint.fontMetrics.ascent,
                badgeNumberPaint
            )
            badgeNumberPaint.color = oldColor
        }
        return maxOf(y + h, textY)
    }

    private fun drawSection(
        canvas: Canvas,
        y: Float,
        innerLeft: Float,
        innerRight: Float,
        innerWidth: Float,
        title: String,
        body: String,
        code: String = "",
        monogram: String = "",
        accent: Int = palette.titleHeader
    ): Float {
        if (body.isBlank()) return y
        val marked = cardStyle == PatientCardStyle.ICON_ROWS ||
            cardStyle == PatientCardStyle.FIELD_MONOGRAMS
        val markerSize = if (marked) 25f else 0f
        val contentRight = innerRight - if (marked) markerSize + 7f else 0f
        val contentWidth = innerWidth - if (marked) markerSize + 7f else 0f
        var cursor = y + 4f
        if (marked) {
            fillPaint.color = if (cardStyle == PatientCardStyle.ICON_ROWS) {
                palette.summary
            } else accent
            canvas.drawRoundRect(
                RectF(innerRight - markerSize, cursor, innerRight, cursor + markerSize),
                6f, 6f, fillPaint
            )
            val oldColor = chipTextPaint.color
            chipTextPaint.color = if (cardStyle == PatientCardStyle.ICON_ROWS) palette.text else Color.WHITE
            val markerText = if (cardStyle == PatientCardStyle.ICON_ROWS) code else monogram
            val baseline = cursor + markerSize / 2f -
                (chipTextPaint.fontMetrics.ascent + chipTextPaint.fontMetrics.descent) / 2f
            canvas.drawText(markerText, innerRight - markerSize / 2f, baseline, chipTextPaint)
            chipTextPaint.color = oldColor
        }
        cursor = drawTextLine(canvas, cursor, contentRight, title, sectionTitlePaint)
        cursor += 2f
        cursor = drawParagraph(canvas, cursor, contentRight, contentWidth, body, bodyPaint)
        cursor += 2f
        borderPaint.color = palette.border
        canvas.drawLine(innerLeft, cursor, innerRight, cursor, borderPaint)
        return cursor + 4f
    }

    private fun styledSectionWidth(innerWidth: Float): Float =
        if (cardStyle == PatientCardStyle.ICON_ROWS ||
            cardStyle == PatientCardStyle.FIELD_MONOGRAMS
        ) innerWidth - 32f else innerWidth

    /**
     * Draws one line whose visual top is at [y], right-aligned to [right].
     * Returns the visual top of the next line, so stacked calls produce
     * non-overlapping text.
     */
    private fun drawTextLine(
        canvas: Canvas,
        y: Float,
        right: Float,
        text: String,
        paint: Paint
    ): Float {
        val baseline = y - paint.fontMetrics.ascent
        canvas.drawText(autoDirection(text), right, baseline, paint)
        return y + PdfTextWrapper.lineHeight(paint)
    }

    private fun drawParagraph(
        canvas: Canvas,
        y: Float,
        right: Float,
        width: Float,
        text: String,
        paint: Paint
    ): Float {
        val lines = PdfTextWrapper.wrap(text, paint, width)
        var cursor = y
        for (line in lines) {
            cursor = drawTextLine(canvas, cursor, right, line, paint)
        }
        return cursor
    }

    private fun autoDirection(text: String): String = bidi.unicodeWrap(
        text,
        TextDirectionHeuristics.FIRSTSTRONG_LTR
    )

    private fun metaLine(patient: Patient): String = buildString {
        append(if (patient.gender == Gender.MALE) "ذكر" else "أنثى")
        patient.age?.let { append(" · $it سنة") }
    }

    private fun admitLine(patient: Patient): String {
        val parts = mutableListOf<String>()
        if (patient.admittanceNumber.isNotBlank()) parts += "رقم ${patient.admittanceNumber}"
        patient.admittanceDate?.let { date ->
            parts += "دخول $date"
            patient.admittanceDays?.let { days -> parts += "$days يوم" }
        }
        return parts.joinToString(" · ")
    }
}
