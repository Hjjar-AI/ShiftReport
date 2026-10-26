package com.hos.rushdpatients.sync

import com.hos.rushdpatients.data.model.Patient

internal fun samePublishedPatients(left: List<Patient>, right: List<Patient>): Boolean =
    KnownShiftSnapshot.orderedContent(left) == KnownShiftSnapshot.orderedContent(right)
