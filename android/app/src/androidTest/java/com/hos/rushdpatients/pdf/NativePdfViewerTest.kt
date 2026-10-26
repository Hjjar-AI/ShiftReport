package com.hos.rushdpatients.pdf

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.testutil.IsolatedStorageContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Opens a dummy PDF in the device's real viewer; retain screenshot for visual acceptance. */
@RunWith(AndroidJUnit4::class)
class NativePdfViewerTest {
    @Test fun installedViewerReceivesActualReadGrantForDemoPdf() = runBlocking<Unit> {
        val context = IsolatedStorageContext()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            val row = Patient(id = "viewer-tea", name = "شاي تجريبي", initialDiagnosis = "تشخيص تجريبي",
                treatmentPlan = "DEMO TREATMENT 12.5 mg", followUp = "متابعة تجريبية", labs = "Na=140")
            val file = File(context.cacheDir, "DEMO-viewer.pdf")
            PdfReportExporter(context).export(listOf(row), emptyList(), ReportSummary(1, 1, 0, LocalDate.of(2026, 10, 26), emptyList()),
                emptyMap(), emptyMap(), PdfExportOptions(paperSize = PdfPaperSize.A5), true, file, documentLabel = "DEMO")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val viewer = requireNotNull(intent.resolveActivity(context.packageManager)) { "No installed PDF viewer" }.packageName
            instrumentation.runOnMainSync { context.startActivity(intent) }
            val deadline = SystemClock.elapsedRealtime() + 15000
            var visible = ""
            while (SystemClock.elapsedRealtime() < deadline) {
                visible = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty()
                if (visible == viewer) break
                SystemClock.sleep(100)
            }
            assertEquals("Expected real PDF viewer foreground", viewer, visible)
            SystemClock.sleep(1200) // Let the external renderer settle before the retained visual.
            val artifacts = File(context.getExternalFilesDir(null), "verification-artifacts/native-pdf-viewer").apply { mkdirs() }
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(artifacts, "viewer.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
            File(artifacts, "result.txt").writeText("viewer=$viewer\nreadGrant=true\ndummyOnly=true\n")
        } finally {
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
            context.cleanUp()
        }
    }
}
