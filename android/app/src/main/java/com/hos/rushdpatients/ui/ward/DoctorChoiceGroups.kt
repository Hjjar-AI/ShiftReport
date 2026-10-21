package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor

/** Display grouping only; caller still controls clinical-role eligibility. */
internal fun doctorChoiceGroups(doctors: List<Doctor>, registry: List<Doctor> = doctors): Map<String, List<Doctor>> {
    val names = registry.associate { it.id to it.fullName }
    return doctors.filterNot { it.isDeleted }.sortedWith(
        compareBy<Doctor> { it.clinicalRole != ClinicalRole.SUPERVISOR }
            .thenBy { it.teamSupervisorId?.let(names::get).orEmpty() }.thenBy { it.fullName }
    ).groupBy { doctor ->
        when {
            doctor.clinicalRole == ClinicalRole.SUPERVISOR -> "المشرفون"
            doctor.teamSupervisorId == null -> "المقيمون · دون فريق"
            else -> "فريق ${doctor.teamSupervisorId?.let(names::get) ?: "مشرف مرتبط"}"
        }
    }
}
