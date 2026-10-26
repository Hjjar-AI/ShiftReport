package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
            gender = Gender.MALE,
            initialDiagnosis = "نفسي",
            hasCompanion = true,
            admittanceNumber = "A-123",
            treatmentPlan = "haloperidol 5mg",
            labs = "CBC normal",
            followUp = "متابعة أسبوعية"
        )

        val csv = CsvCodec.encode(shift, listOf(patient))
        val parsed = CsvCodec.decode(csv)

        assertEquals(1, parsed.patients.size)
        val p = parsed.patients.first()
        assertEquals(patient.id, p.id)
        assertEquals(patient.name, p.name)
        assertEquals(patient.age, p.age)
        assertEquals(patient.gender, p.gender)
        assertEquals(patient.initialDiagnosis, p.initialDiagnosis)
        assertEquals(patient.hasCompanion, p.hasCompanion)
        assertEquals(patient.admittanceNumber, p.admittanceNumber)
        assertEquals(patient.treatmentPlan, p.treatmentPlan)
        assertEquals(patient.labs, p.labs)
        assertEquals(patient.followUp, p.followUp)
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
        val patient = Fixtures.patient(followUp = "قال \"مرحباً\"")
        val csv = CsvCodec.encode(shift, listOf(patient))
        val parsed = CsvCodec.decode(csv)
        assertEquals("قال \"مرحباً\"", parsed.patients.first().followUp)
    }

    @Test
    fun `round trip preserves newlines`() {
        val shift = Fixtures.shift()
        val patient = Fixtures.patient(treatmentPlan = "line1\nline2")
        val csv = CsvCodec.encode(shift, listOf(patient))
        val parsed = CsvCodec.decode(csv)
        assertEquals("line1\nline2", parsed.patients.first().treatmentPlan)
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
    fun `missing admittance date is rejected`() {
        val csv = CsvCodec.encode(Fixtures.shift(), listOf(Fixtures.patient(admittanceDate = null)))
        assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(csv) }
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

    @Test
    fun `current clinical tasks badges audit and publication lineage round trip`() {
        val shift = Fixtures.shift(doctorIds = (1..20).map { "d$it" }).copy(revision = 7,
            snapshotId = "current", baseSnapshotId = "base", publishedByDeviceId = "device",
            sortSpecJson = com.hos.rushdpatients.domain.sort.SortSpecCodec.encode(com.hos.rushdpatients.domain.sort.SortSpec.WORKBOOK))
        val patient = Fixtures.patient().copy(revision = 9, isPriority = true,
            tasks = listOf(Fixtures.task(), Fixtures.task("done", true)),
            badges = listOf(com.hos.rushdpatients.data.model.PatientBadge("تنبيه متعدد؛")),
            lastEditedByDoctorId = "editor", lastEditedByName = "أرز")
        val parsed = CsvCodec.decode(CsvCodec.encode(shift, listOf(patient)))
        assertEquals(patient, parsed.patients.single())
        assertEquals(shift.doctorIds, parsed.doctorIds)
        assertEquals(7L, parsed.shiftRevision)
        assertEquals("current", parsed.snapshotId)
        assertEquals("base", parsed.baseSnapshotId)
        assertEquals("device", parsed.deviceId)
        assertEquals(shift.sortSpecJson, parsed.sortSpecJson)
    }

    @Test
    fun `duplicate patient ids fail the entire import`() {
        val patient = Fixtures.patient()
        val csv = CsvCodec.encode(Fixtures.shift(), listOf(patient, patient))
        assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(csv) }
    }
}
