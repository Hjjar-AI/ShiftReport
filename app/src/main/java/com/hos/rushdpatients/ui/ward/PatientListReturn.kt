package com.hos.rushdpatients.ui.ward

internal data class PatientReturnTarget(val id: String, val previousRevision: Long)

/** Count the same header/group/patient items emitted by the ward grid. */
internal fun patientGridReturnIndex(
    patientId: String,
    orderedIds: List<String>,
    groups: List<Pair<String, List<String>>>,
    collapsedGroups: Set<String>
): Int? {
    if (groups.isEmpty()) return orderedIds.indexOf(patientId).takeIf { it >= 0 }?.plus(1)
    var index = 1 // The count/search/filter header.
    for ((key, ids) in groups) {
        if (ids.isEmpty()) continue
        index++ // Group header.
        if (key in collapsedGroups) continue
        val position = ids.indexOf(patientId)
        if (position >= 0) return index + position
        index += ids.size
    }
    return null
}
