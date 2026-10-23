package com.hos.rushdpatients.domain.task

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientTasksTest {
    @Test fun `overdue is a subset of pending including exact deadline`() {
        val tasks = listOf(Fixtures.task("due").copy(dueAtEpochMillis = 100),
            Fixtures.task("future").copy(dueAtEpochMillis = 101, ownerDoctorId = "doc"),
            Fixtures.task("done", done = true).copy(dueAtEpochMillis = 1))
        assertEquals(TaskCounts(2, 1, 1), PatientTasks.counts(tasks, 100))
        assertFalse(PatientTasks.overdue(tasks.last(), 100))
    }
    @Test fun `rollover drops completed tasks and optionally clears both owner fields`() {
        val pending = Fixtures.task().copy(ownerDoctorId = "doc", ownerName = "أرز")
        val input = listOf(pending, Fixtures.task("done", true))
        assertEquals(listOf(pending), PatientTasks.carryForward(input, false))
        assertEquals(listOf(pending.copy(ownerDoctorId = null, ownerName = null)),
            PatientTasks.carryForward(input, true))
        assertEquals("doc", input.first().ownerDoctorId)
    }
    @Test fun `display orders deadline before priority and completed last without mutation`() {
        val input = listOf(Fixtures.task("done", true), Fixtures.task("undated"),
            Fixtures.task("low").copy(priority = TaskPriority.LOW, dueAtEpochMillis = 100),
            Fixtures.task("high").copy(priority = TaskPriority.HIGH, dueAtEpochMillis = 100),
            Fixtures.task("earlier").copy(dueAtEpochMillis = 50))
        assertEquals(listOf("earlier", "high", "low", "undated", "done"),
            PatientTasks.orderedForDisplay(input, 100).map { it.id })
        assertEquals("done", input.first().id)
    }
    @Test fun `ward filters require both team selections and exclude completed tasks`() {
        val patient = Fixtures.patient(responsibleResidentId = "r", responsibleSpecialistId = "s")
            .copy(tasks = listOf(Fixtures.task(), Fixtures.task("done", true)))
        assertEquals(1, PatientTasks.wardPending(listOf(patient), "s", "r", 100).size)
        assertTrue(PatientTasks.wardPending(listOf(patient), "other", "r", 100).isEmpty())
        assertEquals(1, PatientTasks.wardPending(listOf(patient), null, null, 100).size)
    }
    @Test fun `validation rejects malformed task identities deadlines and completion records`() {
        val task = Fixtures.task()
        val invalid = listOf(task.copy(id = " "), task.copy(description = " "),
            task.copy(ownerDoctorId = ""), task.copy(dueAtEpochMillis = 0),
            task.copy(dueShiftDate = "2026-01-01"),
            task.copy(dueAtEpochMillis = 100, dueShiftDate = "bad"),
            task.copy(done = true), task.copy(completedByDoctorId = "doc"))
        invalid.forEach { value ->
            assertThrows(value.toString(), IllegalArgumentException::class.java) {
                PatientTasks.requireValid(listOf(value))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { PatientTasks.requireValid(listOf(task, task)) }
        PatientTasks.requireValid(listOf(task, Fixtures.task("done", true)))
        PatientTasks.requireValid(listOf(task.copy(done = true)), requireCompletion = false)
    }
    @Test fun `compact report omits routine and unassigned labels while retaining clinical task`() {
        val task = Fixtures.task().copy(description = "قياس تجريبي")
        val summary = PatientTasks.summary(listOf(task), includeUnassigned = false,
            includeRoutinePriority = false, nowEpochMillis = 100)
        assertTrue(summary.contains(task.description))
        assertFalse(summary.contains("غير معيّنة"))
        assertFalse(summary.contains("أولوية"))
        val owned = task.copy(ownerDoctorId = "doc", ownerName = "اسم محفوظ", priority = TaskPriority.HIGH)
        assertTrue(PatientTasks.metadata(owned, mapOf("doc" to "أرز")).contains("أرز"))
        assertTrue(PatientTasks.metadata(owned).contains("اسم محفوظ"))
    }
}
