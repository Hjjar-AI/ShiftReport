package com.hos.rushdpatients.domain.task

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientTask

data class TaskCounts(val pending: Int)
data class WardTaskEntry(val patient: Patient, val task: PatientTask)

/** Plain checklist behavior; legacy task fields remain in persistence and interchange only. */
object PatientTasks {
    fun counts(tasks: List<PatientTask>): TaskCounts = TaskCounts(tasks.count { !it.done })
    fun orderedForDisplay(tasks: List<PatientTask>): List<PatientTask> = tasks.sortedBy { it.done }
    fun wardPending(patients: List<Patient>, supervisorId: String?, residentId: String?): List<WardTaskEntry> =
        patients.filter { (supervisorId == null || it.responsibleSpecialistId == supervisorId) &&
            (residentId == null || it.responsibleResidentId == residentId) }
            .flatMap { patient -> patient.tasks.filterNot { it.done }.map { WardTaskEntry(patient, it) } }

    fun statusLabel(task: PatientTask): String = if (task.done) "مكتملة" else "معلقة"

    fun requireValid(tasks: List<PatientTask>) {
        require(tasks.map { it.id }.distinct().size == tasks.size) { "توجد معرّفات مهام مكررة" }
        tasks.forEach { task -> require(task.id.isNotBlank() && task.description.isNotBlank()) { "وصف المهمة مطلوب" } }
    }

    fun summary(tasks: List<PatientTask>): String = orderedForDisplay(tasks).joinToString("\n") { task ->
        (if (task.done) "✓ " else "☐ ") + statusLabel(task) + ": " + task.description
    }
}
