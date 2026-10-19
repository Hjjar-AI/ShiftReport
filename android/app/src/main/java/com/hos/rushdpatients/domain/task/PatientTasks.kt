package com.hos.rushdpatients.domain.task

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pending includes overdue; the overdue count is a subset, never an additional total. */
data class TaskCounts(val pending: Int, val overdue: Int, val unassigned: Int)

data class WardTaskEntry(val patient: Patient, val task: PatientTask)

object PatientTasks {
    fun counts(tasks: List<PatientTask>, nowEpochMillis: Long): TaskCounts {
        val pending = tasks.filterNot { it.done }
        return TaskCounts(pending.size, pending.count { overdue(it, nowEpochMillis) },
            pending.count { it.ownerDoctorId == null })
    }

    fun carryForward(tasks: List<PatientTask>, clearOwners: Boolean): List<PatientTask> =
        tasks.filterNot { it.done }.map { task ->
            if (clearOwners) task.copy(ownerDoctorId = null, ownerName = null) else task
        }

    fun overdue(task: PatientTask, nowEpochMillis: Long): Boolean =
        !task.done && task.dueAtEpochMillis?.let { it <= nowEpochMillis } == true

    /** Display only: never reorder the stored task list or change completion/deadline data. */
    fun orderedForDisplay(tasks: List<PatientTask>, nowEpochMillis: Long): List<PatientTask> =
        tasks.sortedWith(displayComparator(nowEpochMillis))

    fun wardPending(patients: List<Patient>, supervisorId: String?, residentId: String?, nowEpochMillis: Long): List<WardTaskEntry> {
        val comparator = displayComparator(nowEpochMillis)
        return patients.filter { (supervisorId == null || it.responsibleSpecialistId == supervisorId) &&
            (residentId == null || it.responsibleResidentId == residentId) }
            .flatMap { patient -> patient.tasks.filterNot { it.done }.map { WardTaskEntry(patient, it) } }
            .sortedWith { a, b -> comparator.compare(a.task, b.task) }
    }

    private fun displayComparator(nowEpochMillis: Long): Comparator<PatientTask> = compareBy<PatientTask> {
            when { overdue(it, nowEpochMillis) -> 0; !it.done -> 1; else -> 2 }
        }.thenBy { if (it.done) Long.MAX_VALUE else it.dueAtEpochMillis ?: Long.MAX_VALUE }
            .thenBy { if (it.done) 0 else -it.priority.ordinal }

    fun statusLabel(task: PatientTask, nowEpochMillis: Long): String = when {
        task.done -> "مكتملة"
        overdue(task, nowEpochMillis) -> "متأخرة"
        else -> "معلقة"
    }

    fun requireValid(tasks: List<PatientTask>, requireCompletion: Boolean = true) {
        require(tasks.map { it.id }.distinct().size == tasks.size) { "توجد معرّفات مهام مكررة" }
        tasks.forEach { task ->
            require(task.id.isNotBlank() && task.description.isNotBlank()) { "وصف المهمة مطلوب" }
            require(task.ownerDoctorId == null || task.ownerDoctorId.isNotBlank()) { "مسؤول المهمة غير صالح" }
            require(task.dueAtEpochMillis == null || task.dueAtEpochMillis > 0) { "موعد المهمة غير صالح" }
            task.dueShiftDate?.let {
                require(runCatching { LocalDate.parse(it) }.isSuccess && task.dueAtEpochMillis != null) {
                    "مناوبة استحقاق المهمة غير صالحة"
                }
            }
            if (requireCompletion && task.done) {
                require(!task.completedByDoctorId.isNullOrBlank() && !task.completedByName.isNullOrBlank() &&
                    (task.completedAtEpochMillis ?: 0) > 0) { "سجل إتمام المهمة غير مكتمل" }
            }
            if (!task.done) require(task.completedByDoctorId == null && task.completedByName == null &&
                task.completedAtEpochMillis == null) { "المهمة المعلقة تحمل سجل إتمام غير صالح" }
        }
    }

    fun summary(
        tasks: List<PatientTask>,
        names: Map<String, String> = emptyMap(),
        includeUnassigned: Boolean = true,
        includeRoutinePriority: Boolean = true,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): String = orderedForDisplay(tasks, nowEpochMillis).joinToString("\n") { task ->
        (if (task.done) "✓ " else "☐ ") + statusLabel(task, nowEpochMillis) + ": " + task.description +
            metadataSuffix(task, names, includeUnassigned, includeRoutinePriority)
    }

    /** Shared doctor/deadline/priority/completion text for compact task rows and reports. */
    fun metadata(
        task: PatientTask,
        names: Map<String, String> = emptyMap(),
        includeUnassigned: Boolean = true,
        includeRoutinePriority: Boolean = true
    ): String = metadataSuffix(task, names, includeUnassigned, includeRoutinePriority).removePrefix(" · ")

    private fun metadataSuffix(
        task: PatientTask,
        names: Map<String, String>,
        includeUnassigned: Boolean,
        includeRoutinePriority: Boolean
    ): String = buildString {
        if (includeRoutinePriority || task.priority == com.hos.rushdpatients.data.model.TaskPriority.HIGH) {
            append(" · أولوية ").append(task.priority.arabicLabel)
        }
        if (includeUnassigned || task.ownerDoctorId != null) {
            append(" · ").append(task.ownerDoctorId?.let { names[it] ?: task.ownerName ?: "طبيب غير متاح" } ?: "غير معيّنة")
        }
        task.dueAtEpochMillis?.let { due ->
            append(" · الموعد ").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())))
        }
        task.dueShiftDate?.let { append(" (نهاية مناوبة ").append(it).append(')') }
        if (task.done) {
            append(" · ").append(task.completedByName?.let { "أتمها $it" } ?: "إتمام بانتظار الحفظ")
            task.completedAtEpochMillis?.let { at ->
                append(" · ").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())))
            }
        }
    }
}
