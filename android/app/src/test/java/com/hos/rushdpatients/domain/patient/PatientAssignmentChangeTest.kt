package com.hos.rushdpatients.domain.patient

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.util.ShiftDate
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class PatientAssignmentChangeTest {
    private val day = LocalDate.of(2026, 10, 27)
    private val old = Fixtures.patient(responsibleSpecialistId = "cedar").copy(assignmentChangedOn = day.minusDays(1))

    @Test fun `new assigned and unassigned patients are marked on creation day`() {
        for (supervisor in listOf(null, "cedar")) {
            val patient = Fixtures.patient(responsibleSpecialistId = supervisor)
            assertEquals(day, PatientAssignmentChange.onSave(patient, null, day).assignmentChangedOn)
        }
    }
    @Test fun `assign remove and A to B to A each mark actual save day`() {
        var current = old
        for ((index, supervisor) in listOf("oak", "cedar", null, "cedar").withIndex()) {
            val saveDay = day.plusDays(index.toLong())
            current = PatientAssignmentChange.onSave(current.copy(responsibleSpecialistId = supervisor), current, saveDay)
            assertEquals(saveDay, current.assignmentChangedOn)
        }
    }
    @Test fun `clinical resident task and ordering edits preserve prior assignment date`() {
        val edited = old.copy(labs = "نتيجة", responsibleResidentId = "resident", sortOrder = 99,
            tasks = listOf(Fixtures.task(done = true)), assignmentChangedOn = day.plusDays(10))
        assertEquals(old.assignmentChangedOn, PatientAssignmentChange.onSave(edited, old, day).assignmentChangedOn)
    }
    @Test fun `stale editor with unchanged supervisor preserves newer stored assignment date`() {
        val latest = old.copy(assignmentChangedOn = day)
        assertEquals(day, PatientAssignmentChange.onSave(old.copy(labs = "later clinical edit"), latest, day.plusDays(1)).assignmentChangedOn)
    }
    @Test fun `unknown legacy assignment date is never restamped by clinical save`() {
        val legacy = old.copy(assignmentChangedOn = null)
        assertNull(PatientAssignmentChange.onSave(legacy.copy(labs = "نتيجة"), legacy, day).assignmentChangedOn)
        assertFalse(PatientAssignmentChange.highlighted(legacy, day))
    }
    @Test fun `0830 changes marking day while an already reviewed day stays stable`() {
        val before = ShiftDate.current(LocalDateTime.of(2026, 10, 27, 8, 29, 59))
        val after = ShiftDate.current(LocalDateTime.of(2026, 10, 27, 8, 30))
        val captured = PatientAssignmentChange.onSave(old.copy(responsibleSpecialistId = "oak"), old, before)
        assertTrue(PatientAssignmentChange.highlighted(captured, before))
        assertFalse(PatientAssignmentChange.highlighted(captured, after))
        val later = PatientAssignmentChange.onSave(old.copy(responsibleSpecialistId = "oak"), old, after)
        assertEquals(after, later.assignmentChangedOn)
        assertEquals(before, captured.assignmentChangedOn)
    }
}
