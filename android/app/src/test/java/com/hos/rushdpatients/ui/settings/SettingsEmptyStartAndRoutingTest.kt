package com.hos.rushdpatients.ui.settings

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsEmptyStartAndRoutingTest : SettingsProjectFixture() {
    @Test fun emptyStartPreparationShowsReviewWithoutDeleting() = runTest {
        coEvery { emptyStart.prepare() } returns review
        operations(this).prepareEmptyStart(); runCurrent()
        assertEquals(review.text, state.value.emptyStartReview)
        assertFalse(state.value.emptyStartBusy)
        assertEquals(0, invalidations)
        coVerify(exactly = 0) { emptyStart.confirm(any()) }
    }
    @Test fun emptyStartConfirmationWithoutReviewCannotDelete() = runTest {
        operations(this).confirmEmptyStart(); runCurrent()
        coVerify(exactly = 0) { emptyStart.confirm(any()) }
        assertEquals(0, invalidations)
    }
    @Test fun dismissalDiscardsDestructiveReview() = runTest {
        coEvery { emptyStart.prepare() } returns review
        val operations = operations(this); operations.prepareEmptyStart(); runCurrent()
        operations.dismissEmptyStart(); operations.confirmEmptyStart(); runCurrent()
        assertNull(state.value.emptyStartReview)
        coVerify(exactly = 0) { emptyStart.confirm(any()) }
    }
    @Test fun confirmedResetConsumesReviewInvalidatesPublicationAndReloadsOnce() = runTest {
        coEvery { emptyStart.prepare() } returns review
        coEvery { emptyStart.confirm(review) } returns 1L
        val operations = operations(this); operations.prepareEmptyStart(); runCurrent()
        operations.confirmEmptyStart(); operations.confirmEmptyStart(); runCurrent()
        coVerify(exactly = 1) { emptyStart.confirm(review) }
        assertEquals(1, invalidations); assertEquals(1, reloads)
        assertNull(state.value.emptyStartReview); assertFalse(state.value.emptyStartBusy)
        assertNotNull(state.value.emptyStartResult)
    }
    @Test fun uncertainResetRequiresFreshReviewAndDoesNotReloadAsSuccess() = runTest {
        coEvery { emptyStart.prepare() } returns review
        coEvery { emptyStart.confirm(review) } throws IllegalStateException("synthetic uncertain reset")
        val operations = operations(this); operations.prepareEmptyStart(); runCurrent()
        operations.confirmEmptyStart(); runCurrent(); operations.confirmEmptyStart(); runCurrent()
        coVerify(exactly = 1) { emptyStart.confirm(review) }
        assertEquals(0, reloads); assertEquals(1, invalidations)
        assertEquals("synthetic uncertain reset", state.value.emptyStartResult)
        assertFalse(state.value.emptyStartBusy)
    }
    @Test fun otherBusyWorkPreventsResetPreparation() = runTest {
        val operations = operations(this)
        for (busy in listOf(SettingsUiState(emptyStartBusy = true), SettingsUiState(syncing = true),
            SettingsUiState(backupBusy = true), SettingsUiState(provisioningBusy = true), SettingsUiState(exportingCsv = true))) {
            state.value = busy; operations.prepareEmptyStart(); runCurrent()
        }
        coVerify(exactly = 0) { emptyStart.prepare() }
    }
    @Test fun duplicatePreparationAndDismissalCannotReplaceActiveReset() = runTest {
        coEvery { emptyStart.prepare() } coAnswers { awaitCancellation() }
        val operations = operations(backgroundScope); operations.prepareEmptyStart(); runCurrent()
        operations.prepareEmptyStart(); operations.dismissEmptyStart(); runCurrent()
        assertTrue(state.value.emptyStartBusy)
        coVerify(exactly = 1) { emptyStart.prepare() }
    }
    @Test fun cancelledConfirmationReleasesBusyWithoutSuccessOrReload() = runTest {
        coEvery { emptyStart.prepare() } returns review
        coEvery { emptyStart.confirm(review) } throws CancellationException("synthetic cancel")
        val operations = operations(this); operations.prepareEmptyStart(); runCurrent()
        operations.confirmEmptyStart(); runCurrent()
        assertFalse(state.value.emptyStartBusy); assertNull(state.value.emptyStartResult)
        assertEquals(0, reloads)
    }
    @Test fun nonAdminSessionCannotChangeSupervisorDestination() = runTest {
        every { sessions.current() } returns null
        operations(this).setSupervisorGroupChatId(actor, "-100"); runCurrent()
        assertNotNull(state.value.snackbar)
        coVerify(exactly = 0) { mutations.setSupervisorGroup(any(), any()) }
    }
    @Test fun malformedPositiveAndOverflowDestinationsAreRejected() = runTest {
        val operations = operations(this)
        for (raw in listOf("0", "100", "not a number", "-" + "9".repeat(30))) {
            operations.setSupervisorGroupChatId(actor, raw); runCurrent()
            assertNull(state.value.savingSupervisorGroupId)
        }
        coVerify(exactly = 0) { mutations.setSupervisorGroup(any(), any()) }
    }
    @Test fun negativeDestinationUsesExactExpectedDoctorAndTrimmedId() = runTest {
        coEvery { mutations.setSupervisorGroup(actor, -100) } returns 1L
        operations(this).setSupervisorGroupChatId(actor, "  -100  "); runCurrent()
        coVerify(exactly = 1) { mutations.setSupervisorGroup(actor, -100) }
        assertNull(state.value.savingSupervisorGroupId)
    }
    @Test fun blankDestinationExplicitlyClearsSupervisorRouting() = runTest {
        coEvery { mutations.setSupervisorGroup(actor, null) } returns 1L
        operations(this).setSupervisorGroupChatId(actor, "  "); runCurrent()
        coVerify(exactly = 1) { mutations.setSupervisorGroup(actor, null) }
    }
    @Test fun staleRoutingFailureIsVisibleAndAllowsRetry() = runTest {
        coEvery { mutations.setSupervisorGroup(actor, -100) } throws IllegalStateException("synthetic stale doctor")
        val operations = operations(this); operations.setSupervisorGroupChatId(actor, "-100"); runCurrent()
        assertEquals("synthetic stale doctor", state.value.snackbar)
        assertNull(state.value.savingSupervisorGroupId)
        coEvery { mutations.setSupervisorGroup(actor, -100) } returns 1L
        operations.setSupervisorGroupChatId(actor, "-100"); runCurrent()
        coVerify(exactly = 2) { mutations.setSupervisorGroup(actor, -100) }
    }
    @Test fun concurrentSupervisorRoutingChangesAreBlocked() = runTest {
        coEvery { mutations.setSupervisorGroup(any(), any()) } coAnswers { awaitCancellation() }
        val operations = operations(backgroundScope)
        operations.setSupervisorGroupChatId(actor, "-100"); runCurrent()
        operations.setSupervisorGroupChatId(actor.copy(id = "other"), "-200"); runCurrent()
        assertEquals(actor.id, state.value.savingSupervisorGroupId)
        coVerify(exactly = 1) { mutations.setSupervisorGroup(any(), any()) }
    }
    @Test fun routingCancellationClearsBusyWithoutSuccessMessage() = runTest {
        coEvery { mutations.setSupervisorGroup(any(), any()) } throws CancellationException("synthetic cancel")
        operations(this).setSupervisorGroupChatId(actor, "-100"); runCurrent()
        assertNull(state.value.savingSupervisorGroupId); assertNull(state.value.snackbar)
    }
}
