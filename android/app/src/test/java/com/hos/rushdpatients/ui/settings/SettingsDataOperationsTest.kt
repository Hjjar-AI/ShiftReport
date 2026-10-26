package com.hos.rushdpatients.ui.settings

import android.content.Context
import android.net.Uri
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.domain.export.PatientCsvExporter
import com.hos.rushdpatients.sync.*
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.util.NetworkStatus
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataOperationsTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val context = mockk<Context>()
    private val state = MutableStateFlow(SettingsUiState())
    private val sync = mockk<SyncService>()
    private val shifts = mockk<ShiftRepository>()
    private val exporter = mockk<PatientCsvExporter>()
    private val review = CsvPublicationReview("shift-1", "synthetic CSV", SyncState(), "actor", null, Fixtures.shift(), emptyList(), null, "مراجعة تجريبية")
    private fun operations(scope: CoroutineScope) = SettingsDataOperations(text, state, scope, context, sync, shifts, exporter) { "synthetic time" }
    @Before fun online() {
        mockkObject(NetworkStatus)
        every { NetworkStatus.isOnline(context) } returns true
        coEvery { shifts.getLatest() } returns Fixtures.shift()
        coEvery { sync.prepareCsvPublication("shift-1") } returns review
        coEvery { sync.uploadReviewedCsv(review) } returns Result.success(CsvUploadResult(1, 3))
    }
    @After fun releaseNetworkMock() { unmockkObject(NetworkStatus) }
    @Test fun uploadPreparesReviewWithoutPublishing() = runTest {
        operations(this).uploadCurrent(); runCurrent()
        assertEquals(review.text, state.value.publicationReviewText)
        assertFalse(state.value.syncing)
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
    }
    @Test fun confirmationWithoutPreparedReviewCannotPublish() = runTest {
        operations(this).confirmPublication(); runCurrent()
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
    }
    @Test fun confirmationConsumesExactReviewAndCannotRepeatPublication() = runTest {
        val operations = operations(this); operations.uploadCurrent(); runCurrent()
        operations.confirmPublication(); runCurrent(); operations.confirmPublication(); runCurrent()
        coVerify(exactly = 1) { sync.uploadReviewedCsv(review) }
        assertNull(state.value.publicationReviewText)
        assertFalse(state.value.syncing)
        assertNotNull(state.value.lastOperation)
    }
    @Test fun cancellingPublicationReviewPreventsUpload() = runTest {
        val operations = operations(this); operations.uploadCurrent(); runCurrent()
        operations.cancelPublicationReview(); operations.confirmPublication(); runCurrent()
        assertNull(state.value.publicationReviewText)
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
    }
    @Test fun invalidatingPublicationTokenPreventsStaleConfirmation() = runTest {
        val operations = operations(this); operations.uploadCurrent(); runCurrent()
        operations.invalidateReview(); operations.confirmPublication(); runCurrent()
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
    }
    @Test fun offlinePreparationOffersRetryWithoutReadingOrPublishing() = runTest {
        every { NetworkStatus.isOnline(context) } returns false
        operations(this).uploadCurrent(); runCurrent()
        assertFalse(state.value.syncing)
        assertEquals(SettingsRetryAction.UPLOAD, state.value.retryAction)
        coVerify(exactly = 0) { shifts.getLatest() }
        coVerify(exactly = 0) { sync.prepareCsvPublication(any(), any()) }
    }
    @Test fun goingOfflineAfterReviewConsumesReviewWithoutSending() = runTest {
        val operations = operations(this); operations.uploadCurrent(); runCurrent()
        every { NetworkStatus.isOnline(context) } returns false
        operations.confirmPublication(); runCurrent()
        assertEquals(SettingsRetryAction.UPLOAD, state.value.retryAction)
        assertNull(state.value.publicationReviewText)
        every { NetworkStatus.isOnline(context) } returns true
        operations.confirmPublication(); runCurrent()
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
    }
    @Test fun publicationFailureRequiresNewReviewBeforeRetry() = runTest {
        coEvery { sync.uploadReviewedCsv(review) } returns Result.failure(IllegalStateException("synthetic stale source"))
        val operations = operations(this); operations.uploadCurrent(); runCurrent()
        operations.confirmPublication(); runCurrent(); operations.confirmPublication(); runCurrent()
        assertEquals("synthetic stale source", state.value.snackbar)
        assertFalse(state.value.syncing)
        assertEquals(SettingsRetryAction.UPLOAD, state.value.retryAction)
        coVerify(exactly = 1) { sync.uploadReviewedCsv(review) }
    }
    @Test fun duplicatePreparationCannotStartSecondReview() = runTest {
        coEvery { sync.prepareCsvPublication(any(), any()) } coAnswers { awaitCancellation() }
        val operations = operations(backgroundScope); operations.uploadCurrent(); runCurrent()
        operations.uploadCurrent(); operations.confirmPublication(); runCurrent()
        assertTrue(state.value.syncing)
        coVerify(exactly = 1) { sync.prepareCsvPublication(any(), any()) }
    }
    @Test fun cancelledPreparationClearsBusyWithoutOrdinaryFailure() = runTest {
        coEvery { sync.prepareCsvPublication(any(), any()) } throws CancellationException("synthetic cancel")
        operations(this).uploadCurrent(); runCurrent()
        assertFalse(state.value.syncing)
        assertNull(state.value.snackbar)
        assertNull(state.value.retryAction)
    }
    @Test fun offlineFetchDoesNotReadRemoteDoctorsOrPatients() = runTest {
        every { NetworkStatus.isOnline(context) } returns false
        operations(this).bringLatest(); runCurrent()
        assertEquals(SettingsRetryAction.FETCH, state.value.retryAction)
        assertFalse(state.value.syncing)
        coVerify(exactly = 0) { sync.refreshDoctorsForPatientFetch() }
        coVerify(exactly = 0) { sync.fetchLatestCsv() }
    }
    @Test fun doctorRefreshFailureStopsPatientDownload() = runTest {
        coEvery { sync.refreshDoctorsForPatientFetch() } returns Result.failure(IllegalStateException("synthetic registry failure"))
        operations(this).bringLatest(); runCurrent()
        assertFalse(state.value.syncing)
        assertEquals(SettingsRetryAction.FETCH, state.value.retryAction)
        assertEquals("synthetic registry failure", state.value.syncIssue)
        coVerify(exactly = 0) { sync.fetchLatestCsv() }
    }
    @Test fun successfulFetchRefreshesDoctorsBeforePatientsAndStampsTime() = runTest {
        coEvery { sync.refreshDoctorsForPatientFetch() } returns Result.success(Unit)
        coEvery { sync.fetchLatestCsv() } returns Result.success(CsvFetchResult("shift-1", 2, 1))
        operations(this).bringLatest(); runCurrent()
        coVerifyOrder { sync.refreshDoctorsForPatientFetch(); sync.fetchLatestCsv() }
        assertFalse(state.value.syncing)
        assertNull(state.value.retryAction)
        assertEquals("synthetic time", state.value.lastCsvSync)
        assertNotNull(state.value.lastOperation)
    }
    @Test fun downloadFailureDoesNotStampSuccessfulSync() = runTest {
        coEvery { sync.refreshDoctorsForPatientFetch() } returns Result.success(Unit)
        coEvery { sync.fetchLatestCsv() } returns Result.failure(IllegalStateException("synthetic download failure"))
        operations(this).bringLatest(); runCurrent()
        assertFalse(state.value.syncing)
        assertNull(state.value.lastCsvSync)
        assertEquals(SettingsRetryAction.FETCH, state.value.retryAction)
    }
    @Test fun undoIsBlockedDuringBackupExportOrSync() = runTest {
        val operations = operations(this)
        for (busy in listOf(SettingsUiState(backupBusy = true), SettingsUiState(exportingCsv = true), SettingsUiState(syncing = true))) {
            state.value = busy; operations.undoLastDownload(); runCurrent()
        }
        coVerify(exactly = 0) { sync.undoLastDownload() }
    }
    @Test fun undoFailureReleasesBusyAndShowsConflict() = runTest {
        coEvery { sync.undoLastDownload() } returns Result.failure(IllegalStateException("synthetic changed local data"))
        operations(this).undoLastDownload(); runCurrent()
        assertFalse(state.value.syncing)
        assertEquals("synthetic changed local data", state.value.snackbar)
    }
    @Test fun duplicateUndoCannotStartAnotherRestore() = runTest {
        coEvery { sync.undoLastDownload() } coAnswers { awaitCancellation() }
        val operations = operations(backgroundScope); operations.undoLastDownload(); runCurrent()
        operations.undoLastDownload(); runCurrent()
        coVerify(exactly = 1) { sync.undoLastDownload() }
    }
    @Test fun exportSuccessRecordsFilenameWithoutNetworkSync() = runTest {
        coEvery { exporter.exportCurrentShift() } returns PatientCsvExporter.Result.Success(mockk<Uri>(), "synthetic.csv", 2)
        operations(this).exportCsv(); runCurrent()
        assertFalse(state.value.exportingCsv)
        assertNotNull(state.value.lastOperation)
        assertNull(state.value.retryAction)
        coVerify(exactly = 0) { sync.uploadReviewedCsv(any()) }
        verify(exactly = 0) { NetworkStatus.isOnline(any()) }
    }
    @Test fun exportFailureOffersExplicitRetry() = runTest {
        coEvery { exporter.exportCurrentShift() } returns PatientCsvExporter.Result.Failure("synthetic write failure")
        operations(this).exportCsv(); runCurrent()
        assertFalse(state.value.exportingCsv)
        assertEquals("synthetic write failure", state.value.snackbar)
        assertEquals(SettingsRetryAction.EXPORT, state.value.retryAction)
    }
    @Test fun duplicateExportCannotCreateTwoFiles() = runTest {
        coEvery { exporter.exportCurrentShift() } coAnswers { awaitCancellation() }
        val operations = operations(backgroundScope); operations.exportCsv(); operations.exportCsv(); runCurrent()
        coVerify(exactly = 1) { exporter.exportCurrentShift() }
    }
}
