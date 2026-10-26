package com.hos.rushdpatients.domain.task

import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.data.model.PatientTaskCodec
import com.hos.rushdpatients.data.model.TaskPriority
import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

/** Deterministic combinations exercise shared rules across board and wire storage. */
class PatientTasksInvariantTest {
    private val now = 10_000L
    private fun combinations(): List<PatientTask> = buildList {
        for (done in listOf(false, true)) for (owner in listOf(null, "doc-1")) {
            for (due in listOf(null, now - 1, now, now + 1)) for (priority in TaskPriority.values()) {
                add(Fixtures.task("task-$size", done).copy(ownerDoctorId = owner,
                    dueAtEpochMillis = due, priority = priority))
            }
        }
    }

    @Test fun `count partition holds across all completion owner deadline priority combinations`() {
        val tasks = combinations()
        val counts = PatientTasks.counts(tasks)
        assertEquals(tasks.size / 2, counts.pending)
        assertEquals(TaskCounts(0), PatientTasks.counts(tasks.filter { it.done }))
    }

    @Test fun `legacy deadlines never change checklist status`() {
        combinations().forEach { task ->
            val plain = task.copy(ownerDoctorId = null, ownerName = null, dueAtEpochMillis = null, dueShiftDate = null, priority = TaskPriority.NORMAL)
            assertEquals(PatientTasks.statusLabel(plain), PatientTasks.statusLabel(task))
        }
    }

    @Test fun `display sort preserves every record is idempotent and leaves source intact`() {
        val tasks = combinations().reversed()
        val snapshot = tasks.toList()
        val sorted = PatientTasks.orderedForDisplay(tasks)
        assertEquals(snapshot, tasks)
        assertEquals(tasks.associateBy { it.id }, sorted.associateBy { it.id })
        assertEquals(sorted, PatientTasks.orderedForDisplay(sorted))
        assertTrue(sorted.dropWhile { !it.done }.all { it.done })
    }

    @Test fun `equal display keys retain patient and task input ordering`() {
        val tasks = (1..20).map { Fixtures.task("task-$it") }
        assertEquals(tasks, PatientTasks.orderedForDisplay(tasks))
        val patients = (1..5).map { Fixtures.patient(id = "p-$it").copy(tasks = tasks.take(2)) }
        val entries = PatientTasks.wardPending(patients, null, null)
        assertEquals(patients.flatMap { listOf(it.id, it.id) }, entries.map { it.patient.id })
        assertEquals(patients.flatMap { it.tasks }, entries.map { it.task })
    }

    @Test fun `display and wire round trip preserve legacy task fields`() {
        val tasks = combinations()
        val ordered = PatientTasks.orderedForDisplay(tasks)
        assertEquals(ordered, PatientTasks.orderedForDisplay(ordered))
        assertEquals(tasks.associateBy { it.id }, ordered.associateBy { it.id })
        PatientTasks.requireValid(ordered)
        assertEquals(ordered, PatientTaskCodec.decode(PatientTaskCodec.encode(ordered)))
    }

    @Test fun `full task combination survives codec without dropping completion attribution`() {
        val tasks = combinations()
        PatientTasks.requireValid(tasks)
        assertEquals(tasks, PatientTaskCodec.decode(PatientTaskCodec.encode(tasks)))
    }

    @Test fun `legacy completed attribution is optional and remains round trip compatible`() {
        val done = Fixtures.task(done = true)
        listOf(done.copy(completedByDoctorId = null), done.copy(completedByDoctorId = " "),
            done.copy(completedByName = null), done.copy(completedByName = " "),
            done.copy(completedAtEpochMillis = null), done.copy(completedAtEpochMillis = 0),
            done.copy(completedAtEpochMillis = -1)).forEach { legacy ->
            PatientTasks.requireValid(listOf(legacy))
            assertEquals(listOf(legacy), PatientTaskCodec.decode(PatientTaskCodec.encode(listOf(legacy))))
        }
    }

    @Test fun `legacy pending attribution does not alter checklist status`() {
        val pending = Fixtures.task()
        listOf(pending.copy(completedByDoctorId = "doc-1"), pending.copy(completedByName = "أرز"),
            pending.copy(completedAtEpochMillis = now)).forEach { legacy ->
            PatientTasks.requireValid(listOf(legacy))
            assertEquals(PatientTasks.statusLabel(pending), PatientTasks.statusLabel(legacy))
        }
    }

    @Test fun `legacy shift deadlines are read tolerantly and preserved`() {
        val task = Fixtures.task().copy(dueAtEpochMillis = now, dueShiftDate = "2024-02-29")
        PatientTasks.requireValid(listOf(task))
        val legacy = task.copy(dueShiftDate = "2025-02-29")
        PatientTasks.requireValid(listOf(legacy))
        assertEquals(listOf(legacy), PatientTaskCodec.decode(PatientTaskCodec.encode(listOf(legacy))))
    }

    @Test fun `ward filters cover null and independent resident supervisor combinations`() {
        val patients = listOf(null, "r1", "r2").flatMap { resident ->
            listOf(null, "s1", "s2").map { supervisor ->
                Fixtures.patient(id = "$resident-$supervisor", responsibleResidentId = resident,
                    responsibleSpecialistId = supervisor).copy(tasks = listOf(Fixtures.task(), Fixtures.task("done", true)))
            }
        }
        for (resident in listOf(null, "r1", "absent")) for (supervisor in listOf(null, "s2", "absent")) {
            val expected = patients.filter { (resident == null || it.responsibleResidentId == resident) &&
                (supervisor == null || it.responsibleSpecialistId == supervisor) }.map { it.id }
            val actual = PatientTasks.wardPending(patients, supervisor, resident)
            assertEquals(expected, actual.map { it.patient.id })
            assertTrue(actual.none { it.task.done })
        }
    }

    @Test fun `empty collections stay empty in counts summary and board`() {
        assertEquals(TaskCounts(0), PatientTasks.counts(emptyList()))
        assertEquals("", PatientTasks.summary(emptyList()))
        assertTrue(PatientTasks.wardPending(emptyList(), null, null).isEmpty())
    }

    @Test fun `checklist summary preserves multiline descriptions and omits legacy completion names`() {
        val tasks = listOf(Fixtures.task().copy(description = "قياس\nثم مراجعة", priority = TaskPriority.HIGH),
            Fixtures.task("done", true))
        val text = PatientTasks.summary(tasks)
        assertTrue(text.contains("قياس\nثم مراجعة"))
        assertFalse(text.contains("عالية"))
        assertFalse(text.contains("أتمها أرز"))
        assertFalse(text.contains("غير معيّنة"))
        assertFalse(text.contains("عادية"))
    }
}
