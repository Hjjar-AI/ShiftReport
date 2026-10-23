package com.hos.rushdpatients.ui.connection

import android.content.ContentResolver
import android.database.Cursor
import android.content.Context
import android.net.Uri
import com.hos.rushdpatients.config.ConnectionChange
import com.hos.rushdpatients.config.ProjectConfig
import com.hos.rushdpatients.config.ProjectConnectionManager
import com.hos.rushdpatients.testutil.ViewModelFixture
import com.hos.rushdpatients.util.DispatcherProvider
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ProjectConnectionViewModelTest : ViewModelFixture() {
    private val connections = mockk<ProjectConnectionManager>()
    private val dispatchers = mockk<DispatcherProvider> { every { io } returns Dispatchers.Unconfined }
    private val uri = mockk<Uri>()
    private val change = ConnectionChange(ProjectConfig(hospitalName = "مشروع تجريبي"), "synthetic-replacement")
    private val context = mockk<Context>()
    private fun vm() = keep(ProjectConnectionViewModel(text, connections, context, dispatchers))
    private fun prepare() { coEvery { connections.prepareToken(any()) } returns change }

    @Test fun initialStateContainsNoPendingReview() {
        assertEquals(ProjectConnectionUiState(), vm().state.value)
    }
    @Test fun prepareShowsProjectWithoutApplyingCredentials() {
        prepare()
        val vm = vm(); vm.replaceToken("synthetic")
        assertEquals(change.expected.hospitalName, vm.state.value.reviewProject)
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.applied)
        coVerify(exactly = 0) { connections.apply(any()) }
    }
    @Test fun protectedFileIsPreparedBeforeAnyApply() {
        coEvery { connections.prepareFile(uri, any()) } returns change
        val vm = vm(); vm.importFile(uri, "synthetic password")
        coVerify { connections.prepareFile(uri, match { it.concatToString() == "synthetic password" }) }
        assertEquals(change.expected.hospitalName, vm.state.value.reviewProject)
        coVerify(exactly = 0) { connections.apply(any()) }
    }
    @Test fun confirmationWithoutReviewDoesNothing() {
        vm().confirm()
        coVerify(exactly = 0) { connections.apply(any()) }
    }
    @Test fun confirmationAppliesTheExactReviewedChangeOnce() {
        prepare(); coEvery { connections.apply(change) } returns Unit
        val vm = vm(); vm.replaceToken("synthetic"); vm.confirm(); vm.confirm()
        assertTrue(vm.state.value.applied)
        assertNull(vm.state.value.reviewProject)
        assertNotNull(vm.state.value.message)
        coVerify(exactly = 1) { connections.apply(change) }
    }
    @Test fun resetDiscardsReviewedCredentials() {
        prepare()
        val vm = vm(); vm.replaceToken("synthetic"); vm.reset(); vm.confirm()
        assertEquals(ProjectConnectionUiState(), vm.state.value)
        coVerify(exactly = 0) { connections.apply(any()) }
    }
    @Test fun preparingAnotherChangeInvalidatesTheOldReviewEvenOnFailure() {
        prepare()
        val vm = vm(); vm.replaceToken("first")
        coEvery { connections.prepareToken("second") } throws IllegalStateException("changed project")
        vm.replaceToken("second"); vm.confirm()
        assertNull(vm.state.value.reviewProject)
        assertEquals("changed project", vm.state.value.message)
        coVerify(exactly = 0) { connections.apply(any()) }
    }
    @Test fun preparationFailureExposesValidationReason() {
        coEvery { connections.prepareToken(any()) } throws IllegalArgumentException("different bot")
        val vm = vm(); vm.replaceToken("synthetic")
        assertEquals("different bot", vm.state.value.message)
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.applied)
    }
    @Test fun transportFailureDoesNotExposeExceptionCredentials() {
        coEvery { connections.prepareToken(any()) } throws IOException("sensitive transport detail")
        val vm = vm(); vm.replaceToken("synthetic")
        assertEquals("رسالة تجريبية", vm.state.value.message)
        assertFalse(vm.state.value.toString().contains("sensitive transport detail"))
    }
    @Test fun applyFailureDiscardsStaleReviewAndRequiresPreparationAgain() {
        prepare(); coEvery { connections.apply(change) } throws IllegalStateException("stale review")
        val vm = vm(); vm.replaceToken("synthetic"); vm.confirm(); vm.confirm()
        assertNull(vm.state.value.reviewProject)
        assertFalse(vm.state.value.applied)
        assertFalse(vm.state.value.busy)
        assertEquals("stale review", vm.state.value.message)
        coVerify(exactly = 1) { connections.apply(change) }
    }
    @Test fun duplicatePrepareAndResetAreIgnoredWhilePreparing() {
        coEvery { connections.prepareToken(any()) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.replaceToken("first"); vm.replaceToken("second"); vm.reset(); vm.confirm()
        assertTrue(vm.state.value.busy)
        coVerify(exactly = 1) { connections.prepareToken(any()) }
        coVerify(exactly = 0) { connections.apply(any()) }
    }
    @Test fun duplicateConfirmationAndResetAreIgnoredWhileApplying() {
        prepare(); coEvery { connections.apply(change) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.replaceToken("first"); vm.confirm(); vm.confirm(); vm.reset(); vm.replaceToken("second")
        assertTrue(vm.state.value.busy)
        coVerify(exactly = 1) { connections.apply(change) }
        coVerify(exactly = 1) { connections.prepareToken(any()) }
    }
    @Test fun cancellationDuringPreparationDoesNotBecomeAnErrorOrApply() {
        coEvery { connections.prepareToken(any()) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.replaceToken("first"); clearViewModels()
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.message)
        assertFalse(vm.state.value.applied)
        coVerify(exactly = 0) { connections.apply(any()) }
    }
    @Test fun cancellationDuringApplyDoesNotClaimSuccess() {
        prepare(); coEvery { connections.apply(change) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.replaceToken("first"); vm.confirm(); clearViewModels()
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.applied)
        assertNull(vm.state.value.message)
    }
    private fun metadata(cursor: Cursor?): ContentResolver {
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        every { resolver.query(uri, any(), null, null, null) } returns cursor
        return resolver
    }
    @Test fun providerDisplayNameIsReadWithoutOpeningSecretFile() = runBlocking<Unit> {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.getColumnIndex(any()) } returns 0
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "synthetic.srjoin.json"
        val resolver = metadata(cursor)
        assertEquals("synthetic.srjoin.json", vm().readDisplayName(uri))
        verify(exactly = 1) { cursor.close() }
        verify(exactly = 0) { resolver.openInputStream(any()) }
    }
    @Test fun absentMetadataReturnsNoUriPathFallback() = runBlocking<Unit> {
        metadata(null)
        assertNull(vm().readDisplayName(uri))
    }
    @Test fun missingDisplayNameColumnStillClosesCursor() = runBlocking<Unit> {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.getColumnIndex(any()) } returns -1
        metadata(cursor)
        assertNull(vm().readDisplayName(uri))
        verify(exactly = 1) { cursor.close() }
        verify(exactly = 0) { cursor.getString(any()) }
    }
    @Test fun deniedMetadataReturnsNoSensitiveProviderError() = runBlocking<Unit> {
        val resolver = metadata(null)
        every { resolver.query(uri, any(), null, null, null) } throws SecurityException("sensitive provider details")
        val vm = vm()
        assertNull(vm.readDisplayName(uri))
        assertNull(vm.state.value.message)
    }
    @Test fun metadataCancellationPropagates() = runBlocking<Unit> {
        val resolver = metadata(null)
        every { resolver.query(uri, any(), null, null, null) } throws CancellationException("synthetic cancel")
        try { vm().readDisplayName(uri); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
    }
}
