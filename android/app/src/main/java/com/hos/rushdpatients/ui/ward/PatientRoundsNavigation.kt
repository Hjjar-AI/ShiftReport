package com.hos.rushdpatients.ui.ward

internal data class PatientRoundsNavigation(
    val index: Int,
    val total: Int,
    val previousId: String?,
    val nextId: String?
)

/** Consume the displayed filtered order; never reorder or mutate clinical records here. */
internal fun patientRoundsNavigation(orderedIds: List<String>, selectedId: String): PatientRoundsNavigation? {
    val index = orderedIds.indexOf(selectedId)
    if (index < 0) return null
    return PatientRoundsNavigation(index, orderedIds.size,
        orderedIds.getOrNull(index - 1), orderedIds.getOrNull(index + 1))
}
