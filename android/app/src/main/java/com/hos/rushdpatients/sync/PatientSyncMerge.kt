package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.Patient

internal fun samePatientContent(left: Patient?, right: Patient?): Boolean {
    if (left == null || right == null) return left == right
    return left.copy(updatedAt = java.time.Instant.EPOCH, revision = 0, sortOrder = 0) ==
        right.copy(updatedAt = java.time.Instant.EPOCH, revision = 0, sortOrder = 0)
}

