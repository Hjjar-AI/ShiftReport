package com.hos.rushdpatients.ui.ward

import kotlinx.serialization.Serializable

@Serializable
data class PatientBadgeDraft(
    val text: String,
    val priority: String? = null
)

@Serializable
data class PatientDraft(
    val admittanceNumber: String = "",
    val admittanceDate: String? = null,
    val gender: String = "M",
    val name: String = "",
    val birthYear: String = "",
    val age: String = "",
    val hasCompanion: Boolean = false,
    val diagnosisType: String = "PSY",
    val initialDiagnosis: String = "",
    val treatmentItems: List<String> = emptyList(),
    val treatmentDraft: String = "",
    val followUpItems: List<String> = emptyList(),
    val followUpDraft: String = "",
    val labItems: List<String> = emptyList(),
    val labDraft: String = "",
    val residentId: String? = null,
    val specialistId: String? = null,
    val badges: List<PatientBadgeDraft> = emptyList(),
    val badgeDraftText: String = "",
    val badgeDraftPriority: String? = null,
    val isPriority: Boolean = false
) {
    val isEmpty: Boolean
        get() = admittanceNumber.isBlank() && name.isBlank() && initialDiagnosis.isBlank() &&
            treatmentItems.isEmpty() && treatmentDraft.isBlank() && followUpItems.isEmpty() &&
            followUpDraft.isBlank() && labItems.isEmpty() && labDraft.isBlank() && badges.isEmpty() &&
            badgeDraftText.isBlank()
}
