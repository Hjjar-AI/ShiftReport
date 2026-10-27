package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.data.model.PatientTaskCodec
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class CsvClinicalIntegrityTest {
    private val shift = Fixtures.shift()
    private fun decode(patient: com.hos.rushdpatients.data.model.Patient) =
        CsvCodec.decode(CsvCodec.encode(shift, listOf(patient))).patients.single()

    @Test fun `mixed Arabic delimiters quotes and line endings survive every clinical text field`() {
        val values = listOf("سطر، عربي", "أرز, شاي", "قال \"قهوة\"", "سطر\nثان", "سطر\r\nثان",
            "سطر\rثان", "\"\",\n\r\nنهاية", "☕\tنتيجة;متابعة")
        values.forEach { text ->
            val patient = Fixtures.patient(initialDiagnosis = text, treatmentPlan = text, labs = text, followUp = text)
            assertEquals("Lost clinical content for $text", patient, decode(patient))
        }
    }

    @Test fun `nested task JSON and badge delimiters survive CSV quoting together`() {
        val text = "قياس, \"تجريبي\"\nسطر\r\nثان؛☕"
        val patient = Fixtures.patient().copy(tasks = listOf(Fixtures.task().copy(description = text),
            Fixtures.task("done", true).copy(description = text)), badges = listOf(PatientBadge(text)))
        assertEquals(patient, decode(patient))
    }

    @Test fun `long clinical fields and large task lists retain exact content`() {
        val text = "نتيجة, \"قياس\"\n".repeat(2_000)
        val patient = Fixtures.patient(labs = text, followUp = text).copy(
            tasks = (1..200).map { Fixtures.task("task-$it").copy(description = "مهمة $it, \"تجريبية\"") })
        assertEquals(patient, decode(patient))
    }

    @Test fun `one invalid task rejects entire otherwise valid multirecord snapshot`() {
        val good = Fixtures.patient(id = "good")
        val bad = Fixtures.patient(id = "bad").copy(tasks = listOf(Fixtures.task().copy(description = " ")))
        val csv = CsvCodec.encode(shift, listOf(good, bad, good.copy(id = "good-2")))
        assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(csv) }
    }

    @Test fun `duplicate nested task identity rejects entire snapshot`() {
        val task = Fixtures.task()
        val patient = Fixtures.patient().copy(tasks = listOf(task, task.copy(description = "وصف آخر")))
        assertThrows(IllegalArgumentException::class.java) { decode(patient) }
    }

    @Test fun `legacy completion metadata remains compatible on pending tasks`() {
        val patient = Fixtures.patient().copy(tasks = listOf(Fixtures.task().copy(completedByName = "أرز")))
        assertEquals(patient, decode(patient))
    }

    @Test fun `missing current task column is rejected even with no clinical rows`() {
        val csv = CsvCodec.encode(shift, emptyList()).replace(CsvSchema.COL_TASKS, "unknown_column")
        assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(csv) }
    }

    @Test fun `reordered header and data columns preserve full patient`() {
        val patient = Fixtures.patient().copy(tasks = listOf(Fixtures.task()), revision = 9)
        val rows = CsvCodec.parseCsv(CsvCodec.encode(shift, listOf(patient)))
        fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
        val reordered = listOf(rows.first(), rows[1].reversed(), rows[2].reversed())
            .joinToString("\n") { row -> row.joinToString(",", transform = ::quote) }
        assertEquals(patient, CsvCodec.decode(reordered).patients.single())
    }

    @Test fun `encoding follows caller order and decoding assigns consecutive persisted positions`() {
        val patients = (1..30).map { Fixtures.patient(id = "p-$it", sortOrder = 100 - it) }.reversed()
        val parsed = CsvCodec.decode(CsvCodec.encode(shift, patients)).patients
        assertEquals(patients.map { it.id }, parsed.map { it.id })
        assertEquals((1..30).toList(), parsed.map { it.sortOrder })
        assertEquals(patients.map { it.sortOrder }, (1..30).map { 100 - it }.reversed())
    }

    @Test fun `unlimited roster and sort metadata survive beside multiline clinical payload`() {
        val roster = (1..120).map { "doctor-$it" }
        val sorted = shift.copy(doctorIds = roster, sortSpecJson = SortSpecCodec.encode(SortSpec.WORKBOOK))
        val patient = Fixtures.patient(labs = "نتيجة\n\"أ\", ب").copy(tasks = listOf(Fixtures.task()))
        val parsed = CsvCodec.decode(CsvCodec.encode(sorted, listOf(patient)))
        assertEquals(roster, parsed.doctorIds)
        assertEquals(sorted.sortSpecJson, parsed.sortSpecJson)
        assertEquals(patient, parsed.patients.single())
        assertEquals(patient.tasks, PatientTaskCodec.decode(PatientTaskCodec.encode(parsed.patients.single().tasks)))
    }
    @Test fun `assignment date survives CSV and model persistence round trip without restamping`() {
        for (date in listOf(null, java.time.LocalDate.of(2026, 10, 27))) {
            val patient = Fixtures.patient().copy(assignmentChangedOn = date)
            val decoded = decode(patient)
            assertEquals(date, decoded.assignmentChangedOn)
            val stored = com.hos.rushdpatients.data.mapper.PatientMapper.toEntity(decoded, shift.id)
            assertEquals(patient, com.hos.rushdpatients.data.mapper.PatientMapper.fromEntity(stored))
        }
    }
    @Test fun `legacy missing assignment column stays unknown and malformed dates reject entire CSV`() {
        val patient = Fixtures.patient().copy(assignmentChangedOn = java.time.LocalDate.of(2026, 10, 27))
        val csv = CsvCodec.encode(shift, listOf(patient))
        // This fixture has no embedded newlines. Remove the trailing optional column
        // from the header and record, preserving all older required fields.
        val lines = csv.trimEnd().lines()
        assertEquals(3, lines.size)
        val legacy = listOf(lines[0], lines[1].substringBeforeLast(','), lines[2].substringBeforeLast(',')).joinToString("\n")
        assertNull(CsvCodec.decode(legacy).patients.single().assignmentChangedOn)
        for (invalid in listOf("2026-02-30", "yesterday", "2026-13-01")) {
            val malformed = csv.replace("2026-10-27", invalid)
            assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(malformed) }
        }
    }

}
