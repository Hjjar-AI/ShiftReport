package com.hos.rushdpatients.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.testutil.IsolatedStorageContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/** Checks real dummy PDFs, their emitted annotations and rasterized button borders. */
@RunWith(AndroidJUnit4::class)
class A5FooterNavigationTest {
    private val context = IsolatedStorageContext()
    private val pageWidth = PdfPaperSize.A5.widthPoints.toFloat()
    private val pageHeight = PdfPaperSize.A5.heightPoints.toFloat()
    @After fun cleanup() = context.cleanUp()

    private data class Link(val rectangle: RectF, val destination: Int)
    private data class PdfPages(val order: List<Int>, val links: Map<Int, List<Link>>)

    private suspend fun export(rows: List<Patient>): File = PdfReportExporter(context).export(
        rows, emptyList(), ReportSummary(rows.size, rows.size, 0, LocalDate.of(2026, 11, 16), emptyList()),
        emptyMap(), emptyMap(), PdfExportOptions(paperSize = PdfPaperSize.A5), true,
        File(context.cacheDir, "DEMO-footer.pdf"), documentLabel = "DEMO")

    /** Narrow reader for our own native, unencrypted fixture PDFs; last object revision wins. */
    private fun readLinks(file: File): PdfPages {
        val text = file.readBytes().toString(Charsets.ISO_8859_1)
        val objects = Regex("""(?ms)^(\d+)\s+\d+\s+obj\b(.*?)^endobj\b""").findAll(text)
            .associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val refs = Regex("""(\d+)\s+\d+\s+R""")
        fun reference(key: String, body: String) = requireNotNull(
            Regex("""/$key\s+(\d+)\s+\d+\s+R""").find(body)).groupValues[1].toInt()
        val catalog = objects.values.first { Regex("""/Type\s*/Catalog\b""").containsMatchIn(it) }
        val pages = mutableListOf<Int>()
        fun visit(id: Int) {
            val body = objects.getValue(id)
            if (Regex("""/Type\s*/Page\b""").containsMatchIn(body)) pages += id
            else {
                val children = requireNotNull(Regex("""/Kids\s*\[([^]]*)]""").find(body)).groupValues[1]
                refs.findAll(children).forEach { visit(it.groupValues[1].toInt()) }
            }
        }
        visit(reference("Pages", catalog))
        val links = pages.associateWith { id ->
            val annotations = Regex("""/Annots\s*\[([^]]*)]""").find(objects.getValue(id))?.groupValues?.get(1).orEmpty()
            refs.findAll(annotations).map { annotation ->
                val body = objects.getValue(annotation.groupValues[1].toInt())
                assertTrue(body.contains("/Subtype /Link"))
                assertTrue("Navigation must fit the destination page", body.contains("/Fit"))
                assertTrue("Annotation borders must stay hidden", body.contains("/Border [0 0 0]"))
                val coords = requireNotNull(Regex("""/Rect\s*\[([^]]*)]""").find(body)).groupValues[1]
                    .trim().split(Regex("\\s+")).map(String::toFloat)
                assertEquals(4, coords.size)
                val destination = requireNotNull(Regex("""/Dest\s*\[(\d+)\s+\d+\s+R""").find(body))
                    .groupValues[1].toInt()
                Link(RectF(coords[0], pageHeight - coords[3], coords[2], pageHeight - coords[1]), destination)
            }.toList()
        }
        return PdfPages(pages, links)
    }

