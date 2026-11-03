package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Gender

object ReportRosterLabel {
    fun forDoctors(doctors: List<Doctor>): String = when {
        doctors.size == 1 -> if (doctors.single().gender == Gender.FEMALE) "طبيبة المناوبة" else "طبيب المناوبة"
        doctors.isNotEmpty() && doctors.all { it.gender == Gender.FEMALE } -> "طبيبات المناوبة"
        else -> "أطباء المناوبة"
    }

    /** Flat alphabetical residents first, with an explicitly labelled supervisor line last. */
    fun linesForDoctors(doctors: List<Doctor>, nameSeparator: String): List<String> {
        val (supervisors, residents) = doctors.sortedBy { it.fullName }
            .partition { it.clinicalRole == ClinicalRole.SUPERVISOR }
        return buildList {
            if (residents.isNotEmpty()) add("${forDoctors(residents)}: " +
                residents.joinToString(nameSeparator) { it.fullName })
            if (supervisors.isNotEmpty()) add("${forSupervisors(supervisors)}: " +
                supervisors.joinToString(nameSeparator) { it.fullName })
            if (isEmpty()) add("${forDoctors(emptyList())}: ")
        }
    }

    private fun forSupervisors(doctors: List<Doctor>): String = when {
        doctors.size == 1 -> if (doctors.single().gender == Gender.FEMALE) "مشرفة المناوبة" else "مشرف المناوبة"
        doctors.all { it.gender == Gender.FEMALE } -> "مشرفات المناوبة"
        else -> "مشرفو المناوبة"
    }
}
