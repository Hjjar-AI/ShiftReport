package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.Patient

/** Display values are distinct from the complete, explicit set of available actions. */
data class PatientCardState(
    val patient: Patient, val expanded: Boolean = false, val twoColumn: Boolean = false,
    val doctorNames: Map<String, String> = emptyMap(), val readOnly: Boolean = false,
    val selected: Boolean = false, val recentlyEdited: Boolean = false, val pinned: Boolean = false,
    val showViewControls: Boolean = true, val showIdentity: Boolean = true,
    val compact: Boolean = false, val animateExpansion: Boolean = false,
    val taskNowEpochMillis: Long = System.currentTimeMillis()
)

class PatientCardActions(
    val onOpen: () -> Unit, val onLongPress: () -> Unit, val onEdit: () -> Unit,
    val onDelete: () -> Unit, val onCopy: () -> Unit, val onPriorityChange: (Boolean) -> Unit,
    val onPinToggle: () -> Unit, val onExpandToggle: () -> Unit
) {
    companion object {
        /** Explicitly inert for read-only previews and the details renderer. */
        val None = PatientCardActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}
