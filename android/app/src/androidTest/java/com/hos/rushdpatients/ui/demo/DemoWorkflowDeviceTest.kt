package com.hos.rushdpatients.ui.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.doctor.DoctorEditInput
import com.hos.rushdpatients.domain.report.*
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.testutil.IsolatedStorageContext
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.ui.report.ReportStepOutcome
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Real demo adapters/renderers with isolated dummy data; no production repositories or transport. */
@RunWith(AndroidJUnit4::class)
class DemoWorkflowDeviceTest {
    private lateinit var context: IsolatedStorageContext
    private lateinit var vm: DemoWardViewModel
    private lateinit var repository: InMemoryDemoWardRepository
    private val store = ViewModelStore()
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    @Before fun open() {
        context = IsolatedStorageContext()
        main {
            repository = InMemoryDemoWardRepository()
            vm = DemoWardViewModel(UiStrings(context), context, repository,
                PdfReportExporter(context), PdfAssignmentRenderer(context), MediaStoreSaver(), TextReportBuilder())
            store.put("demo", vm)
        }
    }
    @After fun close() { main { store.clear() }; context.cleanUp() }
    private suspend fun idle() = withTimeout(30000) { while (vm.report.state.value.isBusy) delay(20) }
    private suspend fun preview(route: String = "general", draft: Boolean = true): android.net.Uri {
        main { vm.report.previewExportPdf(route, draft) }; idle()
        assertNull(vm.report.state.value.error)
        return requireNotNull(vm.report.state.value.pdfPreviewUri)
    }
    private fun bytes(uri: android.net.Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    @Test fun clinicalFetchUndoResetNeverWriteSettingsOrSessionFiles() {
        val prefs = context.getSharedPreferences("sentinel", Context.MODE_PRIVATE)
        prefs.edit().putString("keep", "unchanged").commit()
        val sentinel = File(context.filesDir, "sentinel").apply { writeText("unchanged") }
        val before = vm.state.value.patients.first()
        main {
            assertTrue(vm.save(before.copy(labs = "نتيجة محلية تجريبية")))
            vm.controller.bringLatestData()
        }
        assertTrue(vm.state.value.fetchChoiceRequired)
        main { vm.controller.replaceAndFetch(false) }
        assertEquals(before.labs, vm.state.value.patients.single { it.id == before.id }.labs)
        main { vm.undoFetch() }
        assertEquals("نتيجة محلية تجريبية", vm.state.value.patients.single { it.id == before.id }.labs)
        main { vm.reset() }
        assertEquals(before.labs, vm.state.value.patients.single { it.id == before.id }.labs)
        assertEquals(mapOf("keep" to "unchanged"), prefs.all)
        assertEquals(listOf(sentinel.name), context.filesDir.listFiles().orEmpty().map { it.name })
        assertEquals("unchanged", sentinel.readText())
        assertTrue(context.getDatabasePath("probe").parentFile!!.listFiles().orEmpty().isEmpty())
    }

    @Test fun doctorEntryDiscardsPinTelegramAndCustomTitleWhileRetainingClinicalRole() {
        main { vm.doctors.addDoctor(DoctorEditInput("دردار", "شجرة", Gender.MALE,
            ClinicalRole.RESIDENT, "2468", "legacy title", 456), {}) }
        val doctor = vm.state.value.doctors.single { it.firstName == "دردار" }
        assertNull(doctor.telegramId)
        assertNull(doctor.customTitle)
        assertTrue(doctor.extraOptions.none { it.startsWith("pin:") })
        assertEquals(ClinicalRole.RESIDENT, doctor.clinicalRole)
        assertTrue(context.filesDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun generalAndSupervisorPreviewShareExactBytesAndRejectLaterClinicalChanges() = runBlocking<Unit> {
        main { vm.report.load() }
        val general = preview()
        val reviewed = bytes(general)
        main { vm.report.onPdfPreviewOpened(general); vm.report.shareExportPdf("general", true) }; idle()
        assertArrayEquals(reviewed, bytes(requireNotNull(vm.report.state.value.pdfShareUri)))
        main { vm.report.consumePdfShare() }
        val supervisor = vm.report.state.value.supervisorTargets.first().doctorId
        val route = "supervisor:$supervisor"
        val target = preview(route)
        val targetBytes = bytes(target)
        main { vm.report.onPdfPreviewOpened(target); vm.report.shareExportPdf(route, true) }; idle()
        assertArrayEquals(targetBytes, bytes(requireNotNull(vm.report.state.value.pdfShareUri)))
        main {
            vm.report.consumePdfShare()
            val patient = vm.state.value.patients.first()
            vm.save(patient.copy(labs = "تعديل بعد المعاينة"))
            vm.report.shareExportPdf("general", true)
        }
        idle()
        assertNotNull(vm.report.state.value.error)
        assertNull(vm.report.state.value.pdfShareUri)
        assertTrue(vm.report.state.value.deliveryRecords.isEmpty())
    }

    @Test fun simulatedSelectedDeliveryCreatesOnlyMemoryReceiptsAndResetDiscardsThem() = runBlocking<Unit> {
        main { vm.report.load(); vm.report.setReportAsPdf(false) }
        val supervisor = vm.report.state.value.supervisorTargets.first().doctorId
        val target = preview("supervisor:$supervisor", false)
        main {
            vm.report.onPdfPreviewOpened(target)
            vm.report.sendSelectedReports(true, setOf(supervisor), false)
        }
        idle()
        assertEquals(ReportStepOutcome.CONFIRMED, vm.report.state.value.deliveryOutcome)
        assertEquals(2, vm.report.state.value.deliveryRecords.size)
        assertTrue(vm.report.state.value.deliveryRecords.all { it.status == DeliveryStatus.SENT && it.label.startsWith("DEMO") })
        main { vm.reset(); vm.report.load() }
        assertTrue(vm.report.state.value.deliveryRecords.isEmpty())
        assertTrue(context.filesDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun demoClassicCardsAndEmptyRosterAssignmentsProduceRenderableMarkedArtifacts() = runBlocking<Unit> {
        val artifacts = File(context.getExternalFilesDir(null), "verification-artifacts/demo-coverage").apply { mkdirs() }
        main {
            val patient = vm.state.value.patients.first()
            vm.save(patient.copy(treatmentPlan = "BEGIN_TREATMENT\n" +
                (1..36).joinToString("\n") { "MED_%02d — خطة علاجية تجريبية 12.5 mg".format(it) } + "\nEND_TREATMENT",
                tasks = (1..40).map { PatientTask("task-$it", "TASK_%02d — متابعة تجريبية".format(it), done = it % 2 == 0) },
                labs = "END_LABS Na=140"))
        }
        for (style in PdfStyle.entries) for (paper in listOf(PdfPaperSize.A4, PdfPaperSize.A5)) {
            main { vm.report.load(); vm.report.setPdfAppearance(style, PdfExportOptions(paperSize = paper)) }
            val uri = preview()
            val file = File(artifacts, "DEMO-${style.name}-${paper.name}.pdf").apply { writeBytes(bytes(uri)) }
            renderEveryPage(file)
        }
        main { repository.state.value = repository.state.value.copy(shiftDoctorIds = emptyList()); vm.report.load() }
        val uri = preview("assignments", false)
        val file = File(artifacts, "DEMO-assignments-empty-roster.pdf").apply { writeBytes(bytes(uri)) }
        renderEveryPage(file)
        assertNull(vm.report.state.value.summary)
    }

    private fun renderEveryPage(file: File) {
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            assertTrue(pdf.pageCount > 0)
            repeat(pdf.pageCount) { index -> pdf.openPage(index).use { page ->
                assertTrue(page.height > page.width)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("Blank demo page $index", pixels.count { Color.red(it) < 200 && Color.green(it) < 200 && Color.blue(it) < 200 } > 100)
                if (index == 0 || index == pdf.pageCount - 1) {
                    File(file.parentFile, "${file.nameWithoutExtension}-page-${index + 1}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                bitmap.recycle()
            } }
        }
    }
}
