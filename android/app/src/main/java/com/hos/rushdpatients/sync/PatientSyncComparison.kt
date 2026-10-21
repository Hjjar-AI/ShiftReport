package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.Patient

internal fun samePublishedPatients(left: List<Patient>, right: List<Patient>): Boolean {
        fun Patient.withoutLocalMetadata() = copy(
            updatedAt = java.time.Instant.EPOCH,
            revision = 0
        )
        return left.associate { it.id to it.withoutLocalMetadata() } ==
            right.associate { it.id to it.withoutLocalMetadata() }
    }
