package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender

object ReportRosterLabel {
    fun forDoctors(doctors: List<Doctor>): String = when {
        doctors.size == 1 -> if (doctors.single().gender == Gender.FEMALE) "طبيبة المناوبة" else "طبيب المناوبة"
        doctors.isNotEmpty() && doctors.all { it.gender == Gender.FEMALE } -> "طبيبات المناوبة"
        else -> "أطباء المناوبة"
    }
}
