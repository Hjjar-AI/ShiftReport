package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor

/** Display grouping only; caller still controls clinical-role eligibility. */
internal fun doctorChoiceGroups(uiText: UiStrings, doctors: List<Doctor>, registry: List<Doctor> = doctors): Map<String, List<Doctor>> {
    val names = registry.associate { it.id to it.fullName }
    return doctors.filterNot { it.isDeleted }.sortedWith(
        compareBy<Doctor> { it.clinicalRole != ClinicalRole.SUPERVISOR }
            .thenBy { it.teamSupervisorId?.let(names::get).orEmpty() }.thenBy { it.fullName }
    ).groupBy { doctor ->
        when {
            doctor.clinicalRole == ClinicalRole.SUPERVISOR -> uiText(R.string.ui_doctor_choice_groups_001)
            doctor.teamSupervisorId == null -> uiText(R.string.ui_doctor_choice_groups_002)
            else -> uiText(R.string.ui_doctor_choice_groups_004, doctor.teamSupervisorId?.let(names::get) ?: uiText(R.string.ui_doctor_choice_groups_003))
        }
    }
}
