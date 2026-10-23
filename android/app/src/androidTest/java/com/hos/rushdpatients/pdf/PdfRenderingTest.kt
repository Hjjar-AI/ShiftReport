package com.hos.rushdpatients.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.testutil.IsolatedStorageContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PdfRenderingTest {
    private val context = IsolatedStorageContext()
    @After fun cleanup() = context.cleanUp()
    private fun patients() = (1..8).map { n -> Patient(id = "p-$n", name = "شاي تجريبي", sortOrder = n,
        initialDiagnosis = "تشخيص تجريبي", treatmentPlan = "خطة تجريبية", labs = "نتائج تجريبية",
        followUp = (1..12).joinToString("\n") { "متابعة عربية تجريبية للقراءة دون بيانات حقيقية" },
        tasks = listOf(PatientTask("t-$n", "فحص تجريبي"))) }
    private suspend fun export(cards: Boolean, options: PdfExportOptions): File {
        val rows = patients()
        return PdfReportExporter(context).export(rows, emptyList(), ReportSummary(rows.size, rows.size, 0,
            LocalDate.of(2026, 1, 1), emptyList()), emptyMap(), emptyMap(), options, cards,
            File(context.cacheDir, "synthetic.pdf"), documentLabel = "DEMO — بيانات تجريبية")
    }
    private fun renderAll(file: File, landscape: Boolean): Int {
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount > 1)
            repeat(renderer.pageCount) { index -> renderer.openPage(index).use { page ->
                if (landscape) assertTrue(page.width > page.height) else assertTrue(page.height > page.width)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("Blank PDF page $index", pixels.count { Color.alpha(it) > 0 && (Color.red(it) < 220 || Color.green(it) < 220 || Color.blue(it) < 220) } > 100)
                bitmap.recycle()
            } }
            return renderer.pageCount
        }
    }
    @Test fun longArabicClassicReportPaginatesAndEveryPageRenders() = runBlocking<Unit> {
        renderAll(export(false, PdfExportOptions(orientation = PdfOrientation.LANDSCAPE)), true)
    }
    @Test fun a5CardsIncludeIndexAndRenderEveryPatientPage() = runBlocking<Unit> {
        assertTrue(renderAll(export(true, PdfExportOptions(paperSize = PdfPaperSize.A5)), false) >= 9)
    }
}
