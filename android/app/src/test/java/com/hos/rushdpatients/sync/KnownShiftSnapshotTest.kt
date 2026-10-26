package com.hos.rushdpatients.sync

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class KnownShiftSnapshotTest {
    private val shift = Fixtures.shift().copy(sortSpecJson =
        com.hos.rushdpatients.domain.sort.SortSpecCodec.encode(com.hos.rushdpatients.domain.sort.SortSpec.EMPTY))
    private val patient = Fixtures.patient()
    private val csv = CsvCodec.encode(shift, listOf(patient))
    @Test fun `absent corrupt and other shift snapshots are unknown`() {
        assertEquals(KnownShiftState.UNKNOWN, KnownShiftSnapshot.compare(null, listOf(patient), csv))
        for (value in listOf(null, "", "corrupt", CsvCodec.encode(shift.copy(id = "other"), listOf(patient)))) {
            assertEquals(KnownShiftState.UNKNOWN, KnownShiftSnapshot.compare(shift, listOf(patient), value))
        }
    }
    @Test fun `matching is local content evidence ignoring revisions and deleted rows`() {
        assertEquals(KnownShiftState.MATCHED, KnownShiftSnapshot.compare(shift.copy(revision = 100),
            listOf(patient.copy(revision = 99, updatedAt = Instant.EPOCH),
                Fixtures.patient(id = "deleted").copy(deletedAt = Instant.EPOCH)), csv))
    }
    @Test fun `clinical edits roster changes and displayed order changes are detected`() {
        assertEquals(KnownShiftState.CHANGED, KnownShiftSnapshot.compare(shift, listOf(patient.copy(labs = "changed")), csv))
        assertEquals(KnownShiftState.CHANGED, KnownShiftSnapshot.compare(shift.copy(doctorIds = listOf("other")), listOf(patient), csv))
        val second = Fixtures.patient(id = "second", sortOrder = 2)
        val two = CsvCodec.encode(shift, listOf(patient, second))
        assertEquals(KnownShiftState.CHANGED, KnownShiftSnapshot.compare(shift,
            listOf(patient.copy(sortOrder = 2), second.copy(sortOrder = 1)), two))
    }
}
