package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.Fixtures
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PatientLabGroupsTest {
    @Test fun `dated and undated panels have an unambiguous readable order`() {
        assertEquals("تعداد الدم • 2024-02-29\nHb = 12.5 g/dL\nWBC = 6000 /µL\n\nالكلى\nNa = 140 mmol/L\nK = 4.1 mmol/L",
            PatientLabGroups.text(Fixtures.labGroups()))
    }
    @Test fun `unknown date and blank title do not invent clinical metadata`() {
        val group = PatientLabGroup("plain", results = "  Hb=12\r\n\nآخر سطر  ")
        assertEquals("", PatientLabGroups.heading(group))
        assertEquals(group.results, PatientLabGroups.text(listOf(group)))
        assertEquals(group.results, PatientLabGroups.text(PatientLabGroups.fromText(group.results, "plain")))
    }
    @Test fun `empty groups preserve draft structure without fake report content`() {
        val groups = listOf(PatientLabGroup("unfinished"), PatientLabGroup("date-only", date = "2026-01-01"))
        assertEquals(groups, PatientLabGroups.decode(PatientLabGroups.encode(groups)))
        assertEquals("2026-01-01", PatientLabGroups.text(groups))
        assertTrue(PatientLabGroups.fromText("", "none").isEmpty())
        assertEquals("   ", PatientLabGroups.text(PatientLabGroups.fromText("   ", "spaces")))
    }
    @Test fun `duplicate or blank group identities fail instead of losing a panel`() {
        listOf(listOf(PatientLabGroup("")), listOf(PatientLabGroup("  ")),
            listOf(PatientLabGroup("same"), PatientLabGroup("same", results = "different"))).forEach { groups ->
            assertThrows(IllegalArgumentException::class.java) { PatientLabGroups.encode(groups) }
        }
    }
    @Test fun `date boundaries accept real leap days and reject ambiguous or impossible dates`() {
        listOf("0001-01-01", "9999-12-31", "2024-02-29", "2000-02-29").forEach { date ->
            val group = PatientLabGroup("date", date = date)
            assertEquals(listOf(group), PatientLabGroups.decode(PatientLabGroups.encode(listOf(group))))
        }
        listOf("0000-01-01", "1900-02-29", "2025-02-29", "2026-04-31", "2026-13-01",
            "2026-1-01", "2026-01-01T00:00:00", " 2026-01-01", "١٩٩٠-٠١-٠١").forEach { date ->
            assertThrows("Invalid $date", RuntimeException::class.java) {
                PatientLabGroups.encode(listOf(PatientLabGroup("date", date = date)))
            }
        }
    }
    @Test fun `malformed json types missing identities and unknown fields fail explicitly`() {
        listOf("null", "{}", "[null]", "[1]", "[{\"title\":\"CBC\"}]",
            "[{\"id\":\"g\",\"results\":false}]", "[{\"id\":\"g\",\"unexpected\":1}]").forEach { raw ->
            assertThrows(raw, RuntimeException::class.java) { PatientLabGroups.decode(raw) }
        }
    }
    @Test fun `independent unicode and delimiter mixtures survive repeated codec round trips`() {
        val random = Random(875102)
        val fragments = listOf("شَاي", "☕", "💊", "\n", "\r\n", "\t", "\"", ",", "\\", "\u200f", "Na=140")
        repeat(80) { run ->
            fun text() = (0 until random.nextInt(1, 25)).joinToString("") { fragments[random.nextInt(fragments.size)] }
            val groups = (0 until random.nextInt(1, 9)).map { PatientLabGroup("$run-$it", text(), results = text()) }
            assertEquals(groups, PatientLabGroups.decode(PatientLabGroups.encode(groups)))
        }
    }
    @Test fun `patient validator rejects invalid group data but leaves existing fields intact`() {
        val patient = Fixtures.patientWithLabs()
        assertEquals(PatientValidationResult.Valid, PatientValidator.validate(patient))
        val broken = patient.copy(labGroups = patient.labGroups + patient.labGroups.first())
        val invalid = PatientValidator.validate(broken) as PatientValidationResult.Invalid
        assertEquals(listOf(PatientValidationError.LAB_GROUPS_INVALID), invalid.errors)
        assertEquals(patient.labs, broken.labs)
        assertEquals(patient.labGroups, Fixtures.patientWithLabs().labGroups)
    }
}
