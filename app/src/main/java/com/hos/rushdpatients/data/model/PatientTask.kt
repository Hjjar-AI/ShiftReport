package com.hos.rushdpatients.data.model

import com.hos.rushdpatients.domain.task.PatientTasks
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
enum class TaskPriority(val arabicLabel: String) {
    LOW("منخفضة"), NORMAL("عادية"), HIGH("عالية")
}

@Serializable
data class PatientTask(
    val id: String,
    val description: String,
    val ownerDoctorId: String? = null,
    val ownerName: String? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val dueAtEpochMillis: Long? = null,
    val dueShiftDate: String? = null,
    val done: Boolean = false,
    val completedByDoctorId: String? = null,
    val completedByName: String? = null,
    val completedAtEpochMillis: Long? = null
)

/** Shared representation for Room, patient CSV bundles, and saved form drafts. */
object PatientTaskCodec {
    private val json = Json { encodeDefaults = true }
    fun encode(tasks: List<PatientTask>): String = json.encodeToString(tasks)
    fun decode(value: String): List<PatientTask> =
        if (value.isBlank()) emptyList() else json.decodeFromString<List<PatientTask>>(value).also {
            PatientTasks.requireValid(it)
        }
}
