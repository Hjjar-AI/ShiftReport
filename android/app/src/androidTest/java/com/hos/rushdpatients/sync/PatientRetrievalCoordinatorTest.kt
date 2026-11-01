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
        shifts.selectCurrent("today")
        wire.files["csv"] = CsvBundleCodec.encode(listOf(ShiftCsvSnapshot(shift, listOf(patient))))
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
        assertFalse(PatientPublicationStatus(shifts, patients, doctors, settings, dispatchers).hasPending())
        assertNotNull(settings.get(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + "today"))
        undo.undo()
        assertTrue(patients.getForShift("today").isEmpty())
    }
    @Test fun pendingLocalDataIsReplacedAndUndoRestoresIt() = runBlocking<Unit> {
        publication()
        patients.upsert(Patient(id = "local", name = "قهوة"), "today")
        assertTrue(retrieval.fetchLatestCsv().isSuccess)
        assertNotNull(patients.getById("p"))
        assertTrue(requireNotNull(patients.getById("local")).deletedAt != null)
        undo.undo()
        assertEquals("قهوة", patients.getById("local")?.name)
        assertTrue(requireNotNull(patients.getById("p")).deletedAt != null)
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
    @Test fun currentPinReplacesHigherKnownDocumentId() = runBlocking<Unit> {
        publication()
        cursors.update(SyncChannel.CSV, 200, 20, "newer")
        assertTrue(retrieval.fetchLatestCsv().isSuccess)
        assertTrue("getFile" in wire.methods)
        assertEquals(10L, cursors.lastMessageId(SyncChannel.CSV))
        assertNotNull(patients.getById("p"))
    }
    @Test fun currentPinReplacesBaselineWithoutOlderMessageApproval() = runBlocking<Unit> {
        publication()
        cursors.update(SyncChannel.CSV, 200, 20, "newer")
        settings.put(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + "today", "preserved-baseline")
        assertTrue(retrieval.fetchLatestCsv().isSuccess)
        assertEquals(10L, cursors.lastMessageId(SyncChannel.CSV))
        assertNotEquals("preserved-baseline", settings.get(AppConstants.SETTING_KNOWN_SHIFT_SNAPSHOT_PREFIX + "today"))
        assertNotNull(patients.getById("p"))
    }

    @Test fun coupledRegistryReplacementAndUndoRestoreOnlyLocalState() = runBlocking<Unit> {
        publication()
        val obsolete = doctor("obsolete").copy(clinicalRole = ClinicalRole.RESIDENT, telegramId = 999007)
        doctors.upsert(obsolete)
        patients.upsert(Patient(id = "local", name = "قهوة", responsibleResidentId = obsolete.id), "today")
        shifts.updateDoctorIds("today", listOf(obsolete.id))
        val replacement = doctor("replacement").copy(clinicalRole = ClinicalRole.RESIDENT, telegramId = obsolete.telegramId)
        val incoming = requireNotNull(shifts.getById("today")).copy(doctorIds = listOf(replacement.id))
        wire.state = wire.state.copy(doctorsFileId = wire.registry(DoctorsRegistryCodec.encode(listOf(admin, replacement))), doctorsMessageId = 30)
        wire.files["csv"] = CsvBundleCodec.encode(listOf(ShiftCsvSnapshot(incoming,
            listOf(Patient(id = "remote", name = "شاي", responsibleResidentId = replacement.id)))))
        retrieval.fetchLatestCsv().getOrThrow()
        assertTrue(requireNotNull(patients.getById("local")).deletedAt != null)
        assertEquals(replacement.id, patients.getById("remote")?.responsibleResidentId)
        assertTrue(doctors.getById(obsolete.id)?.isDeleted == true)
        assertNull(doctors.getById(obsolete.id)?.telegramId)
        assertEquals(obsolete.telegramId, doctors.getById(replacement.id)?.telegramId)
        wire.methods.clear()
        undo.undo()
        assertEquals(obsolete.id, patients.getById("local")?.responsibleResidentId)
        assertEquals(listOf(obsolete.id), shifts.getById("today")?.doctorIds)
        assertFalse(requireNotNull(doctors.getById(obsolete.id)).isDeleted)
        assertEquals(obsolete.telegramId, doctors.getById(obsolete.id)?.telegramId)
        assertNull(doctors.getById(replacement.id)?.telegramId)
        assertTrue(requireNotNull(patients.getById("remote")).deletedAt != null)
        assertTrue(wire.methods.isEmpty())
    }


    @Test fun sameDateWardIdsRemainDistinctAndUndoRestoresSelection() = runBlocking<Unit> {
        publication()
        val prior = requireNotNull(shifts.getById("today")).copy(doctorIds = listOf(admin.id))
        patients.upsert(Patient(id = "retained", name = "قهوة"), prior.id)
        val remoteWard = prior.copy(id = "other-ward", date = prior.date.minusDays(1))
        wire.files["csv"] = CsvBundleCodec.encode(listOf(ShiftCsvSnapshot(remoteWard,
            listOf(Patient(id = "remote", name = "شاي")))))
        // Same date as the incoming ward must not alias its explicit ID.
        shifts.upsert(prior.copy(date = remoteWard.date))
        retrieval.fetchLatestCsv().getOrThrow()
        assertEquals("other-ward", shifts.getLatest()?.id)
        assertNotNull(patients.getById("retained"))
        assertEquals(remoteWard.date, shifts.getById("other-ward")?.date)
        undo.undo()
        assertEquals("today", shifts.getLatest()?.id)
        assertTrue(requireNotNull(patients.getById("remote")).deletedAt != null)
        assertNotNull(patients.getById("retained"))
    }

}
