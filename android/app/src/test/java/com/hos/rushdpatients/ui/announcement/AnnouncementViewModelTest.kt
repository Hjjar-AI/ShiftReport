package com.hos.rushdpatients.ui.announcement

import com.hos.rushdpatients.domain.announcement.AnnouncementPublisher
import com.hos.rushdpatients.domain.announcement.AnnouncementReview
import com.hos.rushdpatients.domain.report.ReportDeliveryLedger
import com.hos.rushdpatients.domain.report.ReportDeliveryRecord
import com.hos.rushdpatients.sync.SyncState
import com.hos.rushdpatients.testutil.ViewModelFixture
import io.mockk.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AnnouncementViewModelTest : ViewModelFixture() {
    private val publisher = mockk<AnnouncementPublisher>()
    private val ledger = mockk<ReportDeliveryLedger>()
    private val records = MutableStateFlow<List<ReportDeliveryRecord>>(emptyList())
    private val review = AnnouncementReview("إعلان تجريبي", null, SyncState(), "actor", "synthetic digest")
    private fun row(id: String = "one", scope: String = AnnouncementPublisher.SCOPE,
        format: String = AnnouncementPublisher.SCOPE, status: String = "UNKNOWN") =
        ReportDeliveryRecord(id, scope, "route", "key", "إعلان", format, "actor", 1, status)
    private fun vm() = keep(AnnouncementViewModel(text, publisher, ledger))
    private fun reviewedVm() = vm().apply { onTemplateChange(review.text); review() }
    @Before fun setupPublisher() {
        every { ledger.observe() } returns records
        coEvery { publisher.currentMessageId() } returns null
        coEvery { publisher.prepare(review.text) } returns review
        coEvery { publisher.publish(review) } returns 10L
    }
    @Test fun initialRefreshLoadsExistingMessageWithoutPreparingOrSending() {
        coEvery { publisher.currentMessageId() } returns 7L
        val vm = vm()
        assertEquals(7L, vm.state.value.lastMessageId)
        assertFalse(vm.state.value.loading)
        coVerify(exactly = 0) { publisher.prepare(any()) }
        coVerify(exactly = 0) { publisher.publish(any()) }
    }
    @Test fun ledgerShowsOnlyUnknownAnnouncements() {
        records.value = listOf(row(), row("report", scope = "shift"), row("pdf", format = "PDF"), row("sent", status = "SENT"))
        val vm = vm()
        assertEquals(listOf(row()), vm.state.value.deliveryRecords)
        records.value = emptyList()
        assertTrue(vm.state.value.deliveryRecords.isEmpty())
    }
    @Test fun ledgerFailureBlocksReview() {
        every { ledger.observe() } returns flow { throw IllegalStateException("synthetic corrupt ledger") }
        val vm = vm(); vm.onTemplateChange(review.text); vm.review()
        assertTrue(vm.state.value.ledgerUnavailable)
        assertNotNull(vm.state.value.snackbar)
        coVerify(exactly = 0) { publisher.prepare(any()) }
    }
    @Test fun reviewExposesExactTextAndExistingMessageWithoutPublishing() {
        coEvery { publisher.prepare(review.text) } returns review.copy(existingMessageId = 9)
        val vm = reviewedVm()
        assertEquals(review.text, vm.state.value.reviewText)
        assertTrue(vm.state.value.reviewExisting)
        assertEquals(9L, vm.state.value.lastMessageId)
        assertFalse(vm.state.value.reviewing)
        coVerify(exactly = 0) { publisher.publish(any()) }
    }
    @Test fun confirmationWithoutReviewCannotPublish() {
        val vm = vm(); vm.onTemplateChange(review.text); vm.confirmSend()
        coVerify(exactly = 0) { publisher.publish(any()) }
    }
    @Test fun changingTemplateInvalidatesReviewedAnnouncement() {
        val vm = reviewedVm(); vm.onTemplateChange("نص محدث"); vm.confirmSend()
        assertNull(vm.state.value.reviewText)
        coVerify(exactly = 0) { publisher.publish(any()) }
    }
    @Test fun dismissingReviewPreventsPublication() {
        val vm = reviewedVm(); vm.dismissReview(); vm.confirmSend()
        assertNull(vm.state.value.reviewText)
        coVerify(exactly = 0) { publisher.publish(any()) }
    }
    @Test fun confirmedReviewIsConsumedExactlyOnce() {
        val vm = reviewedVm(); vm.confirmSend(); vm.confirmSend()
        assertEquals(10L, vm.state.value.lastMessageId)
        assertFalse(vm.state.value.sending)
        assertNull(vm.state.value.reviewText)
        coVerify(exactly = 1) { publisher.publish(review) }
    }
    @Test fun failedSendCannotAutomaticallyReuseReview() {
        coEvery { publisher.publish(review) } throws IllegalStateException("synthetic uncertain send")
        val vm = reviewedVm(); vm.confirmSend(); vm.confirmSend()
        assertFalse(vm.state.value.sending)
        assertNull(vm.state.value.reviewText)
        assertNull(vm.state.value.lastMessageId)
        assertTrue(vm.state.value.snackbar!!.contains("synthetic uncertain send"))
        coVerify(exactly = 1) { publisher.publish(review) }
    }
    @Test fun duplicateSendAndEditsAreIgnoredDuringPublication() {
        coEvery { publisher.publish(review) } coAnswers { awaitCancellation() }
        val vm = reviewedVm(); vm.confirmSend(); vm.confirmSend(); vm.onTemplateChange("changed")
        vm.dismissReview(); vm.review(); vm.refresh(); vm.resolve(row(), null)
        assertTrue(vm.state.value.sending)
        assertEquals(review.text, vm.state.value.template)
        coVerify(exactly = 1) { publisher.publish(review) }
        coVerify(exactly = 1) { publisher.prepare(any()) }
        coVerify(exactly = 1) { publisher.currentMessageId() }
        coVerify(exactly = 0) { publisher.resolve(any(), any()) }
    }
    @Test fun duplicateReviewAndTemplateEditAreIgnoredWhilePreparing() {
        coEvery { publisher.prepare(review.text) } coAnswers { awaitCancellation() }
        val vm = reviewedVm(); vm.review(); vm.onTemplateChange("changed"); vm.confirmSend()
        assertTrue(vm.state.value.reviewing)
        assertEquals(review.text, vm.state.value.template)
        coVerify(exactly = 1) { publisher.prepare(review.text) }
        coVerify(exactly = 0) { publisher.publish(any()) }
    }
    @Test fun reviewFailureReturnsToEditableState() {
        coEvery { publisher.prepare(review.text) } throws IllegalArgumentException("synthetic rejected review")
        val vm = reviewedVm()
        assertFalse(vm.state.value.reviewing)
        assertNull(vm.state.value.reviewText)
        assertEquals("synthetic rejected review", vm.state.value.snackbar)
        vm.onTemplateChange("new draft")
        assertEquals("new draft", vm.state.value.template)
    }
    @Test fun refreshFailureKeepsLastKnownMessageAndAllowsRetry() {
        coEvery { publisher.currentMessageId() } returns 7L
        val vm = vm()
        coEvery { publisher.currentMessageId() } throws IllegalStateException("synthetic offline")
        vm.refresh()
        assertEquals(7L, vm.state.value.lastMessageId)
        assertFalse(vm.state.value.loading)
        assertEquals("synthetic offline", vm.state.value.snackbar)
        coEvery { publisher.currentMessageId() } returns 8L
        vm.refresh()
        assertEquals(8L, vm.state.value.lastMessageId)
    }
    @Test fun reviewIsBlockedWhileInitialRefreshIsPending() {
        coEvery { publisher.currentMessageId() } coAnswers { awaitCancellation() }
        val vm = vm(); vm.onTemplateChange(review.text); vm.refresh(); vm.review()
        assertTrue(vm.state.value.loading)
        coVerify(exactly = 1) { publisher.currentMessageId() }
        coVerify(exactly = 0) { publisher.prepare(any()) }
    }
    @Test fun resolutionUsesExactRecordAndRefreshesIndexAfterSuccess() {
        coEvery { publisher.resolve(row(), 42L) } returns Unit
        val vm = vm(); vm.resolve(row(), 42)
        assertFalse(vm.state.value.resolving)
        assertNotNull(vm.state.value.snackbar)
        coVerify { publisher.resolve(row(), 42L) }
        coVerify(exactly = 2) { publisher.currentMessageId() }
    }
    @Test fun resolutionFailureDoesNotRefreshOrClaimSuccess() {
        coEvery { publisher.resolve(row(), null) } throws IllegalStateException("synthetic lost authority")
        val vm = vm(); vm.resolve(row(), null)
        assertFalse(vm.state.value.resolving)
        assertEquals("synthetic lost authority", vm.state.value.snackbar)
        coVerify(exactly = 1) { publisher.currentMessageId() }
    }
    @Test fun duplicateResolutionAndReviewAreBlockedDuringResolution() {
        coEvery { publisher.resolve(row(), null) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.resolve(row(), null); vm.resolve(row(), null); vm.review()
        assertTrue(vm.state.value.resolving)
        coVerify(exactly = 1) { publisher.resolve(row(), null) }
        coVerify(exactly = 0) { publisher.prepare(any()) }
    }
    @Test fun cancellationOfSendDoesNotProduceSuccessOrRetry() {
        coEvery { publisher.publish(review) } coAnswers { awaitCancellation() }
        val vm = reviewedVm(); vm.confirmSend(); clearViewModels()
        assertFalse(vm.state.value.sending)
        assertNull(vm.state.value.snackbar)
        assertNull(vm.state.value.lastMessageId)
        assertNull(vm.state.value.reviewText)
    }
    @Test fun cancellationOfReviewClearsProgressWithoutError() {
        coEvery { publisher.prepare(review.text) } coAnswers { awaitCancellation() }
        val vm = reviewedVm(); clearViewModels()
        assertFalse(vm.state.value.reviewing)
        assertNull(vm.state.value.snackbar)
        assertNull(vm.state.value.reviewText)
    }
    @Test fun cancellationOfRefreshClearsProgressWithoutError() {
        coEvery { publisher.currentMessageId() } coAnswers { awaitCancellation() }
        val vm = vm(); clearViewModels()
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.snackbar)
    }
    @Test fun cancellationOfResolutionClearsProgressWithoutSuccess() {
        coEvery { publisher.resolve(row(), null) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.resolve(row(), null); clearViewModels()
        assertFalse(vm.state.value.resolving)
        assertNull(vm.state.value.snackbar)
    }
    @Test fun snackbarDismissalPreservesDraftAndIndex() {
        coEvery { publisher.currentMessageId() } throws IllegalStateException("synthetic offline")
        val vm = vm(); vm.onTemplateChange("draft"); vm.dismissSnackbar()
        assertNull(vm.state.value.snackbar)
        assertEquals("draft", vm.state.value.template)
    }
}
