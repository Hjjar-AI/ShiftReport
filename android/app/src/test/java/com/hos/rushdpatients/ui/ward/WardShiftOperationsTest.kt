package com.hos.rushdpatients.ui.ward

import android.content.Context
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.export.PatientCsvExporter
import com.hos.rushdpatients.sync.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.util.NetworkStatus
import com.hos.rushdpatients.util.ShiftDate
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WardShiftOperationsTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val shift = Fixtures.shift(date = ShiftDate.current()).copy(revision = 7)
    private val state = MutableStateFlow(WardUiState(shift = shift))
    private val shifts = mockk<ShiftRepository>()
    private val patients = mockk<PatientRepository>()
    private val mutations = mockk<WardMutationRepository>()
    private val sync = mockk<SyncService>()
    private val settings = mockk<SettingsRepository>()
    private val exporter = mockk<PatientCsvExporter>()
    private val sessions = mockk<SessionManager> { every { current() } returns null }
    private val context = mockk<Context>()
    private val observed = mutableListOf<String>()
    private fun operations(scope: CoroutineScope) = WardShiftOperations(text, state, scope, context, sync, exporter, WardSaveGuard(text, state), { observed.add(it) })
    @Before fun setup() {
        mockkObject(NetworkStatus); every { NetworkStatus.isOnline(context) } returns true
        coEvery { shifts.getById(shift.id) } returns shift
    }
    @After fun releaseNetworkMock() { unmockkObject(NetworkStatus) }
    @Test fun offlineFetchAndExplicitReplacementOfferLatestRetry() = runTest {
        every { NetworkStatus.isOnline(context) } returns false
        val operations = operations(this); operations.bringLatestData()
        assertEquals(WardRetryAction.FETCH_LATEST, state.value.retryAction)
        operations.replaceAndFetch(); assertEquals(WardRetryAction.FETCH_LATEST, state.value.retryAction)
        coVerify(exactly = 0) { sync.refreshDoctorsForPatientFetch() }
        assertTrue(observed.isEmpty())
    }
    @Test fun failedDoctorRefreshBlocksClinicalDownload() = runTest {
        coEvery { sync.refreshDoctorsForPatientFetch() } returns Result.failure(IllegalStateException("synthetic registry failure"))
        operations(this).bringLatestData(); runCurrent()
        assertFalse(state.value.syncing); assertEquals("synthetic registry failure", state.value.syncIssue)
        coVerify(exactly = 0) { sync.fetchLatestCsv(false, false) }
    }
    @Test fun latestFetchLoadsDoctorsThenPatientsAndSelectsReturnedShift() = runTest {
        coEvery { sync.refreshDoctorsForPatientFetch() } returns Result.success(Unit)
        coEvery { sync.fetchLatestCsv(false, false) } returns Result.success(CsvFetchResult("downloaded", 1, 2))
        operations(this).bringLatestData(); runCurrent()
        coVerifyOrder { sync.refreshDoctorsForPatientFetch(); sync.fetchLatestCsv(false, false) }
        assertEquals(listOf("downloaded"), observed); assertFalse(state.value.syncing)
        assertNull(state.value.retryAction)
    }
    @Test fun replacementFetchUsesExplicitReplaceFlagAndReturnedSection() = runTest {
        coEvery { sync.refreshDoctorsForPatientFetch() } returns Result.success(Unit)
        coEvery { sync.fetchLatestCsv(true, false) } returns Result.success(CsvFetchResult("previous", 1, 0))
        operations(this).replaceAndFetch(); runCurrent()
        assertEquals(listOf("previous"), observed)
        coVerify(exactly = 1) { sync.fetchLatestCsv(true, false) }
        coVerify(exactly = 0) { sync.fetchLatestCsv(false, false) }
    }
    @Test fun failedPatientFetchLeavesDisplayedShiftAndOffersRetry() = runTest {
        coEvery { sync.refreshDoctorsForPatientFetch() } returns Result.success(Unit)
        coEvery { sync.fetchLatestCsv(true, false) } returns Result.failure(IllegalStateException("synthetic bad snapshot"))
        operations(this).replaceAndFetch(); runCurrent()
        assertEquals(shift, state.value.shift); assertTrue(observed.isEmpty())
        assertEquals(WardRetryAction.FETCH_LATEST, state.value.retryAction); assertFalse(state.value.syncing)
    }
    @Test fun duplicateFetchCannotDownloadTwoSnapshots() = runTest {
        coEvery { sync.refreshDoctorsForPatientFetch() } coAnswers { awaitCancellation() }
        val operations = operations(backgroundScope); operations.bringLatestData(); runCurrent()
        operations.replaceAndFetch(); runCurrent()
        coVerify(exactly = 1) { sync.refreshDoctorsForPatientFetch() }
    }
    @Test fun undoSelectsRestoredShiftOnlyAfterSuccess() = runTest {
        coEvery { sync.undoLastDownload() } returns Result.success("restored")
        operations(this).undoLastDownload(); runCurrent()
        assertEquals(listOf("restored"), observed); assertFalse(state.value.syncing)
    }
    @Test fun undoConflictKeepsCurrentShiftAndClearsBusy() = runTest {
        coEvery { sync.undoLastDownload() } returns Result.failure(IllegalStateException("synthetic local edits"))
        operations(this).undoLastDownload(); runCurrent()
        assertTrue(observed.isEmpty()); assertEquals(shift, state.value.shift)
        assertEquals("synthetic local edits", state.value.snackbar); assertFalse(state.value.syncing)
    }
    @Test fun undoIsBlockedDuringSaveOrDownload() = runTest {
        val operations = operations(this)
        state.value = state.value.copy(saving = true); operations.undoLastDownload()
        state.value = state.value.copy(saving = false, syncing = true); operations.undoLastDownload(); runCurrent()
        coVerify(exactly = 0) { sync.undoLastDownload() }
    }
    @Test fun exportUsesDisplayedShiftRatherThanCurrentDateShift() = runTest {
        state.value = state.value.copy(shift = shift.copy(id = "historical", date = shift.date.minusDays(1)))
        coEvery { exporter.exportShift("historical") } returns PatientCsvExporter.Result.Failure("synthetic storage failure")
        operations(this).exportCsv(); runCurrent()
        coVerify(exactly = 1) { exporter.exportShift("historical") }
        assertFalse(state.value.exportingCsv); assertEquals(WardRetryAction.EXPORT_CSV, state.value.retryAction)
    }
    @Test fun missingShiftCannotExport() = runTest {
        state.value = state.value.copy(shift = null)
        operations(this).exportCsv(); runCurrent()
        coVerify(exactly = 0) { exporter.exportShift(any()) }
        assertFalse(state.value.exportingCsv)
    }
}
