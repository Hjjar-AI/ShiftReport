package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.data.model.Doctor

/** Session-local import preview assumptions; never persisted or exported. */
data class DoctorImportPlan(
    val imported: List<Doctor>,
    val expectedRegistry: List<Doctor>,
    val actorId: String,
    val reviewedChanges: List<Doctor>
)

/** Compare clinical/administrative fields, excluding local secrets and incidental update times. */
data class DoctorImportChanges(
    val added: List<String>, val changed: List<String>, val unchanged: List<String>, val retained: List<String>
)

fun DoctorImportPlan.changes(): DoctorImportChanges {
    val before = expectedRegistry.associateBy { it.id }
    val touched = reviewedChanges.mapTo(mutableSetOf()) { it.id }
    fun portable(doctor: Doctor) = doctor.copy(updatedAt = java.time.Instant.EPOCH,
        extraOptions = doctor.options.portable)
    return DoctorImportChanges(
        reviewedChanges.filter { !it.isDeleted && before[it.id] == null }.map { it.fullName },
        reviewedChanges.filter { !it.isDeleted && before[it.id]?.let { old -> portable(old) != portable(it) } == true }.map { it.fullName },
        reviewedChanges.filter { !it.isDeleted && before[it.id]?.let { old -> portable(old) == portable(it) } == true }.map { it.fullName },
        expectedRegistry.filter { !it.isDeleted && it.id !in touched }.map { it.fullName }
    )
}
