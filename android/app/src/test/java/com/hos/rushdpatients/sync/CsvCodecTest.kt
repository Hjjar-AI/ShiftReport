package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvCodecTest {

    @Test
    fun `round trip preserves simple patient`() {
        val shift = Fixtures.shift()
        val patient = Fixtures.patient(
            id = "p-100",
            name = "محمد",
            age = 45,
            gender = Gender.MALE,
            diagnosis = "نفسي",
            condition = "مستقر",
            hasEscort = true,
            admittanceNumber = "A-123",
            treatment = "haloperidol 5mg",
            labs = "CBC normal",
            notes = "متابعة أسبوعية"
        )

        val csv = CsvCodec.encode(shift, listOf(patient))
        val parsed = CsvCodec.decode(csv)

        assertEquals(1, parsed.patients.size)
        val p = parsed.patients.first()
        assertEquals(patient.id, p.id)
        assertEquals(patient.name, p.name)
        assertEquals(patient.age, p.age)
        assertEquals(patient.gender, p.gender)
        assertEquals(patient.diagnosis, p.diagnosis)
        assertEquals(patient.condition, p.condition)
        assertEquals(patient.hasEscort, p.hasEscort)
        assertEquals(patient.admittanceNumber, p.admittanceNumber)
        assertEquals(patient.treatment, p.treatment)
        assertEquals(patient.labs, p.labs)
        assertEquals(patient.notes, p.notes)
    }

    @Test
    fun `round trip preserves commas in name`() {
        val shift = Fixtures.shift()
        val patient = Fixtures.patient(name = "أحمد, علي")
        val csv = CsvCodec.encode(shift, listOf(patient))
        val parsed = CsvCodec.decode(csv)
        assertEquals("أحمد, علي", parsed.patients.first().name)
    }

    @Test
    fun `round trip preserves double quotes`() {
        val shift = Fixtures.shift()
        val patient = Fixtures.patient(notes = """قال "مرحباً"""")
        val csv = CsvCodec.encode(shift, listOf(patient))
        val parsed = CsvCodec.decode(csv)
        assertEquals("""قال "مرحباً"""", parsed.patients.first().notes)
    }

    @Test
    fun `round trip preserves newlines`() {
        val shift = Fixtures.shift()
        val patient = Fixtures.patient(treatment = "line1\nline2")
        val csv = CsvCodec.encode(shift, listOf(patient))
        val parsed = CsvCodec.decode(csv)
        assertEquals("line1\nline2", parsed.patients.first().treatment)
    }

    @Test
    fun `empty patient list encodes header only`() {
        val shift = Fixtures.shift()
        val csv = CsvCodec.encode(shift, emptyList())
        val parsed = CsvCodec.decode(csv)
        assertTrue(parsed.patients.isEmpty())
    }

    @Test
    fun `shift date is round-tripped`() {
        val date = LocalDate.of(2026, 12, 31)
        val shift = Fixtures.shift(date = date)
        val csv = CsvCodec.encode(shift, emptyList())
        val parsed = CsvCodec.decode(csv)
        assertEquals(date, parsed.shiftDate)
    }

    @Test
    fun `shift id is round-tripped`() {
        val shift = Fixtures.shift(id = "shift-abc")
        val csv = CsvCodec.encode(shift, emptyList())
        val parsed = CsvCodec.decode(csv)
        assertEquals("shift-abc", parsed.shiftId)
    }

    @Test
    fun `doctor ids are round-tripped`() {
        val shift = Fixtures.shift(doctorIds = listOf("d1", "d2", "d3"))
        val csv = CsvCodec.encode(shift, emptyList())
        val parsed = CsvCodec.decode(csv)
        assertEquals(listOf("d1", "d2", "d3"), parsed.doctorIds)
    }

    @Test
    fun `admittance date is preserved when present`() {
        val date = LocalDate.now().minusDays(5)
        val patient = Fixtures.patient(admittanceDate = date)
        val csv = CsvCodec.encode(Fixtures.shift(), listOf(patient))
        val parsed = CsvCodec.decode(csv)
        assertEquals(date, parsed.patients.first().admittanceDate)
    }

    @Test
    fun `null admittance date decodes as null`() {
        val patient = Fixtures.patient(admittanceDate = null)
        val csv = CsvCodec.encode(Fixtures.shift(), listOf(patient))
        val parsed = CsvCodec.decode(csv)
        assertNull(parsed.patients.first().admittanceDate)
    }

    @Test
    fun `deleted patients are excluded from encoding`() {
        val shift = Fixtures.shift()
        val alive = Fixtures.patient(id = "p-alive", name = "A")
        val dead = Fixtures.patient(id = "p-dead", name = "B").copy(
            deletedAt = java.time.Instant.now()
        )
        val csv = CsvCodec.encode(shift, listOf(alive, dead))
        val parsed = CsvCodec.decode(csv)
        assertEquals(1, parsed.patients.size)
        assertEquals("p-alive", parsed.patients.first().id)
    }

    @Test
    fun `decoding handles UTF-8 BOM prefix`() {
        val shift = Fixtures.shift()
        val patient = Fixtures.patient(name = "مريض")
        val csv = "\uFEFF" + CsvCodec.encode(shift, listOf(patient))
        val parsed = CsvCodec.decode(csv)
        assertEquals("مريض", parsed.patients.first().name)
    }

    @Test
    fun `decoding handles Windows line endings`() {
        val shift = Fixtures.shift()
        val csv = CsvCodec.encode(shift, listOf(Fixtures.patient(name = "X")))
            .replace("\n", "\r\n")
        val parsed = CsvCodec.decode(csv)
        assertEquals(1, parsed.patients.size)
    }
}