    private fun expectedButtons(): List<RectF> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = PdfFonts(context).bold; textSize = 12f }
        val bidi = BidiFormatter.getInstance()
        val measured = listOf("السابق", "الفهرس", "التالي").maxOf {
            paint.measureText(bidi.unicodeWrap(it, TextDirectionHeuristics.FIRSTSTRONG_LTR))
        }
        val width = measured + 40f
        val height = paint.fontMetrics.bottom - paint.fontMetrics.top + 10f
        // Independent footer contract: 2pt clear of both the card and page edge.
        val reserved = maxOf(8f * 72f / 25.4f, height + 4f)
        val centerY = pageHeight - reserved / 2f
        return (0..2).map { slot ->
            val centerX = pageWidth / 2f + (1 - slot) * (width + 20f)
            RectF(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
        }
    }

    private fun assertRectangle(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, 0.02f)
        assertEquals(expected.top, actual.top, 0.02f)
        assertEquals(expected.right, actual.right, 0.02f)
        assertEquals(expected.bottom, actual.bottom, 0.02f)
    }

    private fun assertButtonBordersAndClearGaps(page: PdfRenderer.Page, slots: List<RectF>) {
        val scale = 3
        val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            fun inkNear(x: Float, y: Float): Boolean {
                val px = (x * scale).roundToInt(); val py = (y * scale).roundToInt()
                return (-2..2).any { dx -> (-2..2).any { dy ->
                    val color = bitmap.getPixel((px + dx).coerceIn(0, bitmap.width - 1),
                        (py + dy).coerceIn(0, bitmap.height - 1))
                    minOf(Color.red(color), Color.green(color), Color.blue(color)) < 245
                } }
            }
            val cardBottom = pageHeight - maxOf(8f * 72f / 25.4f, slots[0].height() + 4f)
            assertTrue("Footer needs clearance below the card", slots[0].top - cardBottom >= 1.9f)
            assertTrue("Patient card bottom intrudes into the footer or is missing", inkNear(pageWidth / 2f, cardBottom))
            assertFalse("Card and footer controls overlap", inkNear(pageWidth / 2f, (cardBottom + slots[0].top) / 2f))
            slots.forEach { rect ->
                assertTrue("Missing left button border", inkNear(rect.left, rect.centerY()))
                assertTrue("Missing right button border", inkNear(rect.right, rect.centerY()))
                assertTrue("Button top clipped or click target exceeds drawing", inkNear(rect.centerX(), rect.top))
                assertTrue("Button bottom clipped or click target exceeds drawing", inkNear(rect.centerX(), rect.bottom))
            }
            slots.zipWithNext().forEach { (right, left) ->
                assertEquals(20f, right.left - left.right, 0.02f)
                assertFalse("Gap contains a button or link decoration", inkNear((right.left + left.right) / 2f, right.centerY()))
            }
        } finally { bitmap.recycle() }
    }

    private fun verifyFooter(file: File, patientCount: Int, requireContinuation: Boolean) {
        val pdf = readLinks(file)
        val indexPage = pdf.order.first()
        val starts = pdf.links.getValue(indexPage).map { it.destination }
        assertEquals(patientCount, starts.size)
        val startPositions = starts.map { pdf.order.indexOf(it) }
        assertTrue(startPositions.all { it > 0 })
        assertEquals(startPositions.sorted(), startPositions)
        val slots = expectedButtons()
        slots.forEach {
            assertTrue(it.left > 0f && it.right < pageWidth)
            assertTrue(it.top > 0f && it.bottom <= pageHeight - 1.9f)
        }
        var continuations = 0
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(pdf.order.size, renderer.pageCount)
            pdf.order.forEachIndexed { pageIndex, id ->
                if (pageIndex < startPositions.first()) return@forEachIndexed
                val patient = startPositions.indexOfLast { it <= pageIndex }
                if (pageIndex != startPositions[patient]) continuations++
                val links = pdf.links.getValue(id)
                val footer = links.filter { it.rectangle.top > pageHeight - 70f }
                val expected = mapOf(0 to starts.getOrNull(patient - 1), 1 to indexPage, 2 to starts.getOrNull(patient + 1))
                assertEquals(expected.values.count { it != null }, footer.size)
                expected.forEach { (slot, target) ->
                    val matching = footer.filter { kotlin.math.abs(it.rectangle.centerX() - slots[slot].centerX()) < 0.02f }
                    if (target == null) assertTrue("Disabled navigation must have no link", matching.isEmpty())
                    else {
                        assertEquals(1, matching.size)
                        assertRectangle(slots[slot], matching.single().rectangle)
                        assertEquals(target, matching.single().destination)
                    }
                }
                // Header links end above the footer, so taller buttons cannot overlap the card header.
                assertTrue(links.filter { it.rectangle.top <= pageHeight - 70f }.all { it.rectangle.bottom < slots[0].top })
                renderer.openPage(pageIndex).use { page ->
                    assertEquals(PdfPaperSize.A5.widthPoints, page.width)
                    assertEquals(PdfPaperSize.A5.heightPoints, page.height)
                    assertButtonBordersAndClearGaps(page, slots)
                }
            }
        }
        if (requireContinuation) assertTrue("Fixture must exercise continuation pages", continuations > 0)
    }

    private fun rows() = (1..3).map { Patient(id = "footer-$it", name = "شاي تجريبي $it", sortOrder = it,
        initialDiagnosis = "تشخيص تجريبي", treatmentPlan = "خطة تجريبية") }

    @Test fun enlargedButtonsKeepTwentyPointGapsFullHitAreasAndCorrectPatientDestinations() = runBlocking<Unit> {
        verifyFooter(export(rows()), patientCount = 3, requireContinuation = false)
    }

    @Test fun continuationPagesKeepEnlargedFooterLinksToAdjacentPatientsAndIndex() = runBlocking<Unit> {
        val rows = rows().mapIndexed { index, patient ->
            if (index == 1) patient.copy(followUp = (1..100).joinToString("\n") { "متابعة تجريبية $it" }) else patient
        }
        verifyFooter(export(rows), patientCount = 3, requireContinuation = true)
    }

    @Test fun singlePatientContinuationsKeepOnlyIndexActiveAndBothOtherButtonsVisible() = runBlocking<Unit> {
        val row = rows().first().copy(followUp = (1..100).joinToString("\n") { "متابعة تجريبية $it" })
        verifyFooter(export(listOf(row)), patientCount = 1, requireContinuation = true)
    }
}
