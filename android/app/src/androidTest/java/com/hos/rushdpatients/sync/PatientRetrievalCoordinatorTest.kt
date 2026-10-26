package com.hos.rushdpatients.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.SyncFixture
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PatientRetrievalCoordinatorTest : SyncFixture() {
    private suspend fun publication() {
        seedShift("today", ShiftDate.current())
        val shift = requireNotNull(shifts.getById("today")).copy(doctorIds = listOf(admin.id), revision = 2)
        val patient = Patient(id = "p", name = "شاي", admittanceNumber = "1", admittanceDate = ShiftDate.current(),
            birthDate = LocalDate.of(1990, 1, 1), initialDiagnosis = "تجريبي", treatmentPlan = "خطة", followUp = "متابعة", revision = 2)
        wire.files["csv"] = CsvCodec.encode(shift, listOf(patient))
        wire.state = wire.state.copy(csvFileId = "csv", csvMessageId = 10, csvUpdatedAt = 100,
            previousCsvFileId = "csv", previousCsvMessageId = 9)
    }
    @Test fun downloadAppliesSnapshotAndPersistsCursorAndUndo() = runBlocking<Unit> {
        publication()
        val result = retrieval.fetchLatestCsv().getOrThrow()
        assertEquals(1, result.inserted)
        assertEquals("شاي", patients.getById("p")?.name)
        assertEquals(10L, cursors.lastMessageId(SyncChannel.CSV))
        assertEquals(10L, shifts.getById("today")?.csvMessageId)
        assertFalse(settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true))
        assertNotNull(settings.get(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + "today"))
        undo.undo()
        assertTrue(patients.getForShift("today").isEmpty())
    }
    @Test fun pendingLocalDataPreventsAnyDownload() = runBlocking<Unit> {
        publication()
        settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true)
        assertTrue(retrieval.fetchLatestCsv().isFailure)
        assertTrue(wire.methods.isEmpty())
        assertNull(patients.getById("p"))
    }
    @Test fun localEditDuringNetworkDownloadCannotBeOverwritten() = runBlocking<Unit> {
        publication()
        wire.beforeResponse = { method -> if (method == "csv.txt") runBlocking {
            patients.upsert(Patient(id = "local", name = "قهوة"), "today")
            settings.putBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, true)
        } }
        assertTrue(retrieval.fetchLatestCsv().isFailure)
        assertEquals("قهوة", patients.getById("local")?.name)
        assertNull(patients.getById("p"))
        assertNull(cursors.lastMessageId(SyncChannel.CSV))
    }
    @Test fun corruptRemoteFileLeavesRowsCursorAndUndoUntouched() = runBlocking<Unit> {
        publication()
        wire.files["csv"] = "invalid data"
        assertTrue(retrieval.fetchLatestCsv().isFailure)
        assertNull(patients.getById("p"))
        assertNull(cursors.lastMessageId(SyncChannel.CSV))
        assertTrue(db.auditDao().getRecent(10).isEmpty())
    }
    @Test fun olderRemotePointerCannotRollBackLastKnownPublication() = runBlocking<Unit> {
        publication()
        cursors.update(SyncChannel.CSV, 200, 20, "newer")
        assertTrue(retrieval.fetchLatestCsv().isFailure)
        assertFalse("getFile" in wire.methods)
        assertEquals(20L, cursors.lastMessageId(SyncChannel.CSV))
    }
    @Test fun explicitRecoveryDoesNotRewindCurrentCursorOrBaseline() = runBlocking<Unit> {
        publication()
        cursors.update(SyncChannel.CSV, 200, 20, "newer")
        settings.put(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + "today", "preserved-baseline")
        retrieval.fetchPreviousCsv().getOrThrow()
        assertEquals(20L, cursors.lastMessageId(SyncChannel.CSV))
        assertEquals("preserved-baseline", settings.get(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + "today"))
        assertTrue(settings.getBoolean(AppConstants.SETTING_PATIENTS_SYNC_PENDING, false))
    }
}
