package com.hos.rushdpatients.sync

import com.hos.rushdpatients.domain.report.DeliveryStatus
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.data.model.SyncChannel
import com.hos.rushdpatients.domain.report.ReportDeliveryLedger
import com.hos.rushdpatients.network.ReportSender
import com.hos.rushdpatients.testutil.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class IndependentClientsWorkflowTest {
    private val server = SharedTelegramServer()
    private lateinit var first: IndependentClient
    private lateinit var second: IndependentClient
    @Before fun openClients() = runBlocking<Unit> {
        first = IndependentClient("first", server); second = IndependentClient("second", server)
        first.initialize(); second.initialize()
        server.state = SyncState(doctorsData = DoctorsRegistryCodec.encode(listOf(first.actor)))
    }
    @After fun closeClients() {
        if (::second.isInitialized) second.close()
        if (::first.isInitialized) first.close()
    }
    private suspend fun publishAndRetrieve() {
        first.seedPatient(); first.publish(); second.sync.fetchLatestCsv().getOrThrow()
    }
    private suspend fun edit(client: IndependentClient, labs: String) {
        val patient = requireNotNull(client.patients.getById("tea"))
        client.wardMutations.updatePatient(patient.copy(labs = labs), "today")
    }
    @Test fun secondClientRetrievesActualUploadedBytesIntoIndependentDatabase() = runBlocking<Unit> {
        publishAndRetrieve()
        assertNotSame(first.db, second.db); assertNotSame(first.sessions, second.sessions)
        assertEquals("شاي", second.patients.getById("tea")?.name)
        assertEquals(server.state.csvMessageId, second.cursors.lastMessageId(SyncChannel.CSV))
        edit(second, "نتيجة محلية ثانية")
        assertEquals("", first.patients.getById("tea")?.labs)
        assertTrue(PatientPublicationStatus(second.shifts, second.patients, second.doctors, second.settings, second.dispatchers).hasPending())
    }
    @Test fun secondPublicationThenFirstRetrievalPreservesExactEditedClinicalContent() = runBlocking<Unit> {
        publishAndRetrieve(); edit(second, "نتيجة الجهاز الثاني"); second.publish()
        first.sync.fetchLatestCsv().getOrThrow()
        assertEquals("نتيجة الجهاز الثاني", first.patients.getById("tea")?.labs)
        assertEquals(second.patients.getById("tea")?.labs, first.patients.getById("tea")?.labs)
    }
    @Test fun pendingEditsBlockPeerDownloadWithoutOverwritingThem() = runBlocking<Unit> {
        publishAndRetrieve(); edit(first, "نتيجة أولى"); first.publish()
        edit(second, "تعديل غير منشور")
        val cursor = second.cursors.lastMessageId(SyncChannel.CSV)
        assertTrue(second.sync.fetchLatestCsv().isFailure)
        assertEquals("تعديل غير منشور", second.patients.getById("tea")?.labs)
        assertEquals(cursor, second.cursors.lastMessageId(SyncChannel.CSV))
    }
    @Test fun explicitLocalPublicationRetainsDocumentedLastSuccessfulWriterScope() = runBlocking<Unit> {
        publishAndRetrieve(); edit(first, "نشر أول"); edit(second, "نشر ثان")
        first.publish(); val previous = server.state.csvMessageId
        second.publish()
        assertNotEquals(previous, server.state.csvMessageId)
        first.sync.fetchLatestCsv().getOrThrow()
        assertEquals("نشر ثان", first.patients.getById("tea")?.labs)
    }
    @Test fun reviewedSnapshotRemainsLastSuccessfulWriteWhenPeerPublishesAfterReview() = runBlocking<Unit> {
        publishAndRetrieve(); edit(second, "تعديل ثان")
        val review = second.sync.prepareCsvPublication("today")
        edit(first, "نشر أحدث"); first.publish()
        val uploads = server.calls.count { it.second == "sendDocument" }
        assertTrue(second.sync.uploadReviewedCsv(review).isSuccess)
        assertEquals(uploads + 1, server.calls.count { it.second == "sendDocument" })
        first.sync.fetchLatestCsv().getOrThrow()
        assertEquals("تعديل ثان", first.patients.getById("tea")?.labs)
        assertEquals("تعديل ثان", second.patients.getById("tea")?.labs)
    }
    @Test fun sameProjectReconnectOnOneClientPreservesBothClinicalWorkspaces() = runBlocking<Unit> {
        publishAndRetrieve(); edit(second, "غير منشور")
        val before = second.patients.getById("tea"); val config = second.projects.current()
        val manager = ProjectConnectionManager(second.projects, ProjectProvisioningManager(second.context, second.dispatchers),
            second.telegram, second.authorizer, second.dispatchers)
        val replacement = "12345:" + "b".repeat(24)
        manager.apply(manager.prepareToken(replacement))
        assertEquals(config.copy(botToken = replacement), second.projects.current())
        assertEquals(before, second.patients.getById("tea"))
        assertTrue(PatientPublicationStatus(second.shifts, second.patients, second.doctors, second.settings, second.dispatchers).hasPending())
        assertNotEquals(replacement, first.projects.current().botToken)
    }
    @Test fun protectedFileForAnotherProjectCannotReplaceClientScope() = runBlocking<Unit> {
        publishAndRetrieve()
        val provisioning = ProjectProvisioningManager(second.context, second.dispatchers)
        val output = File(second.context.cacheDir, "other.srjoin.json")
        val encrypted = provisioning.prepareExport(second.projects.current().copy(chatId = -200), "synthetic password".toCharArray())
        provisioning.writeExport(Uri.fromFile(output), encrypted)
        val manager = ProjectConnectionManager(second.projects, provisioning, second.telegram, second.authorizer, second.dispatchers)
        val before = second.projects.current()
        try { manager.prepareFile(Uri.fromFile(output), "synthetic password".toCharArray()); fail("Cross-project reconnect accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, second.projects.current()); assertEquals("شاي", second.patients.getById("tea")?.name)
    }
    @Test fun uploadResponseLossLeavesUploadedFileUnreferencedAndLocalEditsPending() = runBlocking<Unit> {
        first.seedPatient()
        server.fault = { who, method, phase -> if (who == "first" && method == "sendDocument" && phase == "after")
            throw IOException("synthetic lost upload response") }
        val result = first.sync.uploadReviewedCsv(first.sync.prepareCsvPublication("today"))
        assertTrue(result.isFailure); assertTrue(server.files.isNotEmpty())
        assertNull(server.state.csvFileId); assertNull(first.cursors.lastMessageId(SyncChannel.CSV))
        assertTrue(PatientPublicationStatus(first.shifts, first.patients, first.doctors, first.settings, first.dispatchers).hasPending())
        assertEquals("شاي", first.patients.getById("tea")?.name)
    }
    @Test fun downloadResponseFailurePreservesRowsCursorAndUndoState() = runBlocking<Unit> {
        first.seedPatient(); first.publish()
        val before = second.settings.getAll()
        server.fault = { who, method, phase -> if (who == "second" && method.endsWith(".bin") && phase == "before")
            throw IOException("synthetic interrupted download") }
        assertTrue(second.sync.fetchLatestCsv().isFailure)
        assertNull(second.patients.getById("tea")); assertNull(second.cursors.lastMessageId(SyncChannel.CSV))
        assertEquals(before, second.settings.getAll())
    }
    @Test fun sendResponseLossRetainsUnknownReceiptAndBlocksAutomaticRetry() = runBlocking<Unit> {
        first.seedPatient()
        val ledger = ReportDeliveryLedger(first.settings, first.sessions, first.doctors)
        val sender = ReportSender(first.context, ProjectDataCipher(first.projects, first.dispatchers), first.telegram,
            first.topics, first.shifts, first.audit, ledger)
        val shift = requireNotNull(first.shifts.getById("today"))
        server.fault = { _, method, phase -> if (method == "sendMessage" && phase == "after")
            throw IOException("synthetic lost delivery response") }
        try { sender.sendTextReport(shift, listOf("تقرير تجريبي"), first.actor, "reviewed"); fail("Ambiguous send reported success") }
        catch (_: Exception) { }
        assertEquals(DeliveryStatus.UNKNOWN, ledger.observe().first().single().status)
        val sends = server.deliveredTexts.size
        try { sender.sendTextReport(shift, listOf("تقرير تجريبي"), first.actor, "reviewed"); fail("Automatic repeat accepted") }
        catch (_: Exception) { }
        assertEquals(sends, server.deliveredTexts.size)
    }
    @Test fun indexWriteFailureBeforeAcceptanceKeepsPreviousRemoteSnapshotAndLocalPendingEdit() = runBlocking<Unit> {
        publishAndRetrieve(); edit(first, "تعديل قبل فشل الفهرس")
        val old = server.state.csvFileId
        server.fault = { who, method, phase -> if (who == "first" && method == "editMessageText" && phase == "before")
            throw IOException("synthetic rejected index write") }
        assertTrue(first.sync.uploadReviewedCsv(first.sync.prepareCsvPublication("today")).isFailure)
        assertEquals(old, server.state.csvFileId)
        assertTrue(PatientPublicationStatus(first.shifts, first.patients, first.doctors, first.settings, first.dispatchers).hasPending())
        assertEquals("تعديل قبل فشل الفهرس", first.patients.getById("tea")?.labs)
    }
    @Test fun acceptedIndexWithLostResponseRemainsRetrievableByIndependentPeer() = runBlocking<Unit> {
        publishAndRetrieve(); edit(first, "نشر مع رد مفقود")
        val old = server.state.csvFileId
        server.fault = { who, method, phase -> if (who == "first" && method == "editMessageText" && phase == "after")
            throw IOException("synthetic lost accepted-index response") }
        first.sync.uploadReviewedCsv(first.sync.prepareCsvPublication("today")) // Success depends on whether the real store can confirm by rereading.
        assertNotEquals(old, server.state.csvFileId)
        server.fault = { _, _, _ -> }
        second.sync.fetchLatestCsv().getOrThrow()
        assertEquals("نشر مع رد مفقود", second.patients.getById("tea")?.labs)
        assertEquals("نشر مع رد مفقود", first.patients.getById("tea")?.labs)
    }
    @Test fun simulatedStorageExhaustionDuringSnapshotApplyRollsBackClinicalAndSettingsTogether() = runBlocking<Unit> {
        first.seedPatient(); first.publish()
        val before = second.settings.getAll()
        val prefix = AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX
        second.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_full BEFORE INSERT ON settings " +
            "WHEN NEW.key LIKE '$prefix%' BEGIN SELECT RAISE(ABORT, 'synthetic SQLITE_FULL'); END")
        try {
            assertTrue(second.sync.fetchLatestCsv().isFailure)
            assertNull(second.patients.getById("tea")); assertNull(second.shifts.getById("today"))
            assertNull(second.cursors.lastMessageId(SyncChannel.CSV))
            assertEquals(before, second.settings.getAll())
        } finally { second.db.openHelper.writableDatabase.execSQL("DROP TRIGGER synthetic_full") }
        second.sync.fetchLatestCsv().getOrThrow()
        assertEquals("شاي", second.patients.getById("tea")?.name)
    }
    @Test fun localEditRacingDownloadWinsLocallyAndPreventsRemoteApply() = runBlocking<Unit> {
        publishAndRetrieve(); edit(first, "نشر جهاز أول"); first.publish()
        var changed = false
        server.fault = { who, method, phase ->
            if (!changed && who == "second" && method.endsWith(".bin") && phase == "before") {
                changed = true
                runBlocking { edit(second, "تعديل أثناء الجلب") }
            }
        }
        assertTrue(second.sync.fetchLatestCsv().isFailure)
        assertTrue(changed)
        assertEquals("تعديل أثناء الجلب", second.patients.getById("tea")?.labs)
        assertTrue(PatientPublicationStatus(second.shifts, second.patients, second.doctors, second.settings, second.dispatchers).hasPending())
    }
}
