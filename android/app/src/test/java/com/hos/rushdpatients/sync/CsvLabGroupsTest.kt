package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class CsvLabGroupsTest {
    private fun mutate(csv: String, row: Int, column: String, value: String): String {
        val records = CsvCodec.parseCsv(csv).map { it.toMutableList() }.toMutableList()
        records[row][records[1].indexOf(column)] = value
        return records.joinToString("\n") { record ->
            record.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        }
    }
    @Test fun `full snapshot round trip retains ordered dated panels and publication context`() {
        val shift = Fixtures.shift(doctorIds = listOf("cedar", "oak")).copy(snapshotId = "snapshot", baseSnapshotId = "base", revision = 4)
        val groups = Fixtures.labGroups() + PatientLabGroup("quoted", "نتيجة, \"خاصة\" ☕", results = "A\r\n\r\nB\t\\path")
        val patient = Fixtures.patientWithLabs(groups).copy(sortOrder = 1)
        val decoded = CsvCodec.decode(CsvCodec.encode(shift, listOf(patient)))
        assertEquals(patient, decoded.patients.single())
        assertEquals(shift.doctorIds, decoded.doctorIds)
        assertEquals("snapshot", decoded.snapshotId)
        assertEquals("base", decoded.baseSnapshotId)
        assertEquals(4L, decoded.shiftRevision)
    }
    @Test fun `malformed second patient never yields a partial decoded snapshot`() {
        val first = Fixtures.patientWithLabs()
        val csv = CsvCodec.encode(Fixtures.shift(), listOf(first, first.copy(id = "coffee", sortOrder = 2)))
        listOf("null", "{}", "[{\"id\":\"same\"},{\"id\":\"same\"}]",
            "[{\"id\":\"bad-date\",\"date\":\"2025-02-29\"}]").forEach { invalid ->
            assertThrows(RuntimeException::class.java) {
                CsvCodec.decode(mutate(csv, 3, CsvSchema.COL_LAB_GROUPS, invalid))
            }
        }
        assertEquals(2, CsvCodec.decode(csv).patients.size)
    }
    @Test fun `altered plain text cannot hide behind valid structured groups`() {
        val csv = CsvCodec.encode(Fixtures.shift(), listOf(Fixtures.patientWithLabs()))
        assertThrows(IllegalArgumentException::class.java) {
            CsvCodec.decode(mutate(csv, 2, CsvSchema.COL_LABS, "content not present in the groups"))
        }
    }
    @Test fun `unstructured current records preserve all text without inferred panels or dates`() {
        val raw = "CBC:\nHb=12\n\nعنوان: ليس افتراضاً\n  Na=140  "
        val original = Fixtures.patient(labs = raw)
        val decoded = CsvCodec.decode(CsvCodec.encode(Fixtures.shift(), listOf(original))).patients.single()
        assertEquals(raw, decoded.labs)
        assertTrue(decoded.labGroups.isEmpty())
        assertEquals(raw, decoded.effectiveLabGroups.single().results)
        assertNull(decoded.effectiveLabGroups.single().date)
    }
    @Test fun `removing a whole group clears both representations across subsequent exports`() {
        val patient = Fixtures.patientWithLabs()
        val remaining = patient.labGroups.drop(1)
        val changed = patient.copy(labGroups = remaining, labs = PatientLabGroups.text(remaining))
        val restored = CsvCodec.decode(CsvCodec.encode(Fixtures.shift(), listOf(changed))).patients.single()
        assertEquals(remaining, restored.labGroups)
        assertFalse(restored.labText.contains("Hb"))
        assertTrue(restored.labText.contains("Na = 140"))
    }
    @Test fun `bundle boundaries inside group titles or results cannot corrupt the next snapshot`() {
        listOf("title", "results").forEach { field ->
            val delimiter = "first\n#==RUSHD_SHIFT_BOUNDARY_7F3A9C==\nlast"
            val group = PatientLabGroup("g", title = if (field == "title") delimiter else "CBC",
                results = if (field == "results") delimiter else "Hb=12")
            assertThrows(IllegalArgumentException::class.java) {
                CsvBundleCodec.encode(listOf(ShiftCsvSnapshot(Fixtures.shift(), listOf(Fixtures.patientWithLabs(listOf(group))))))
            }
        }
    }
    @Test fun `desktop generated grouped fixture is decoded by the real Android bundle codec`() {
        val csv = requireNotNull(javaClass.getResourceAsStream("/interop/desktop-labs.bundle.csv"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val decoded = CsvBundleCodec.decode(csv).single()
        val patient = decoded.patients.single()
        assertEquals("synthetic-shift", decoded.shiftId)
        assertEquals(listOf("cbc", "renal"), patient.labGroups.map { it.id })
        assertEquals("2024-02-29", patient.labGroups.first().date)
        assertNull(patient.labGroups.last().date)
        assertEquals("Hb=12.5 g/dL\nWBC=6000 /µL", patient.labGroups.first().results)
        assertEquals("Na=140 mmol/L\nK=4.1 mmol/L", patient.labGroups.last().results)
        assertEquals(patient.labs, patient.labText)
        assertEquals(2, patient.tasks.size)
        assertEquals("snapshot", decoded.snapshotId)
    }

}
