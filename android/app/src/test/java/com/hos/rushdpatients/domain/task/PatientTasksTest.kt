package com.hos.rushdpatients.domain.task

import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PatientTasksTest {
    @Test fun `legacy deadlines and ownership do not affect checklist counts`() {
        val tasks = listOf(Fixtures.task("due").copy(dueAtEpochMillis = 100),
            Fixtures.task("future").copy(dueAtEpochMillis = 101, ownerDoctorId = "doc"),
            Fixtures.task("done", done = true).copy(dueAtEpochMillis = 1))
        assertEquals(TaskCounts(2), PatientTasks.counts(tasks))
    }
    @Test fun `display ordering preserves legacy ownership without changing records`() {
        val pending = Fixtures.task().copy(ownerDoctorId = "doc", ownerName = "أرز")
        val done = Fixtures.task("done", true)
        val input = listOf(done, pending)
        assertEquals(listOf(pending, done), PatientTasks.orderedForDisplay(input))
        assertEquals("doc", input.last().ownerDoctorId)
        assertEquals(listOf(done, pending), input)
    }
    @Test fun `display keeps checklist order with completed last without mutation`() {
        val input = listOf(Fixtures.task("done", true), Fixtures.task("undated"),
            Fixtures.task("low").copy(priority = TaskPriority.LOW, dueAtEpochMillis = 100),
            Fixtures.task("high").copy(priority = TaskPriority.HIGH, dueAtEpochMillis = 100),
            Fixtures.task("earlier").copy(dueAtEpochMillis = 50))
        assertEquals(listOf("undated", "low", "high", "earlier", "done"),
            PatientTasks.orderedForDisplay(input).map { it.id })
        assertEquals("done", input.first().id)
    }
    @Test fun `ward filters require both team selections and exclude completed tasks`() {
        val patient = Fixtures.patient(responsibleResidentId = "r", responsibleSpecialistId = "s")
            .copy(tasks = listOf(Fixtures.task(), Fixtures.task("done", true)))
        assertEquals(1, PatientTasks.wardPending(listOf(patient), "s", "r").size)
        assertTrue(PatientTasks.wardPending(listOf(patient), "other", "r").isEmpty())
        assertEquals(1, PatientTasks.wardPending(listOf(patient), null, null).size)
    }
    @Test fun `validation rejects blank identities descriptions and duplicates while retaining legacy compatibility`() {
        val task = Fixtures.task()
        for (invalid in listOf(task.copy(id = " "), task.copy(description = " "))) {
            assertThrows(IllegalArgumentException::class.java) { PatientTasks.requireValid(listOf(invalid)) }
        }
        assertThrows(IllegalArgumentException::class.java) { PatientTasks.requireValid(listOf(task, task)) }
        PatientTasks.requireValid(listOf(task.copy(done = true)))
        PatientTasks.requireValid(listOf(task.copy(completedByDoctorId = "legacy")))
    }
    @Test fun `compact report omits routine and unassigned labels while retaining clinical task`() {
        val task = Fixtures.task().copy(description = "قياس تجريبي", ownerDoctorId = "doc", ownerName = "اسم محفوظ", priority = TaskPriority.HIGH)
        val summary = PatientTasks.summary(listOf(task))
        assertTrue(summary.contains(task.description))
        assertTrue(summary.contains("☐"))
        assertFalse(summary.contains("غير معيّنة"))
        assertFalse(summary.contains("أولوية"))
        assertFalse(summary.contains("اسم محفوظ"))
    }
}
