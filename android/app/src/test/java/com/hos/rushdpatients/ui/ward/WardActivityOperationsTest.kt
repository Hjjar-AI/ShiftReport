package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WardActivityOperationsTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val state = MutableStateFlow(WardUiState())
    private val audit = mockk<AuditRepository>()
    private val admin = mockk<AdminAuthorizer>()
    private val sync = mockk<SyncService>()
    private val entry = AuditEntryEntity(1, "actor", "أرز", "synthetic", "p", null, null, "detail", 1)
    private fun operations(scope: CoroutineScope) = WardActivityOperations(text, state, scope, sync, audit, admin)
    private fun authorized() {
        coEvery { admin.requireAdmin() } returns Fixtures.doctor(rank = 1)
        coEvery { audit.getRecent(any()) } returns listOf(entry)
        coEvery { sync.getPublicationJournal() } returns emptyList()
    }
    @Test fun deniedActivityClearsPreviouslyDisplayedPrivilegedData() = runTest {
        state.value = state.value.copy(recentActivity = listOf(entry), activityLoading = true)
        coEvery { admin.requireAdmin() } throws IllegalStateException("synthetic denied")
        operations(this).loadRecentActivity(); runCurrent()
        assertTrue(state.value.recentActivity.isEmpty())
        assertTrue(state.value.publications.isEmpty())
        assertFalse(state.value.activityLoading)
        assertEquals("synthetic denied", state.value.activityError)
        coVerify(exactly = 0) { audit.getRecent(any()) }
        coVerify(exactly = 0) { sync.getPublicationJournal() }
    }
    @Test fun authorizedActivityChecksAuthorityBeforeReadingAuditAndJournal() = runTest {
        authorized(); operations(this).loadRecentActivity(); runCurrent()
        assertEquals(listOf(entry), state.value.recentActivity)
        assertFalse(state.value.activityLoading)
        assertNull(state.value.activityError)
        coVerifyOrder { admin.requireAdmin(); audit.getRecent(any()); sync.getPublicationJournal() }
    }
    @Test fun duplicateActivityRequestSharesOneActiveLoad() = runTest {
        authorized()
        coEvery { audit.getRecent(any()) } coAnswers { awaitCancellation() }
        val operations = operations(backgroundScope)
        operations.loadRecentActivity(); runCurrent(); operations.loadRecentActivity(); runCurrent()
        assertTrue(state.value.activityLoading)
        coVerify(exactly = 1) { admin.requireAdmin() }
        coVerify(exactly = 1) { audit.getRecent(any()) }
    }
    @Test fun auditFailureStopsJournalLoadAndExposesRetryableError() = runTest {
        authorized(); coEvery { audit.getRecent(any()) } throws IllegalStateException("synthetic storage failure")
        operations(this).loadRecentActivity(); runCurrent()
        assertFalse(state.value.activityLoading)
        assertEquals("synthetic storage failure", state.value.activityError)
        coVerify(exactly = 0) { sync.getPublicationJournal() }
    }
    @Test fun journalFailurePreservesLoadedLocalAudit() = runTest {
        authorized(); coEvery { sync.getPublicationJournal() } throws IllegalStateException("synthetic journal failure")
        operations(this).loadRecentActivity(); runCurrent()
        assertEquals(listOf(entry), state.value.recentActivity)
        assertEquals("synthetic journal failure", state.value.activityError)
        assertFalse(state.value.activityLoading)
    }
    @Test fun completedFailureAllowsExplicitRetryAndClearsOldError() = runTest {
        authorized(); coEvery { audit.getRecent(any()) } throws IllegalStateException("synthetic storage failure")
        val operations = operations(this); operations.loadRecentActivity(); runCurrent()
        coEvery { audit.getRecent(any()) } returns listOf(entry)
        operations.loadRecentActivity(); runCurrent()
        assertEquals(listOf(entry), state.value.recentActivity)
        assertNull(state.value.activityError)
        coVerify(exactly = 2) { admin.requireAdmin() }
    }
    @Test fun cancellationClearsLoadingWithoutOrdinaryError() = runTest {
        authorized(); coEvery { audit.getRecent(any()) } coAnswers { awaitCancellation() }
        val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
        try {
            operations(scope).loadRecentActivity(); runCurrent()
            assertTrue(state.value.activityLoading)
            job.cancel(); runCurrent()
            assertFalse(state.value.activityLoading)
            assertNull(state.value.activityError)
            coVerify(exactly = 0) { sync.getPublicationJournal() }
        } finally { job.cancel() }
    }
    @Test fun localActivityRefreshDoesNotFetchRemoteJournal() = runTest {
        coEvery { audit.getRecent(any()) } returns listOf(entry)
        operations(this).loadLocalActivity(); runCurrent()
        assertEquals(listOf(entry), state.value.recentActivity)
        coVerify(exactly = 0) { sync.getPublicationJournal() }
    }
}
