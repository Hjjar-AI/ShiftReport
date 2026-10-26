package com.hos.rushdpatients.data.model

/** Retired tracking fields remain readable in old files, outside clinical equality. */
fun Patient.withoutTrackingMetadata(): Patient = copy(
    updatedAt = java.time.Instant.EPOCH, lastEditedByDoctorId = null, lastEditedByName = null,
    tasks = tasks.map { it.copy(completedByDoctorId = null, completedByName = null, completedAtEpochMillis = null) }
)
