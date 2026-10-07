package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.data.model.Doctor
import java.time.LocalDate

data class ReportSummary(
    val patientCount: Int,
    val psychoCount: Int,
    val escortCount: Int,
    val shiftDate: LocalDate,
    val doctors: List<Doctor>
) {
    val hasDoctors: Boolean get() = doctors.isNotEmpty()
}