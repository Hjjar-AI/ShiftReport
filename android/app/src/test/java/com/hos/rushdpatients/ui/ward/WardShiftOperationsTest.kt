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
    private fun operations(scope: CoroutineScope) = WardShiftOperations(text, state, scope, context, shifts,
        patients, mutations, sync, settings, exporter, sessions, WardSaveGuard(text, state, shifts), { observed.add(it) })
    @Before fun setup() {
        mockkObject(NetworkStatus); every { NetworkStatus.isOnline(context) } returns true
        coEvery { shifts.getById(shift.id) } returns shift
    }
    @After fun releaseNetworkMock() { unmockkObject(NetworkStatus) }
    @Test fun offlineLatestAndPreviousFetchOfferTheCorrectRetry() = runTest {
        every { NetworkStatus.isOnline(context) } returns false
        val operations = operations(this); operations.bringLatestData()
        assertEquals(WardRetryAction.FETCH_LATEST, state.value.retryAction)
        operations.bringPreviousData(); assertEquals(WardRetryAction.FETCH_PREVIOUS, state.value.retryAction)
        coVerify(exactly = 0) { sync.refreshDoctorsFromRemote() }
        assertTrue(observed.isEmpty())
    }
    @Test fun failedDoctorRefreshBlocksClinicalDownload() = runTest {
        coEvery { sync.refreshDoctorsFromRemote() } returns Result.failure(IllegalStateException("synthetic registry failure"))
        operations(this).bringLatestData(); runCurrent()
        assertFalse(state.value.syncing); assertEquals("synthetic registry failure", state.value.snackbar)
        coVerify(exactly = 0) { sync.fetchLatestCsv() }
    }
    @Test fun latestFetchLoadsDoctorsThenPatientsAndSelectsReturnedShift() = runTest {
        coEvery { sync.refreshDoctorsFromRemote() } returns Result.success(Unit)
        coEvery { sync.fetchLatestCsv() } returns Result.success(CsvFetchResult("downloaded", 1, 2))
        operations(this).bringLatestData(); runCurrent()
        coVerifyOrder { sync.refreshDoctorsFromRemote(); sync.fetchLatestCsv() }
        assertEquals(listOf("downloaded"), observed); assertFalse(state.value.syncing)
        assertNull(state.value.retryAction)
    }
    @Test fun previousFetchUsesPreviousTransportAndReturnedShift() = runTest {
        coEvery { sync.refreshDoctorsFromRemote() } returns Result.success(Unit)
        coEvery { sync.fetchPreviousCsv() } returns Result.success(CsvFetchResult("previous", 1, 0))
        operations(this).bringPreviousData(); runCurrent()
        assertEquals(listOf("previous"), observed)
        coVerify(exactly = 0) { sync.fetchLatestCsv() }
    }
    @Test fun failedPatientFetchLeavesDisplayedShiftAndOffersRetry() = runTest {
        coEvery { sync.refreshDoctorsFromRemote() } returns Result.success(Unit)
        coEvery { sync.fetchPreviousCsv() } returns Result.failure(IllegalStateException("synthetic bad snapshot"))
        operations(this).bringPreviousData(); runCurrent()
        assertEquals(shift, state.value.shift); assertTrue(observed.isEmpty())
        assertEquals(WardRetryAction.FETCH_PREVIOUS, state.value.retryAction); assertFalse(state.value.syncing)
    }
    @Test fun duplicateFetchCannotDownloadTwoSnapshots() = runTest {
        coEvery { sync.refreshDoctorsFromRemote() } coAnswers { awaitCancellation() }
        val operations = operations(backgroundScope); operations.bringLatestData(); runCurrent()
        operations.bringPreviousData(); runCurrent()
        coVerify(exactly = 1) { sync.refreshDoctorsFromRemote() }
    }
    @Test fun snapshotPickerOffersOnlyNewestBundleSortedByDate() = runTest {
        val recent = listOf(shift.copy(id = "older-bundle", csvMessageId = 10),
            shift.copy(id = "first", csvMessageId = 20, date = shift.date.minusDays(1)),
            shift.copy(id = "latest", csvMessageId = 20), shift.copy(id = "local", csvMessageId = null))
        coEvery { shifts.getRecent(12) } returns recent
        coEvery { patients.countForShift(any()) } returns 2
        operations(this).showDownloadedSnapshots(); runCurrent()
        assertEquals(listOf("latest", "first").take(AppConstants.CSV_BUNDLE_SHIFT_COUNT), state.value.snapshotChoices.map { it.shiftId })
        assertTrue(state.value.snapshotChoices.all { it.patientCount == 2 }); assertTrue(state.value.showSnapshotPicker)
        coVerify(exactly = 0) { patients.countForShift("older-bundle") }
    }
    @Test fun absentDownloadedSnapshotsDoNotOpenPicker() = runTest {
        coEvery { shifts.getRecent(12) } returns listOf(shift)
        operations(this).showDownloadedSnapshots(); runCurrent()
        assertFalse(state.value.showSnapshotPicker); assertTrue(state.value.snapshotChoices.isEmpty())
        assertNotNull(state.value.snackbar)
    }
    @Test fun arbitrarySnapshotIdCannotSwitchDisplayedShift() = runTest {
        state.value = state.value.copy(snapshotChoices = listOf(ShiftSnapshotOption("allowed", shift.date, 2)), showSnapshotPicker = true)
        operations(this).selectDownloadedSnapshot("unreviewed")
        assertTrue(observed.isEmpty()); assertTrue(state.value.showSnapshotPicker)
    }
    @Test fun choosingReviewedSnapshotSelectsItAndClosesPicker() = runTest {
        state.value = state.value.copy(snapshotChoices = listOf(ShiftSnapshotOption("allowed", shift.date, 2)), showSnapshotPicker = true)
        operations(this).selectDownloadedSnapshot("allowed")
        assertEquals(listOf("allowed"), observed); assertFalse(state.value.showSnapshotPicker)
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
    @Test fun rolloverOfferRejectsNonemptyTargetBeforeReadingPreviousShift() = runTest {
        coEvery { patients.countForShift(shift.id) } returns 1
        operations(this).offerPreviousShift(); runCurrent()
        assertTrue(state.value.rolloverPatients.isEmpty())
        coVerify(exactly = 0) { shifts.getRecent(any()) }
    }
    @Test fun rolloverDismissalClearsPreviewWithoutChangingClinicalRecords() = runTest {
        val source = Fixtures.patient()
        state.value = state.value.copy(patients = listOf(source), rolloverPatients = listOf(source), rolloverTarget = shift, rolloverSourceLabel = "preview")
        operations(this).dismissRollover()
        assertTrue(state.value.rolloverPatients.isEmpty()); assertNull(state.value.rolloverTarget)
        assertEquals("", state.value.rolloverSourceLabel); assertEquals(listOf(source), state.value.patients)
    }
    @Test fun confirmedRolloverReachesRepositoryWithFreshIdsPendingTasksAndExpectedRevision() = runTest {
        val pending = Fixtures.task().copy(ownerDoctorId = "tree", ownerName = "أرز")
        val source = Fixtures.patient(responsibleResidentId = "tree", labs = "نتيجة").copy(
            tasks = listOf(pending, Fixtures.task("done", true)), revision = 5)
        state.value = state.value.copy(rolloverPatients = listOf(source), rolloverTarget = shift)
        coEvery { mutations.rollover(any(), shift.id, shift.revision, any(), null) } returns Unit
        operations(this).applyRollover(keepAssignments = false); runCurrent()
        val inserted = slot<List<Patient>>()
        coVerify(exactly = 1) { mutations.rollover(capture(inserted), shift.id, 7,
            listOf(source to "reassign"), null) }
        val saved = inserted.captured.single()
        assertNotEquals(source.id, saved.id); assertEquals(source.labs, saved.labs)
        assertEquals(1L, saved.revision); assertEquals(1, saved.sortOrder)
        assertNull(saved.responsibleResidentId)
        assertEquals(listOf(pending.copy(ownerDoctorId = null, ownerName = null)), saved.tasks)
        assertFalse(state.value.saving); assertNull(state.value.rolloverTarget)
    }
    @Test fun suspendedRolloverBlocksDuplicateConfirmationAndKeepsAssignments() = runTest {
        val source = Fixtures.patient(responsibleResidentId = "tree").copy(tasks = listOf(Fixtures.task().copy(ownerDoctorId = "tree")))
        state.value = state.value.copy(rolloverPatients = listOf(source), rolloverTarget = shift)
        val release = CompletableDeferred<Unit>()
        coEvery { mutations.rollover(any(), shift.id, shift.revision, any(), null) } coAnswers { release.await() }
        val operations = operations(this)
        operations.applyRollover(keepAssignments = true)
        assertTrue(state.value.saving)
        operations.applyRollover(keepAssignments = true)
        coVerify(exactly = 1) { mutations.rollover(match { it.single().responsibleResidentId == "tree" && it.single().tasks == source.tasks },
            shift.id, shift.revision, listOf(source to "continue"), null) }
        release.complete(Unit); runCurrent()
        assertFalse(state.value.saving)
    }
    @Test fun rejectedRolloverReleasesBusyStateAndRetainsReview() = runTest {
        val source = Fixtures.patient()
        state.value = state.value.copy(rolloverPatients = listOf(source), rolloverTarget = shift)
        coEvery { mutations.rollover(any(), any(), any(), any(), any()) } throws IllegalStateException("synthetic stale revision")
        operations(this).applyRollover(keepAssignments = false); runCurrent()
        assertFalse(state.value.saving)
        assertEquals("synthetic stale revision", state.value.snackbar)
        assertEquals(listOf(source), state.value.rolloverPatients)
        assertEquals(shift, state.value.rolloverTarget)
    }
}
