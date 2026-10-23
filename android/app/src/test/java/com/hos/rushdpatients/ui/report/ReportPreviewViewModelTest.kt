package com.hos.rushdpatients.ui.report

import android.content.Context
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
    private fun built() = BuiltReport(shift, rows, listOf(actor), mapOf(actor.id to actor.fullName),
        ReportSummary(rows.size, rows.size, 0, shift.date, listOf(actor)), listOf("synthetic review"))
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
        coEvery { sync.describeLocalChanges(shift.id) } returns HandoverChangeSummary(emptyList(), emptyList(), emptyList())
        coEvery { sync.prepareCsvPublication(shift.id, any()) } answers {
            CsvPublicationReview(shift.id, "synthetic csv", SyncState(), actor.id, actor.telegramId, "synthetic review")
        }
        coEvery { sync.uploadReviewedCsv(any(), any()) } returns Result.success(CsvUploadResult(10, 1))
        coEvery { sender.sendTextReport(any(), any(), any(), any()) } returns SendResult(listOf(20), 20, Fixtures.timestamp)
    }
    private fun viewModel() = ReportPreviewViewModel(text, SavedStateHandle(mapOf("shiftId" to shift.id)), context,
        reports, TextReportBuilder(), sender, mockk(), mockk(), mockk(), mockk(), settings, doctors, shifts,
        mockk(), sessions, mockk(), sync, projects, ledger).also { store.put("report", it) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain(); unmockkObject(NetworkStatus) }
    @Test fun `loading review never sends and explicit send publishes before delivery`() {
        val vm = viewModel()
        assertFalse(vm.state.value.loading)
        coVerify(exactly = 0) { sender.sendTextReport(any(), any(), any(), any()) }
        vm.send()
        coVerifyOrder {
            sender.checkGeneralDelivery(any(), any(), false)
            sync.uploadReviewedCsv(any(), false)
            sender.sendTextReport(any(), any(), actor, any())
        }
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.publicationOutcome)
        assertEquals(ReportStepOutcome.CONFIRMED, vm.state.value.deliveryOutcome)
        assertFalse(vm.state.value.sending)
    }
    @Test fun `clinical change after preview prevents publication and delivery`() {
        val vm = viewModel()
        rows = listOf(rows.single().copy(labs = "changed since review"))
        vm.send()
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any(), any()) }
        coVerify(exactly = 0) { sender.sendTextReport(any(), any(), any(), any()) }
    }
    @Test fun `publication failure never starts report delivery`() {
        val vm = viewModel()
        coEvery { sync.uploadReviewedCsv(any(), any()) } returns Result.failure(IllegalStateException("publication failed"))
        vm.send()
        assertFalse(vm.state.value.sending)
        assertTrue(vm.state.value.error.orEmpty().contains("publication failed"))
        coVerify(exactly = 0) { sender.sendTextReport(any(), any(), any(), any()) }
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
    @Test fun `archived report cannot publish`() {
        shift = shift.copy(date = ShiftDate.current().minusDays(1))
        val vm = viewModel()
        vm.send()
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any(), any()) }
        assertNotNull(vm.state.value.error)
    }
}
