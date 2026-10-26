package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.sync.KnownShiftState
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class WardSynchronizationTest {
    @Test fun activeSyncTakesPrecedenceOverAllStoredSnapshotStates() {
        for (snapshot in KnownShiftState.entries)
            assertEquals(PatientSyncStatus.SYNCING, WardSynchronization(selectedSnapshot = snapshot).status(true))
    }
    @Test fun statusUsesSelectedSnapshotRatherThanAnotherShiftsPendingFlag() {
        assertEquals(PatientSyncStatus.LOCAL, WardSynchronization(pending = true).status(false))
        assertEquals(PatientSyncStatus.BACKED_UP, WardSynchronization(pending = true, selectedSnapshot = KnownShiftState.MATCHED).status(false))
        assertEquals(PatientSyncStatus.PENDING, WardSynchronization(selectedSnapshot = KnownShiftState.CHANGED).status(false))
    }
    @Test fun unknownDeliveryOrUnavailableLedgerAlwaysOffersHistoryFirst() {
        for (snapshot in KnownShiftState.entries) for (readOnly in listOf(true, false)) {
            assertEquals(WardWorkflowAction.DELIVERY_HISTORY, WardSynchronization(selectedSnapshot = snapshot,
                unresolvedDeliveries = 1).nextAction(readOnly))
            assertEquals(WardWorkflowAction.DELIVERY_HISTORY, WardSynchronization(selectedSnapshot = snapshot,
                deliveryLogUnavailable = true).nextAction(readOnly))
        }
    }
    @Test fun readOnlyShiftNeverOffersPublicationEvenWithLocalChanges() {
        for (snapshot in KnownShiftState.entries)
            assertEquals(WardWorkflowAction.DOWNLOAD, WardSynchronization(pending = true,
                selectedSnapshot = snapshot).nextAction(true))
    }
    @Test fun writableChangedOrUnknownPendingShiftOffersReviewBeforePublication() {
        assertEquals(WardWorkflowAction.REVIEW_PUBLICATION, WardSynchronization(selectedSnapshot = KnownShiftState.CHANGED).nextAction(false))
        assertEquals(WardWorkflowAction.REVIEW_PUBLICATION, WardSynchronization(pending = true).nextAction(false))
        assertEquals(WardWorkflowAction.DOWNLOAD, WardSynchronization(selectedSnapshot = KnownShiftState.MATCHED,
            pending = true).nextAction(false))
    }
    @Test fun downloadedFreshnessDependsOnMatchingMessageReference() {
        val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } answers { "[${firstArg<Int>()}]" } }
        val sync = WardSynchronization(selectedSnapshot = KnownShiftState.MATCHED, downloadedAt = 1, downloadedMessage = 7)
        val current = sync.description(text, false, 7)
        val older = sync.description(text, false, 8)
        assertNotEquals(current, older)
        assertEquals(older, sync.description(text, false, null))
    }
    @Test fun unknownSnapshotDescriptionRetainsUncertaintyEvenWithRecordedDownloadTime() {
        val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } answers { "[${firstArg<Int>()}]" } }
        val unknown = WardSynchronization(downloadedAt = 1, downloadedMessage = 7)
        assertNotEquals(unknown.description(text, false, 7),
            unknown.copy(selectedSnapshot = KnownShiftState.MATCHED).description(text, false, 7))
    }
}
