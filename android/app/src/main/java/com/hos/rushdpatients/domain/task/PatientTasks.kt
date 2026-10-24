package com.hos.rushdpatients.domain.task

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientTask
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Legacy counter slots remain zero for source compatibility; tasks are a plain checklist. */
data class TaskCounts(val pending: Int, val overdue: Int, val unassigned: Int)

data class WardTaskEntry(val patient: Patient, val task: PatientTask)

object PatientTasks {
    // Legacy public arguments remain source-compatible; checklist behavior ignores time/assignment/priority.
    @Suppress("UNUSED_PARAMETER")
    fun counts(tasks: List<PatientTask>, nowEpochMillis: Long): TaskCounts {
        val pending = tasks.filterNot { it.done }
        return TaskCounts(pending.size, 0, 0)
    }

    fun carryForward(tasks: List<PatientTask>, clearOwners: Boolean): List<PatientTask> =
        tasks.filterNot { it.done }.map { task ->
            if (clearOwners) task.copy(ownerDoctorId = null, ownerName = null) else task
        }

    @Suppress("UNUSED_PARAMETER")
    fun overdue(task: PatientTask, nowEpochMillis: Long): Boolean =
        false // Imported legacy deadlines do not affect checklist behavior.

    /** Display only: never reorder the stored task list or change completion/deadline data. */
    @Suppress("UNUSED_PARAMETER")
    fun orderedForDisplay(tasks: List<PatientTask>, nowEpochMillis: Long): List<PatientTask> =
        tasks.sortedWith(displayComparator())

    @Suppress("UNUSED_PARAMETER")
    fun wardPending(patients: List<Patient>, supervisorId: String?, residentId: String?, nowEpochMillis: Long): List<WardTaskEntry> {
        val comparator = displayComparator()
        return patients.filter { (supervisorId == null || it.responsibleSpecialistId == supervisorId) &&
            (residentId == null || it.responsibleResidentId == residentId) }
            .flatMap { patient -> patient.tasks.filterNot { it.done }.map { WardTaskEntry(patient, it) } }
            .sortedWith { a, b -> comparator.compare(a.task, b.task) }
    }

    private fun displayComparator(): Comparator<PatientTask> = compareBy<PatientTask> { it.done }

    @Suppress("UNUSED_PARAMETER")
    fun statusLabel(task: PatientTask, nowEpochMillis: Long): String = when {
        task.done -> "مكتملة"
        else -> "معلقة"
    }

    fun requireValid(tasks: List<PatientTask>, requireCompletion: Boolean = true) {
        require(tasks.map { it.id }.distinct().size == tasks.size) { "توجد معرّفات مهام مكررة" }
        tasks.forEach { task ->
            require(task.id.isNotBlank() && task.description.isNotBlank()) { "وصف المهمة مطلوب" }
            if (requireCompletion && task.done) {
                require(!task.completedByDoctorId.isNullOrBlank() && !task.completedByName.isNullOrBlank() &&
                    (task.completedAtEpochMillis ?: 0) > 0) { "سجل إتمام المهمة غير مكتمل" }
            }
            if (!task.done) require(task.completedByDoctorId == null && task.completedByName == null &&
                task.completedAtEpochMillis == null) { "المهمة المعلقة تحمل سجل إتمام غير صالح" }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun summary(
        tasks: List<PatientTask>,
        names: Map<String, String> = emptyMap(),
        includeUnassigned: Boolean = true,
        includeRoutinePriority: Boolean = true,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): String = orderedForDisplay(tasks, nowEpochMillis).joinToString("\n") { task ->
        (if (task.done) "✓ " else "☐ ") + statusLabel(task, nowEpochMillis) + ": " + task.description +
            metadataSuffix(task)
    }

    /** Completion text only; legacy presentation arguments remain compatible. */
    @Suppress("UNUSED_PARAMETER")
    fun metadata(
        task: PatientTask,
        names: Map<String, String> = emptyMap(),
        includeUnassigned: Boolean = true,
        includeRoutinePriority: Boolean = true
    ): String = metadataSuffix(task).removePrefix(" · ")

    private fun metadataSuffix(task: PatientTask): String = buildString {
        if (task.done) {
            append(" · ").append(task.completedByName?.let { "أتمها $it" } ?: "إتمام بانتظار الحفظ")
            task.completedAtEpochMillis?.let { at ->
                append(" · ").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())))
            }
        }
    }
}
