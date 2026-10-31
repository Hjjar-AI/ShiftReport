package com.hos.rushdpatients.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.testutil.SyncFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class EmptyStartCoordinatorTest : SyncFixture() {
    private fun coordinator() = EmptyStartCoordinator(preferences, db, settings, shifts, cursors,
        audit, doctorSync, remote, wire.client, wire.topics, dispatchers)
    private var row = Patient(id = "tea", name = "شاي", labs = "نتائج محفوظة", revision = 4)

    @Before fun seedReset() = runBlocking<Unit> {
        doctors.upsert(admin.copy(telegramId = 123))
        admin = requireNotNull(doctors.getById(admin.id)); login(admin)
        wire.state = wire.state.copy(doctorsFileId = wire.registry(DoctorsRegistryCodec.encode(doctors.getAll())))
        patients.upsert(row, "shift-1")
        row = requireNotNull(patients.getById(row.id))
        patients.upsert(row.copy(id = "recycled", deletedAt = Instant.EPOCH), "shift-2")
        settings.put(AppConstants.SETTING_PATIENT_DRAFT, "synthetic-draft")
        settings.put("report_delivery_ledger", "synthetic-receipt")
        settings.put("appearance", "dark")
    }
    private suspend fun assertClinicalIntact() {
        assertEquals(row, patients.getById(row.id))
        assertEquals(2, db.patientDao().getAll().size)
        assertEquals(2, db.shiftDao().getRecent(20).size)
        assertEquals("synthetic-draft", settings.get(AppConstants.SETTING_PATIENT_DRAFT))
        assertTrue(db.auditDao().getRecent(20).isEmpty())
    }

    @Test fun prepareDoesNotUploadOrDeleteAnything() = runBlocking<Unit> {
        coordinator().prepare()
        assertClinicalIntact()
        assertFalse(wire.methods.contains("sendDocument"))
    }
    @Test fun successfulResetClearsOnlyClinicalStateAndPreservesAccountsSettingsAndReceipts() = runBlocking<Unit> {
        val beforeDoctors = doctors.getAll()
        val operation = coordinator()
        operation.confirm(operation.prepare())
        assertTrue(db.patientDao().getAll().isEmpty())
        assertEquals(1, db.shiftDao().getRecent(20).size)
        assertNull(settings.get(AppConstants.SETTING_PATIENT_DRAFT))
        assertEquals(beforeDoctors, doctors.getAll())
        assertEquals("synthetic-receipt", settings.get("report_delivery_ledger"))
        assertEquals("dark", settings.get("appearance"))
        assertEquals(admin.id, sessions.current()?.doctorId)
        assertFalse(PatientPublicationStatus(shifts, patients, doctors, settings, dispatchers).hasPending())
        assertTrue(db.auditDao().getRecent(20).isEmpty())
        assertEquals(100L, wire.state.csvMessageId)
        assertNull(wire.state.previousCsvFileId)
        assertFalse(wire.methods.any { it in listOf("deleteMessage", "sendMessage", "pinChatMessage") })
    }
    @Test fun changedPatientRejectsReviewBeforeUpload() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        patients.upsert(row.copy(labs = "newer", revision = 5), "shift-1")
        rejected { operation.confirm(review) }
        assertEquals("newer", patients.getById(row.id)?.labs)
        assertFalse(wire.methods.contains("sendDocument"))
    }
    @Test fun changedDraftRejectsReviewBeforeUpload() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        settings.put(AppConstants.SETTING_PATIENT_DRAFT, "newer")
        rejected { operation.confirm(review) }
        assertEquals(row, patients.getById(row.id))
        assertFalse(wire.methods.contains("sendDocument"))
    }
    @Test fun changedShiftRejectsReviewBeforeUpload() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        val shift = requireNotNull(shifts.getById("shift-1"))
        shifts.upsert(shift.copy(revision = shift.revision + 1))
        rejected { operation.confirm(review) }
        assertEquals(row, patients.getById(row.id))
        assertFalse(wire.methods.contains("sendDocument"))
    }
    @Test fun liveDemotionRejectsPreviouslyReviewedReset() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        doctors.upsert(admin.copy(rank = 0, isPermanentAdmin = false))
        rejected { operation.confirm(review) }
        assertClinicalIntact()
        assertFalse(wire.methods.contains("sendDocument"))
    }
    @Test fun signedOutActorCannotConfirm() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        sessions.clear()
        rejected { operation.confirm(review) }
        assertClinicalIntact()
    }
    @Test fun changedPublishedRegistryRejectsReview() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        wire.state = wire.state.copy(doctorsMessageId = 999)
        rejected { operation.confirm(review) }
        assertClinicalIntact()
    }
    @Test fun uploadFailureConsumesReviewWithoutDeletingLocalRecords() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        wire.beforeResponse = { if (it == "sendDocument") error("synthetic upload failure") }
        rejected { operation.confirm(review) }
        wire.beforeResponse = {}
        rejected { operation.confirm(review) }
        assertEquals(1, wire.methods.count { it == "sendDocument" })
        assertClinicalIntact()
        assertNoTemporaryPlaintext()
    }
    @Test fun cancellationDuringUploadPropagatesAndKeepsRecords() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        wire.beforeResponse = { if (it == "sendDocument") throw CancellationException("synthetic cancellation") }
        assertTrue(rejected { operation.confirm(review) } is CancellationException)
        assertClinicalIntact()
        assertNoTemporaryPlaintext()
    }
    @Test fun missingUploadReceiptCannotDeleteRecords() = runBlocking<Unit> {
        wire.uploadedDocumentId = null
        val operation = coordinator(); val review = operation.prepare()
        rejected { operation.confirm(review) }
        assertClinicalIntact()
        assertFalse(wire.methods.contains("editMessageText"))
    }
    @Test fun editDuringUploadPreventsPinnedReplacement() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        wire.beforeResponse = { method -> if (method == "sendDocument") runBlocking {
            patients.upsert(row.copy(labs = "racing edit", revision = 5), "shift-1")
        } }
        rejected { operation.confirm(review) }
        assertEquals("racing edit", patients.getById(row.id)?.labs)
        assertFalse(wire.methods.contains("editMessageText"))
    }
    @Test fun authorityRevokedDuringUploadPreventsPinnedReplacement() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        wire.beforeResponse = { if (it == "sendDocument") sessions.clear() }
        rejected { operation.confirm(review) }
        assertClinicalIntact()
        assertFalse(wire.methods.contains("editMessageText"))
    }
    @Test fun pinnedWriteFailureCannotDeleteLocalRecords() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        wire.beforeResponse = { if (it == "editMessageText") error("synthetic pin failure") }
        rejected { operation.confirm(review) }
        assertClinicalIntact()
    }
    @Test fun editDuringPinnedWriteKeepsLocalWorkEvenAfterRemoteReset() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        wire.beforeResponse = { if (it == "editMessageText") runBlocking {
            patients.upsert(row.copy(labs = "racing pin edit", revision = 5), "shift-1")
        } }
        rejected { operation.confirm(review) }
        assertEquals(100L, wire.state.csvMessageId)
        assertEquals("racing pin edit", patients.getById(row.id)?.labs)
        assertEquals(2, db.shiftDao().getRecent(20).size)
    }
    @Test fun baselineWriteFailureRollsBackDeletionSettingsAndSyncCursor() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        val previousSettings = settings.getAll()
        val previousCursor = cursors.getAll()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_reset_baseline BEFORE INSERT ON settings WHEN NEW.key = '${AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX}${review.shift.id}' BEGIN SELECT RAISE(ABORT, 'synthetic baseline failure'); END")
        rejected { operation.confirm(review) }
        assertClinicalIntact()
        assertEquals(previousSettings, settings.getAll())
        assertEquals(previousCursor, cursors.getAll())
        assertEquals(100L, wire.state.csvMessageId) // Remote success is independent of local rollback.
    }
    @Test fun usedReviewCannotRunAgain() = runBlocking<Unit> {
        val operation = coordinator(); val review = operation.prepare()
        operation.confirm(review)
        rejected { operation.confirm(review) }
        assertEquals(1, wire.methods.count { it == "sendDocument" })
        assertTrue(db.auditDao().getRecent(20).isEmpty())
    }
    private fun assertNoTemporaryPlaintext() {
        assertTrue(preferences.cacheDir.listFiles().orEmpty().none { it.name.startsWith("empty_start_") })
    }
}
