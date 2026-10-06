package com.hos.rushdpatients.domain.doctor

import com.hos.rushdpatients.data.model.Doctor

/** Session-local import preview assumptions; never persisted or exported. */
data class DoctorImportPlan(
    val imported: List<Doctor>,
    val expectedRegistry: List<Doctor>,
    val actorId: String,
    val reviewedChanges: List<Doctor>
)
