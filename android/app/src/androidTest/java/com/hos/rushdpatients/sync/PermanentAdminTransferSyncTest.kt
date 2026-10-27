package com.hos.rushdpatients.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.testutil.SyncFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PermanentAdminTransferSyncTest : SyncFixture() {
    private lateinit var recipient: Doctor
    private suspend fun seed() {
        doctors.upsert(admin.copy(telegramId = 111, extraOptions = setOf("pin:synthetic-owner-hash")))
        admin = requireNotNull(doctors.getById(admin.id)); login(admin)
        doctors.upsert(doctor("recipient", "بلوط", 20).copy(telegramId = 222,
            extraOptions = setOf("pin:synthetic-recipient-hash")))
        recipient = requireNotNull(doctors.getById("recipient"))
        settings.put(AppConstants.SETTING_DOCTORS_BASE_REGISTRY, DoctorsRegistryCodec.encode(doctors.getAll()))
        settings.putBoolean(AppConstants.SETTING_DOCTORS_SYNC_PENDING, false)
        wire.state = wire.state.copy(doctorsData = DoctorsRegistryCodec.encode(doctors.getAll()), doctorsMessageId = 2)
        wire.methods.clear()
    }
    private suspend fun review(): PermanentAdminTransferReview {
        val review = doctorSync.preparePermanentAdminTransfer(recipient.id).getOrThrow()
        wire.files[requireNotNull(wire.uploadedDocumentId)] = review.after
        return review
    }
    private suspend fun assertUnchangedOwner() {
        assertTrue(requireNotNull(doctors.getById(admin.id)).isPermanentAdmin)
        assertFalse(requireNotNull(doctors.getById(recipient.id)).isPermanentAdmin)
    }
    @Test fun confirmedHandoverPublishesReviewedBytesAndPreservesPinsPatientsAndDrafts() = runBlocking<Unit> {
        seed()
        patients.upsert(Patient(id = "tea", name = "شاي", responsibleResidentId = admin.id,
            tasks = listOf(PatientTask("task", "متابعة تجريبية"))), "shift-1")
        settings.put(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + "tea", "synthetic retained draft")
        val clinical = patients.getForShift("shift-1")
        val reviewed = review()
        assertTrue(wire.uploadedDocuments.isEmpty()); assertUnchangedOwner()
        doctorSync.publishPermanentAdminTransfer(reviewed).getOrThrow()
        assertEquals(listOf(reviewed.after), wire.uploadedDocuments.map { it.toString(Charsets.UTF_8) })
        val outgoing = requireNotNull(doctors.getById(admin.id))
        val incoming = requireNotNull(doctors.getById(recipient.id))
        assertTrue(outgoing.isAdmin); assertFalse(outgoing.isPermanentAdmin); assertEquals(admin.rank, outgoing.rank)
        assertTrue(incoming.isPermanentAdmin); assertEquals(recipient.rank, incoming.rank)
        assertEquals(admin.extraOptions, outgoing.extraOptions); assertEquals(recipient.extraOptions, incoming.extraOptions)
        assertEquals(clinical, patients.getForShift("shift-1"))
        assertEquals("synthetic retained draft", settings.get(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + "tea"))
        assertFalse(doctors.hasPendingSharedChanges())
        assertEquals(reviewed.after, settings.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY))
    }
    @Test fun ordinaryPublishedAdminCannotPrepareTransfer() = runBlocking<Unit> {
        seed(); doctors.upsert(admin.copy(isPermanentAdmin = false)); login(requireNotNull(doctors.getById(admin.id)))
        assertTrue(doctorSync.preparePermanentAdminTransfer(recipient.id).isFailure)
        assertTrue(wire.uploadedDocuments.isEmpty())
    }
    @Test fun pendingLocalChangesCannotBeBundledIntoHandover() = runBlocking<Unit> {
        seed(); doctors.upsert(recipient.copy(customTitle = "تعديل محلي"))
        val before = doctors.getAllIncludingDeleted()
        assertTrue(doctorSync.preparePermanentAdminTransfer(recipient.id).isFailure)
        assertEquals(before, doctors.getAllIncludingDeleted()); assertTrue(wire.uploadedDocuments.isEmpty())
    }
    @Test fun changedProjectOrRemotePointerInvalidatesReviewBeforeUpload() = runBlocking<Unit> {
        seed(); val reviewed = review()
        wire.state = wire.state.copy(doctorsMessageId = 3)
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        wire.state = reviewed.source
        wire.projects.saveDraft(wire.projects.current().copy(hospitalName = "مشروع تجريبي آخر"))
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        assertTrue(wire.uploadedDocuments.isEmpty()); assertUnchangedOwner()
    }
    @Test fun changedSessionOrRecipientIdentityInvalidatesReviewBeforeUpload() = runBlocking<Unit> {
        seed(); val reviewed = review()
        sessions.clear()
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        login(admin); doctors.upsert(recipient.copy(telegramId = 333))
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        assertTrue(wire.uploadedDocuments.isEmpty()); assertUnchangedOwner()
    }
    @Test fun revocationDuringUploadCannotUpdatePinnedIndex() = runBlocking<Unit> {
        seed(); val reviewed = review()
        wire.beforeResponse = { method -> if (method == "sendDocument") runBlocking {
            doctors.upsert(admin.copy(rank = 0, isPermanentAdmin = false))
        } }
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        assertFalse("editMessageText" in wire.methods)
        assertFalse(requireNotNull(doctors.getById(recipient.id)).isPermanentAdmin)
    }
    @Test fun uploadFailureLeavesRolesAndPendingStateRecoverable() = runBlocking<Unit> {
        seed(); val reviewed = review()
        wire.beforeResponse = { if (it == "sendDocument") throw IOException("synthetic upload rejection") }
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        assertUnchangedOwner(); assertFalse(doctors.hasPendingSharedChanges())
        assertFalse("editMessageText" in wire.methods)
    }
    @Test fun failedIndexWriteLeavesRolesUnchangedAndAllowsFreshReview() = runBlocking<Unit> {
        seed(); val reviewed = review()
        wire.beforeResponse = { if (it == "editMessageText") throw IOException("synthetic index rejection") }
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        assertUnchangedOwner(); assertFalse(doctors.hasPendingSharedChanges())
        wire.beforeResponse = { }
        assertTrue(doctorSync.preparePermanentAdminTransfer(recipient.id).isSuccess)
    }
    @Test fun lostConfirmationRecoversByFetchAndCannotPublishSameTransferAgain() = runBlocking<Unit> {
        seed(); val reviewed = review()
        wire.beforeResponse = { method ->
            if (method == "getChat" && wire.state.doctorsFileId == wire.uploadedDocumentId)
                throw IOException("synthetic lost confirmation")
        }
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        assertEquals(wire.uploadedDocumentId, wire.state.doctorsFileId)
        assertUnchangedOwner(); assertFalse(doctors.hasPendingSharedChanges())
        wire.beforeResponse = { }
        assertTrue(doctorSync.preparePermanentAdminTransfer(recipient.id).isFailure)
        doctorSync.fetchAndApplyLatestDoctors().getOrThrow()
        assertFalse(requireNotNull(doctors.getById(admin.id)).isPermanentAdmin)
        assertTrue(requireNotNull(doctors.getById(recipient.id)).isPermanentAdmin)
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        assertEquals(1, wire.uploadedDocuments.size)
    }
    @Test fun cancellationBeforeUploadPropagatesWithoutRoleOrPendingChanges() = runBlocking<Unit> {
        seed(); val reviewed = review()
        wire.beforeResponse = { throw CancellationException("synthetic cancellation") }
        try {
            doctorSync.publishPermanentAdminTransfer(reviewed)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertUnchangedOwner(); assertFalse(doctors.hasPendingSharedChanges())
        assertTrue(wire.uploadedDocuments.isEmpty())
    }
    @Test fun fetchAcceptsSuccessorAfterMissingFormerOwnerRemoval() = runBlocking<Unit> {
        seed()
        wire.state = wire.state.copy(doctorsData = DoctorsRegistryCodec.encode(listOf(recipient.copy(isPermanentAdmin = true))),
            doctorsMessageId = 3)
        doctorSync.fetchAndApplyLatestDoctors().getOrThrow()
        assertTrue(requireNotNull(doctors.getById(admin.id)).isDeleted)
        assertTrue(requireNotNull(doctors.getById(recipient.id)).isPermanentAdmin)
    }
    @Test fun ordinaryPublicationCannotBypassDedicatedTransferReview() = runBlocking<Unit> {
        seed(); val reviewed = review()
        assertTrue(doctorSync.uploadReviewedDoctors(reviewed.after).isFailure)
        assertTrue(wire.uploadedDocuments.isEmpty()); assertUnchangedOwner()
    }
    @Test fun mismatchedRemoteReadbackDoesNotApplyLocalRoles() = runBlocking<Unit> {
        seed(); val reviewed = review()
        val originalBaseline = settings.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY)
        wire.files[requireNotNull(wire.uploadedDocumentId)] = reviewed.before
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        assertUnchangedOwner(); assertFalse(doctors.hasPendingSharedChanges())
        assertEquals(originalBaseline, settings.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY))
    }
    @Test fun localBookkeepingFailureRollsBackBothRolesAndFetchRecoversPublishedTransfer() = runBlocking<Unit> {
        seed(); val reviewed = review()
        val originalBaseline = settings.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_transfer_base BEFORE UPDATE ON settings
            WHEN NEW.`key` = '${AppConstants.SETTING_DOCTORS_BASE_REGISTRY}'
            BEGIN SELECT RAISE(ABORT, 'synthetic transfer bookkeeping failure'); END
        """.trimIndent())
        assertTrue(doctorSync.publishPermanentAdminTransfer(reviewed).isFailure)
        assertEquals(wire.uploadedDocumentId, wire.state.doctorsFileId)
        assertUnchangedOwner()
        assertEquals(originalBaseline, settings.get(AppConstants.SETTING_DOCTORS_BASE_REGISTRY))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_transfer_base")
        doctorSync.fetchAndApplyLatestDoctors().getOrThrow()
        assertFalse(requireNotNull(doctors.getById(admin.id)).isPermanentAdmin)
        assertTrue(requireNotNull(doctors.getById(recipient.id)).isPermanentAdmin)
        assertFalse(doctors.hasPendingSharedChanges())
        assertEquals(1, wire.uploadedDocuments.size)
    }

}
