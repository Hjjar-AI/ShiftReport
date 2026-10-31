package com.hos.rushdpatients.sync

import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CsvAdversarialTest {
    private val shift = Fixtures.shift()
    @Test fun `duplicate required or optional headers reject ambiguous import`() {
        val original = CsvCodec.encode(shift, listOf(Fixtures.patient()))
        listOf(CsvSchema.COL_NAME, CsvSchema.COL_TASKS, CsvSchema.COL_REVISION).forEach { column ->
            val lines = original.lines().toMutableList()
            lines[1] += ",$column"
            assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(lines.joinToString("\n")) }
        }
    }
    @Test fun `header whitespace cannot disguise duplicate column`() {
        val lines = CsvCodec.encode(shift, emptyList()).lines().toMutableList()
        lines[1] += ", ${CsvSchema.COL_NAME} "
        assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(lines.joinToString("\n")) }
    }
    @Test fun `unterminated quoted field never yields partial records`() {
        for (text in listOf("\"", "a,\"b", "\"first\nsecond", "a,b\n\"last")) {
            assertThrows(IllegalArgumentException::class.java) { CsvCodec.parseCsv(text) }
        }
    }
    @Test fun `negative patient and shift revisions rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvCodec.decode(CsvCodec.encode(shift, listOf(Fixtures.patient().copy(revision = -1))))
        }
        assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(CsvCodec.encode(shift.copy(revision = -1), emptyList())) }
    }
    @Test fun `generated unicode and escaping combinations preserve all clinical fields`() {
        val random = Random(74521)
        val alphabet = listOf("ش", "ا", "☕", "\"", ",", "\n", "\r", "\t", "\u200f", "💊", "A", "١")
        repeat(150) { iteration ->
            val text = buildString { repeat(random.nextInt(1, 120)) { append(alphabet[random.nextInt(alphabet.size)]) } }
            val patient = Fixtures.patient(initialDiagnosis = text, treatmentPlan = text, labs = text, followUp = text)
            val result = CsvCodec.decode(CsvCodec.encode(shift, listOf(patient))).patients.single()
            assertEquals("Generated case $iteration", patient, result)
        }
    }
    @Test fun `bundle delimiter in clinical content fails explicitly rather than generating unreadable snapshot`() {
        val patient = Fixtures.patient(labs = "first\n#==RUSHD_SHIFT_BOUNDARY_7F3A9C==\nlast")
        assertThrows(IllegalArgumentException::class.java) { CsvBundleCodec.encode(listOf(ShiftCsvSnapshot(shift, listOf(patient)))) }
    }
    @Test fun `all required columns individually enforced`() {
        val original = CsvCodec.encode(shift, emptyList()).lines()
        CsvSchema.COLUMNS.forEach { column ->
            val lines = original.toMutableList()
            lines[1] = lines[1].split(',').filterNot { it == column }.joinToString(",")
            assertThrows("Missing $column", IllegalArgumentException::class.java) { CsvCodec.decode(lines.joinToString("\n")) }
        }
    }
}
