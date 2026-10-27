package com.hos.rushdpatients.ui.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.hos.rushdpatients.data.model.ClinicalRole
import java.io.File
import java.io.IOException
import java.nio.file.Files
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.*
import com.hos.rushdpatients.domain.report.*
import com.hos.rushdpatients.network.ReportSender
import com.hos.rushdpatients.network.SendResult
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.sync.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.util.NetworkStatus
import com.hos.rushdpatients.util.ShiftDate
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportPreviewViewModelTest {
    private val store = ViewModelStore()
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val context = mockk<Context>()
    private val directory = Files.createTempDirectory("report_delivery_copies_").toFile()
    private val cards = mockk<PdfCardsExporter>()
    private val saver = mockk<MediaStoreSaver>()
    private var registry = emptyList<com.hos.rushdpatients.data.model.Doctor>()
    private val reports = mockk<ReportBuilder>()
    private val sender = mockk<ReportSender>(relaxed = true)
    private val settings = mockk<SettingsRepository>()
    private val doctors = mockk<DoctorRepository>()
    private val shifts = mockk<ShiftRepository>()
    private val sessions = mockk<SessionManager>()
    private val sync = mockk<SyncService>()
    private val projects = mockk<ProjectConfigStore>()
    private val ledger = mockk<ReportDeliveryLedger>()
    private val actor = Fixtures.doctor(telegramId = 123)
    private var shift = Fixtures.shift(date = ShiftDate.current())
    private var rows = listOf(Fixtures.patient())
    private fun built() = BuiltReport(shift, rows, listOf(actor), (listOf(actor) + registry).associate { it.id to it.fullName },
        ReportSummary(rows.size, rows.size, 0, shift.date, listOf(actor)), listOf("synthetic review"), registryDoctors = listOf(actor) + registry)
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(NetworkStatus)
        every { NetworkStatus.isOnline(context) } returns true
        coEvery { settings.get(any()) } returns null
        coEvery { settings.getBoolean(any(), any()) } returns false
        coEvery { settings.isReportAsPdf() } returns false
        every { projects.current() } returns ProjectConfig(hospitalName = "تجريبي")
        every { ledger.observe() } returns flowOf(emptyList())
        val session = Session(actor.id, actor.fullName, actor.telegramId, actor.role, Fixtures.timestamp)
        every { sessions.current() } returns session
        every { sessions.sessionFlow } returns MutableStateFlow(session)
        every { doctors.observeAll() } returns flowOf(listOf(actor))
        coEvery { doctors.getAll() } returns listOf(actor)
        coEvery { doctors.getActiveById(actor.id) } returns actor
        coEvery { shifts.getById(shift.id) } answers { shift }
        coEvery { reports.build(shift.id) } answers { built() }
        coEvery { reports.buildAssignments(shift.id) } answers { built() }
        coEvery { shifts.getLatest() } answers { shift }
        coEvery { sync.describeLocalChanges(shift.id) } returns HandoverChangeSummary(emptyList(), emptyList(), emptyList())
        coEvery { sync.prepareCsvPublication(shift.id, any()) } answers {
            val captured = secondArg<BuiltReport?>() ?: built()
            CsvPublicationReview(shift.id, "synthetic csv", SyncState(), actor.id, actor.telegramId, captured.shift, captured.patients, null, "synthetic review")
        }
        coEvery { sync.uploadReviewedCsv(any()) } returns Result.success(CsvUploadResult(10, 1))
        coEvery { sender.sendTextReport(any(), any(), any(), any(), any()) } returns SendResult(listOf(20), 20, Fixtures.timestamp)
    }
    private fun viewModel() = ReportPreviewViewModel(text, SavedStateHandle(mapOf("shiftId" to shift.id)), context,
        reports, TextReportBuilder(), sender, mockk(), cards, mockk(), saver, settings, doctors, shifts,
        mockk(), sessions, mockk(), sync, projects, ledger).also { store.put("report", it) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain(); unmockkObject(NetworkStatus); unmockkStatic(FileProvider::class); directory.deleteRecursively() }
    @Test fun `loading review never sends and explicit send publishes before delivery`() {
        val vm = viewModel()
        assertFalse(vm.state.value.loading)
        coVerify(exactly = 0) { sender.sendTextReport(any(), any(), any(), any(), any()) }
        vm.send()
        coVerifyOrder {
            sender.checkGeneralDelivery(any(), any(), false, any())
            sync.uploadReviewedCsv(any())
            sender.sendTextReport(any(), any(), actor, any(), any())
        }
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.publicationOutcome)
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.deliveryOutcome)
        assertFalse(vm.state.value.sending)
    }
    @Test fun `sendUsesCapturedReviewedSnapshotWhileLaterEditsRemainLocal`() {
        val reviewedRows = rows
        val vm = viewModel()
        rows = listOf(rows.single().copy(labs = "later local edit"))
        vm.send()
        coVerify(exactly = 1) { sync.prepareCsvPublication(shift.id, match { it.patients == reviewedRows }) }
        coVerify(exactly = 1) { sync.uploadReviewedCsv(match { it.patients == reviewedRows }) }
        assertEquals("later local edit", rows.single().labs)
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.deliveryOutcome)
    }
    @Test fun `publication failure never starts report delivery`() {
        val vm = viewModel()
        coEvery { sync.uploadReviewedCsv(any()) } returns Result.failure(IllegalStateException("publication failed"))
        vm.send()
        assertFalse(vm.state.value.sending)
        assertTrue(vm.state.value.error.orEmpty().contains("publication failed"))
        coVerify(exactly = 0) { sender.sendTextReport(any(), any(), any(), any(), any()) }
    }
    @Test fun `empty roster still exposes selectable doctors before report exists`() {
        shift = shift.copy(doctorIds = emptyList())
        val vm = viewModel()
        assertEquals(listOf(actor), vm.state.value.availableDoctors)
        assertTrue(vm.state.value.doctors.isEmpty())
        assertNull(vm.state.value.summary)
        vm.send()
        coVerify(exactly = 0) { sync.prepareCsvPublication(any(), any()) }
    }
    @Test fun `switchingCurrentSectionInvalidatesReviewedPublication`() {
        val vm = viewModel()
        coEvery { sync.prepareCsvPublication(shift.id, any()) } throws IllegalArgumentException("synthetic selected section changed")
        vm.send()
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
        assertNotNull(vm.state.value.error)
    }

    private fun awaitIdle(vm: ReportPreviewViewModel) = runBlocking {
        withTimeout(10000) { while (vm.state.value.isBusy) delay(10) }
    }

    private fun pdfRoutes(): ReportPreviewViewModel {
        registry = listOf(actor.copy(id = "oak", fullName = "د. بلوط", clinicalRole = ClinicalRole.SUPERVISOR,
            supervisorGroupChatId = -200))
        rows = listOf(rows.single().copy(responsibleSpecialistId = "oak", labs = "reviewed result"))
        every { context.cacheDir } returns directory
        every { context.packageName } returns "synthetic.verification"
        coEvery { settings.isReportAsPdf() } returns true
        coEvery { reports.buildAssignments(any(), any()) } answers { built() }
        coEvery { doctors.getActiveById("oak") } answers { registry.single() }
        mockkStatic(FileProvider::class)
        val generalUri = mockk<Uri>(name = "general-preview")
        val supervisorUri = mockk<Uri>(name = "supervisor-preview")
        every { FileProvider.getUriForFile(context, any(), any<File>(), any<String>()) } returnsMany listOf(generalUri, supervisorUri)
        coEvery { cards.export(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            arg<File>(6).apply { writeText("reviewed bytes: " + firstArg<List<com.hos.rushdpatients.data.model.Patient>>().joinToString { it.labs }) }
        }
        coEvery { sender.sendPdfReport(any(), any(), any(), any(), any(), any(), any()) } returns SendResult(listOf(20), 20, Fixtures.timestamp)
        coEvery { sender.sendSupervisorPdfReport(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns SendResult(listOf(21), 21, Fixtures.timestamp)
        coEvery { saver.savePdf(any(), any(), any(), true) } returns mockk()
        val vm = viewModel()
        vm.previewPdf(); awaitIdle(vm)
        assertNull(vm.state.value.error)
        vm.onPdfPreviewOpened(requireNotNull(vm.state.value.pdfPreviewUri))
        vm.previewSupervisorPdf("oak"); awaitIdle(vm)
        assertNull(vm.state.value.error)
        vm.onPdfPreviewOpened(requireNotNull(vm.state.value.pdfPreviewUri))
        return vm
    }

    @Test fun `all selected destinations confirm before exact reviewed bytes are copied`() {
        val vm = pdfRoutes()
        val delivered = mutableListOf<ByteArray>()
        coEvery { sender.sendPdfReport(any(), any(), any(), any(), any(), any(), any()) } answers {
            delivered += secondArg<File>().readBytes(); SendResult(listOf(20), 20, Fixtures.timestamp)
        }
        coEvery { sender.sendSupervisorPdfReport(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            delivered += secondArg<File>().readBytes(); SendResult(listOf(21), 21, Fixtures.timestamp)
        }
        val copied = mutableListOf<ByteArray>()
        coEvery { saver.savePdf(any(), any(), any(), true) } answers {
            assertEquals(2, delivered.size)
            copied += secondArg<File>().readBytes(); mockk<Uri>()
        }
        vm.sendSelectedReports(true, setOf("oak"), exportPdf = true); awaitIdle(vm)
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.deliveryOutcome)
        assertEquals(2, copied.size)
        delivered.zip(copied).forEach { (sent, saved) -> assertArrayEquals(sent, saved) }
        coVerify(exactly = 2) { cards.export(any(), any(), any(), any(), any(), any(), any(), any()) }
        assertNull(vm.state.value.retryAction)
    }

    @Test fun `partial supervisor failure never copies selected PDFs locally`() {
        val vm = pdfRoutes()
        coEvery { sender.sendSupervisorPdfReport(any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws IOException("synthetic interrupted destination")
        vm.sendSelectedReports(true, setOf("oak"), exportPdf = true); awaitIdle(vm)
        assertEquals(ReportStepOutcome.PARTIAL, vm.state.value.deliveryOutcome)
        coVerify(exactly = 0) { saver.savePdf(any(), any(), any(), any()) }
    }

    @Test fun `local copy failure retains confirmed delivery without send retry`() {
        val vm = pdfRoutes()
        coEvery { saver.savePdf(any(), any(), any(), true) } returns null
        vm.sendSelectedReports(true, setOf("oak"), exportPdf = true); awaitIdle(vm)
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.deliveryOutcome)
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.retryAction)
        coVerify(exactly = 1) { sender.sendPdfReport(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { sender.sendSupervisorPdfReport(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `cancelled local copy retains confirmed delivery and releases busy state`() {
        val vm = pdfRoutes()
        coEvery { saver.savePdf(any(), any(), any(), true) } throws CancellationException("synthetic local cancellation")
        vm.sendSelectedReports(true, setOf("oak"), exportPdf = true); awaitIdle(vm)
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.deliveryOutcome)
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.retryAction)
    }

    @Test fun `tampered reviewed PDF rejects every route before publication`() {
        val vm = pdfRoutes()
        directory.resolve("pdf_previews").listFiles().orEmpty().first().appendText("tampered")
        vm.sendSelectedReports(true, setOf("oak"), exportPdf = true); awaitIdle(vm)
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
        coVerify(exactly = 0) { sender.sendPdfReport(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { saver.savePdf(any(), any(), any(), any()) }
        assertNotNull(vm.state.value.error)
    }

    @Test fun `unchecked local copy option does not save after confirmed delivery`() {
        val vm = pdfRoutes()
        vm.sendSelectedReports(true, setOf("oak"), exportPdf = false); awaitIdle(vm)
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.deliveryOutcome)
        coVerify(exactly = 0) { saver.savePdf(any(), any(), any(), any()) }
    }
    @Test fun `uncertain general delivery never copies PDFs or offers automatic send retry`() {
        val vm = pdfRoutes()
        coEvery { sender.sendPdfReport(any(), any(), any(), any(), any(), any(), any()) } throws
            com.hos.rushdpatients.network.ReportDeliveryUncertainException(IOException("synthetic response lost"))
        vm.sendSelectedReports(true, setOf("oak"), exportPdf = true); awaitIdle(vm)
        assertEquals(ReportStepOutcome.UNCERTAIN, vm.state.value.deliveryOutcome)
        assertNull(vm.state.value.retryAction)
        coVerify(exactly = 0) { saver.savePdf(any(), any(), any(), any()) }
        coVerify(exactly = 0) { sender.sendSupervisorPdfReport(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `mixed text and supervisor PDF saves only PDF after both confirm`() {
        val vm = pdfRoutes()
        coEvery { settings.setReportAsPdf(false) } just Runs
        vm.setReportAsPdf(false); awaitIdle(vm)
        var textSent = false
        var supervisorSent = false
        coEvery { sender.sendTextReport(any(), any(), any(), any(), any()) } answers {
            textSent = true; SendResult(listOf(20), 20, Fixtures.timestamp)
        }
        coEvery { sender.sendSupervisorPdfReport(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            supervisorSent = true; SendResult(listOf(21), 21, Fixtures.timestamp)
        }
        coEvery { saver.savePdf(any(), any(), any(), true) } answers {
            assertTrue(textSent && supervisorSent); mockk<Uri>()
        }
        vm.sendSelectedReports(true, setOf("oak"), exportPdf = true); awaitIdle(vm)
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.deliveryOutcome)
        coVerify(exactly = 1) { saver.savePdf(any(), any(), any(), true) }
        coVerify(exactly = 0) { sender.sendPdfReport(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `locked session after review blocks publication and preserves reviewed patients`() {
        val vm = viewModel()
        val patients = vm.state.value.patients
        every { sessions.current() } returns null
        vm.send(); awaitIdle(vm)
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
        coVerify(exactly = 0) { sender.sendTextReport(any(), any(), any(), any(), any()) }
        assertEquals(patients, vm.state.value.patients)
        assertNotNull(vm.state.value.error)
    }
    @Test fun `deactivated doctor after review cannot publish or send`() {
        val vm = viewModel()
        coEvery { doctors.getActiveById(actor.id) } returns null
        vm.send(); awaitIdle(vm)
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
        coVerify(exactly = 0) { sender.sendTextReport(any(), any(), any(), any(), any()) }
        assertNotNull(vm.state.value.error)
    }
    @Test fun `reconnected project invalidates reviewed publication before transport`() {
        val vm = viewModel()
        every { projects.current() } returns ProjectConfig(hospitalName = "another synthetic project")
        vm.send(); awaitIdle(vm)
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
        coVerify(exactly = 0) { sender.sendTextReport(any(), any(), any(), any(), any()) }
        assertNotNull(vm.state.value.error)
    }
    @Test fun `double send during publication enters transport once`() = runBlocking<Unit> {
        val release = CompletableDeferred<Unit>()
        coEvery { sync.uploadReviewedCsv(any()) } coAnswers { release.await(); Result.success(CsvUploadResult(10, 1)) }
        val vm = viewModel()
        vm.send(); vm.send()
        assertTrue(vm.state.value.sending)
        coVerify(exactly = 1) { sync.uploadReviewedCsv(any()) }
        release.complete(Unit); awaitIdle(vm)
        coVerify(exactly = 1) { sender.sendTextReport(any(), any(), any(), any(), any()) }
        assertFalse(vm.state.value.sending)
    }
    @Test fun `explicit delivery retry revalidates and reuses already confirmed publication`() {
        coEvery { sync.validatePublishedReport(any(), any(), any()) } just Runs
        coEvery { sender.sendTextReport(any(), any(), any(), any(), any()) } throws IOException("synthetic definite failure")
        val vm = viewModel()
        vm.send(); awaitIdle(vm)
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.publicationOutcome)
        coEvery { sender.sendTextReport(any(), any(), any(), any(), any()) } returns SendResult(listOf(21), 21, Fixtures.timestamp)
        vm.send(); awaitIdle(vm)
        coVerify(exactly = 1) { sync.uploadReviewedCsv(any()) }
        coVerify(exactly = 1) { sync.validatePublishedReport(any(), actor.id, actor.telegramId) }
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.deliveryOutcome)
    }
    @Test fun `retry action opens review without publishing automatically`() {
        val vm = viewModel()
        every { NetworkStatus.isOnline(context) } returns false
        vm.send()
        var reviews = 0
        every { NetworkStatus.isOnline(context) } returns true
        vm.retryLastAction { reviews++ }
        assertEquals(1, reviews)
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
        coVerify(exactly = 0) { sender.sendTextReport(any(), any(), any(), any(), any()) }
    }
    @Test fun `second load cannot overtake pending report construction`() = runBlocking<Unit> {
        val release = CompletableDeferred<Unit>()
        coEvery { reports.build(shift.id) } coAnswers { release.await(); built() }
        val vm = viewModel()
        assertTrue(vm.state.value.loading)
        vm.load()
        coVerify(exactly = 1) { reports.build(shift.id) }
        release.complete(Unit); awaitIdle(vm)
        assertEquals(rows, vm.state.value.patients)
        assertNull(vm.state.value.error)
    }

}
