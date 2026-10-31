package com.hos.rushdpatients.ui.ward

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.hos.rushdpatients.config.ProjectConfig
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.sync.PatientPublicationStatus
import com.hos.rushdpatients.sync.PublicationJournalEntry
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.testutil.ViewModelFixture
import com.hos.rushdpatients.ui.settings.SettingsViewModel
import com.hos.rushdpatients.util.NetworkStatus
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Existing history cases follow admin publication history into Settings; clinical audit UI is retired. */
class WardActivityOperationsTest : ViewModelFixture() {
    private val context = mockk<Context>(relaxed = true)
    private val admin = mockk<AdminAuthorizer>()
    private val sync = mockk<SyncService>()
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val doctors = mockk<DoctorRepository>(relaxed = true)
    private val sessions = mockk<SessionManager>()
    private val publication = mockk<PatientPublicationStatus>()
    private val projects = mockk<ProjectConfigStore>()
    private val entry = PublicationJournalEntry("snapshot", fileId = "synthetic-file", messageId = 1, at = 1, deviceId = "synthetic-device")
    @Before fun configureHistory() {
        mockkObject(NetworkStatus)
        every { NetworkStatus.observe(context) } returns flowOf(false)
        every { sync.observeDownloadUndo() } returns flowOf(false)
        every { publication.observePending() } returns flowOf(false)
        every { sessions.current() } returns null
        every { sessions.sessionFlow } returns MutableStateFlow(null)
        every { doctors.observeAll() } returns flowOf(emptyList())
        every { settings.observe(any()) } returns flowOf(null)
        every { projects.current() } returns ProjectConfig()
        coEvery { settings.get(any()) } returns null
        coEvery { admin.requireAdmin() } returns Fixtures.doctor(rank = 1)
        coEvery { sync.getPublicationJournal() } returns listOf(entry)
    }
    @After fun releaseHistoryNetwork() { unmockkObject(NetworkStatus) }
    private fun model() = keep(SettingsViewModel(text, context, settings, doctors, mockk(), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), sessions, sync, publication, mockk(), mockk(),
        mockk(relaxed = true), mockk(), projects, mockk(), mockk(), admin, mockk(), SavedStateHandle()))
    private suspend fun settled(vm: SettingsViewModel) = withTimeout(5_000) {
        vm.state.first { !it.publicationHistoryLoading }
    }
    @Test fun revokedAuthorityPreventsFurtherJournalReads() = runBlocking<Unit> {
        val vm = model(); vm.loadPublicationHistory(); settled(vm)
        coEvery { admin.requireAdmin() } throws IllegalStateException("synthetic denied")
        vm.loadPublicationHistory()
        assertEquals("synthetic denied", settled(vm).publicationHistoryError)
        coVerify(exactly = 1) { sync.getPublicationJournal() }
        coVerify(exactly = 2) { admin.requireAdmin() }
    }
    @Test fun authorityIsCheckedBeforeReadingPublicationHistory() = runBlocking<Unit> {
        val vm = model(); vm.loadPublicationHistory()
        assertEquals(listOf(entry), settled(vm).publicationHistory)
        coVerifyOrder { admin.requireAdmin(); sync.getPublicationJournal() }
    }
    @Test fun duplicateHistoryRequestSharesOneActiveLoad() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        coEvery { sync.getPublicationJournal() } coAnswers { entered.complete(Unit); release.await(); listOf(entry) }
        val vm = model(); vm.loadPublicationHistory()
        withTimeout(5_000) { entered.await() }
        vm.loadPublicationHistory()
        coVerify(exactly = 1) { admin.requireAdmin() }
        coVerify(exactly = 1) { sync.getPublicationJournal() }
        release.complete(Unit)
        assertEquals(listOf(entry), settled(vm).publicationHistory)
    }
    @Test fun deniedHistoryCannotReadJournalAndExposesError() = runBlocking<Unit> {
        coEvery { admin.requireAdmin() } throws IllegalStateException("synthetic denied")
        val vm = model(); vm.loadPublicationHistory()
        assertEquals("synthetic denied", settled(vm).publicationHistoryError)
        assertTrue(vm.state.value.publicationHistory.isEmpty())
        coVerify(exactly = 0) { sync.getPublicationJournal() }
    }
    @Test fun journalFailurePreservesPreviouslyLoadedHistoryAndExposesError() = runBlocking<Unit> {
        val vm = model(); vm.loadPublicationHistory(); settled(vm)
        coEvery { sync.getPublicationJournal() } throws IllegalStateException("synthetic journal failure")
        vm.loadPublicationHistory()
        assertEquals("synthetic journal failure", settled(vm).publicationHistoryError)
        assertEquals(listOf(entry), vm.state.value.publicationHistory)
    }
    @Test fun completedFailureAllowsExplicitRetryAndClearsOldError() = runBlocking<Unit> {
        coEvery { sync.getPublicationJournal() } throws IllegalStateException("synthetic journal failure")
        val vm = model(); vm.loadPublicationHistory(); settled(vm)
        coEvery { sync.getPublicationJournal() } returns listOf(entry)
        vm.loadPublicationHistory()
        assertEquals(listOf(entry), settled(vm).publicationHistory)
        assertNull(vm.state.value.publicationHistoryError)
        coVerify(exactly = 2) { admin.requireAdmin() }
    }
    @Test fun cancellationClearsLoadingWithoutOrdinaryError() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        coEvery { sync.getPublicationJournal() } coAnswers { entered.complete(Unit); awaitCancellation() }
        val vm = model(); vm.loadPublicationHistory()
        withTimeout(5_000) { entered.await() }
        clearViewModels()
        settled(vm)
        assertNull(vm.state.value.publicationHistoryError)
    }
    @Test fun openingSettingsDoesNotAutomaticallyReadRemoteHistory() {
        model()
        coVerify(exactly = 0) { admin.requireAdmin() }
        coVerify(exactly = 0) { sync.getPublicationJournal() }
    }
}
