package com.hos.rushdpatients.data.db

import com.hos.rushdpatients.domain.report.DeliveryStatus
import android.content.*
import android.net.Uri
import android.os.*
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.report.ReportDeliveryBlockedException
import com.hos.rushdpatients.domain.export.PendingExportStore
import com.hos.rushdpatients.sync.ShiftCsvSnapshot
import com.hos.rushdpatients.testutil.*
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Kills actual production operations in a dedicated process, then reopens their durable state. */
@RunWith(AndroidJUnit4::class)
class ProcessDeathWorkflowTest {
    private fun execute(mode: Int, prepare: suspend (CrashWorkflowHarness) -> Uri? = { null },
        verify: suspend (CrashWorkflowHarness) -> Unit) = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = IsolatedStorageContext()
        val name = "instrumentation-crash-${UUID.randomUUID()}"
        val workflowContext = if (mode >= 9) CrashPreferencesContext(context, name) else preferences
        val connected = CountDownLatch(1); val died = CountDownLatch(1); val boundaryReached = CountDownLatch(1)
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == 101 && message.arg1 == mode) boundaryReached.countDown()
            true
        })
        var messenger: Messenger? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { messenger = Messenger(binder); connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) { died.countDown() }
        }
        var bound = false
        try {
            val backupUri = Room.databaseBuilder(context, AppDatabase::class.java, name).build().let { db ->
                try { prepare(CrashWorkflowHarness(workflowContext, db)) } finally { db.close() }
            }
            bound = context.bindService(Intent(context, TransactionCrashService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound)
            assertTrue("Workflow crash worker did not connect", connected.await(10, TimeUnit.SECONDS))
            messenger!!.send(Message.obtain().apply {
                what = mode
                replyTo = reply
                data = Bundle().apply { putString("database", name); backupUri?.let { putString("backupUri", it.toString()) } }
            })
            assertTrue("Workflow crash worker did not die", died.await(20, TimeUnit.SECONDS))
            assertTrue("Worker failed before reaching the intended production kill boundary", boundaryReached.await(5, TimeUnit.SECONDS))
            Room.databaseBuilder(context, AppDatabase::class.java, name).build().let { db ->
                try { verify(CrashWorkflowHarness(workflowContext, db)) } finally { db.close() }
            }
        } finally {
            if (bound) context.unbindService(connection)
            context.deleteDatabase(name)
            // Only this worker's explicitly namespaced preferences; never the shared master key.
            File(context.applicationInfo.dataDir, "shared_prefs").listFiles()
                ?.filter { it.name.startsWith("${name}_") }?.forEach { it.delete() }
            preferences.cleanUp()
            File(context.cacheDir, "instrumentation_worker/$name").deleteRecursively()
        }
    }

    @Test fun deathAfterUnknownReceiptCommitBlocksChangedContentAfterRestart() = receipt(false)
    @Test fun deathAfterSentReceiptCommitBlocksAccidentalDuplicateAfterRestart() = receipt(true)
    private fun receipt(sent: Boolean) = execute(if (sent) 4 else 3) { core ->
        val record = core.ledger.observe().first().single()
        assertEquals(if (sent) DeliveryStatus.SENT else DeliveryStatus.UNKNOWN, record.status)
        assertEquals(if (sent) listOf(42L) else emptyList<Long>(), record.messageIds)
        core.signIn()
        val content = if (sent) "synthetic-content" else "changed-content"
        try {
            core.ledger.check("crash-shift", -100, content, "pdf")
            fail("Persisted delivery evidence allowed an accidental repeat")
        } catch (_: ReportDeliveryBlockedException) { }
    }

    @Test fun deathAfterExportStagingAllowsExplicitRecoveryFromEncryptedBytes() = exportRecovery(false)
    @Test fun deathAfterVerifiedExportBeforeDiscardRetainsExactDestinationAndRecoverableStaging() = exportRecovery(true)
    private fun exportRecovery(written: Boolean) = execute(if (written) 10 else 9) { core ->
        val id = requireNotNull(core.settings.get("crash-export-id"))
        val expected = "شاي — تصدير تجريبي 💊".toByteArray(Charsets.UTF_8)
        val staged = File(core.context.noBackupFilesDir, "pending_document_exports/$id")
        assertTrue(staged.isFile)
        assertFalse(staged.readBytes().toString(Charsets.UTF_8).contains("شاي"))
        val original = File(core.context.cacheDir, "synthetic-export.txt")
        assertEquals(written, original.isFile)
        if (written) assertArrayEquals(expected, original.readBytes())
        val recovery = File(core.context.cacheDir, "explicit-recovery.txt")
        val exports = PendingExportStore(core.context, core.dispatchers)
        exports.write(Uri.fromFile(recovery), id)
        assertArrayEquals(expected, recovery.readBytes())
        exports.discard(id)
        assertFalse(staged.exists())
        assertArrayEquals(expected, recovery.readBytes())
    }

    @Test fun deathAfterSyntheticAcceptanceBeforeConfirmationKeepsUnknownAndBlocksRepeat() = execute(11) { core ->
        assertEquals("42", core.settings.get("synthetic-delivery-accepted"))
        val record = core.ledger.observe().first().single()
        assertEquals(DeliveryStatus.UNKNOWN, record.status)
        assertTrue(record.messageIds.isEmpty())
        core.signIn()
        try {
            core.ledger.check("crash-shift", -100, "synthetic-content", "pdf")
            fail("Unconfirmed accepted delivery allowed an automatic duplicate")
        } catch (_: ReportDeliveryBlockedException) { }
    }

    @Test fun deathAfterDraftCommitRetainsMultilineAndUnfinishedTaskWithoutClinicalSave() = execute(12,
        prepare = { core -> seed(core); null }) { core ->
        val encoded = requireNotNull(core.settings.get(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + "crash-patient"))
        val draft = kotlinx.serialization.json.Json.decodeFromString(com.hos.rushdpatients.ui.ward.PatientDraft.serializer(), encoded)
        val form = com.hos.rushdpatients.ui.ward.PatientFormState(null, draft)
        assertEquals("تشخيص\nسطر ثان", form.initialDiagnosis)
        assertEquals("خطة\n12.5 mg", form.treatmentText)
        assertEquals("Na=140\nK=4", form.labText)
        assertEquals("unfinished-inline", form.tasks.single().id)
        assertEquals("مهمة قيد الكتابة", form.tasks.single().description)
        assertEquals("before", core.patients.getById("crash-patient")?.labs)
        assertEquals(7L, core.patients.getById("crash-patient")?.revision)
    }

    private suspend fun seed(core: CrashWorkflowHarness): Patient {
        core.shifts.upsert(Shift(id = "crash-shift", date = ShiftDate.current(), doctorIds = emptyList(), revision = 7))
        core.patients.upsert(Patient(id = "crash-patient", name = "شاي", admittanceNumber = "12",
            admittanceDate = LocalDate.of(2020, 1, 1), birthDate = LocalDate.of(1990, 1, 1), initialDiagnosis = "تشخيص تجريبي",
            treatmentPlan = "خطة تجريبية", followUp = "متابعة تجريبية", labs = "before", revision = 7), "crash-shift")
        core.settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false)
        return requireNotNull(core.patients.getById("crash-patient"))
    }

    @Test fun deathDuringActualBackupRestoreRollsBackClinicalRevisionAndPendingTogether() = restore(false)
    @Test fun deathAfterActualBackupRestoreRetainsCommittedClinicalRevisionAndPending() = restore(true)
    private fun restore(committed: Boolean) = execute(if (committed) 6 else 5, prepare = { core ->
        val patient = seed(core)
        val backup = core.backup.prepareExport("synthetic-password".toCharArray())
        val file = File(core.context.cacheDir, "synthetic-process-backup.rpb")
        try { file.writeBytes(backup.bytes) } finally { backup.bytes.fill(0) }
        core.patients.upsert(patient.copy(labs = "current", revision = 9), "crash-shift")
        val shift = requireNotNull(core.shifts.getById("crash-shift"))
        core.shifts.upsert(shift.copy(revision = 9))
        Uri.fromFile(file)
    }) { core ->
        val patient = requireNotNull(core.patients.getById("crash-patient"))
        assertEquals(if (committed) "before" else "current", patient.labs)
        assertEquals(if (committed) 10L else 9L, patient.revision)
        assertEquals(if (committed) 10L else 9L, core.shifts.getById("crash-shift")?.revision)
        assertFalse(core.settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))
        assertTrue(com.hos.rushdpatients.sync.PatientPublicationStatus(core.shifts, core.patients, core.doctors,
            core.settings, core.dispatchers).hasPending())
    }

    @Test fun deathDuringActualDownloadUndoRetainsDownloadedRowsAndUndoTicket() = undo(false)
    @Test fun deathAfterActualDownloadUndoRetainsRestoredRowsAndDerivedPendingWithoutTicket() = undo(true)
    private fun undo(committed: Boolean) = execute(if (committed) 8 else 7, prepare = { core ->
        val patient = seed(core)
        val shift = requireNotNull(core.shifts.getById("crash-shift"))
        core.db.withTransaction {
            core.patients.upsert(patient.copy(labs = "downloaded", revision = 9), shift.id)
            core.shifts.upsert(shift.copy(revision = 9))
            core.undo.record(listOf(ShiftCsvSnapshot(shift, listOf(patient))), listOf(shift.id))
        }
        null
    }) { core ->
        val patient = requireNotNull(core.patients.getById("crash-patient"))
        assertEquals(if (committed) "before" else "downloaded", patient.labs)
        assertEquals(if (committed) 10L else 9L, patient.revision)
        assertEquals(!committed, core.undo.available.first())
        assertFalse(core.settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))
        assertTrue(com.hos.rushdpatients.sync.PatientPublicationStatus(core.shifts, core.patients, core.doctors,
            core.settings, core.dispatchers).hasPending())
        assertTrue(core.audit.getRecent().isEmpty())
    }
}
