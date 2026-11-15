package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.task.PatientTasks

/** Clinical formatting shared by the table and linked patient cards. */
internal object PdfPatientContent {
    fun buildPatientCell(patient: Patient, includeName: Boolean = true, reportDate: java.time.LocalDate = com.hos.rushdpatients.util.ShiftDate.current()): String {
        if (!includeName) return buildList {
            val gender = if (patient.gender == com.hos.rushdpatients.data.model.Gender.MALE) "ذكر" else "أنثى"
            add(gender + if (patient.hasCompanion) " (مع مرافق)" else "")
            patient.ageOn(reportDate)?.let { age ->
                add(listOfNotNull(patient.birthDate?.year?.toString(), "$age ${PdfStrings.YEAR_SUFFIX}").joinToString(" • "))
            }
            if (patient.isPriority) add("[أولوية]")
        }.joinToString("\n")
        val sb = StringBuilder()
        if (patient.isPriority) sb.append("[أولوية] ")
        if (includeName) sb.append(patient.displayName)
        patient.ageOn(reportDate)?.let { age ->
            if (sb.isNotEmpty()) sb.append('\n')
            patient.birthDate?.let {
                sb.append(it.year)
                sb.append(" • ")
            }
            sb.append(age).append(' ').append(PdfStrings.YEAR_SUFFIX)
        }
        if (sb.isNotEmpty()) sb.append(" • ")
        sb.append(if (patient.gender == com.hos.rushdpatients.data.model.Gender.MALE) "ذكر" else "أنثى")
        if (patient.hasCompanion) {
            sb.append('\n')
            sb.append('(')
            sb.append(PdfStrings.ESCORT_LABEL)
            sb.append(')')
        }
        return sb.toString()
    }

    fun buildAdmitCell(
        patient: Patient,
        reportDate: java.time.LocalDate = com.hos.rushdpatients.util.ShiftDate.current(),
        includeNumber: Boolean = true,
        dayLabel: String = "يوم"
    ): String {
        val sb = StringBuilder()
        if (includeNumber) {
            if (patient.admittanceNumber.isNotBlank()) sb.append(patient.admittanceNumber)
            sb.append('\n')
            sb.append(PdfStrings.SEPARATOR)
            sb.append('\n')
        }
        patient.admittanceDate?.let { date ->
            sb.append(dayLabel).append(' ')
            sb.append(patient.admittanceDaysOn(reportDate) ?: 0)
            sb.append('\n')
            sb.append(date)
        }
        return sb.toString()
    }

    fun buildSupervisorResidentCell(
        patient: Patient,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>,
        roleSeparator: String = "\n${PdfStrings.SEPARATOR}\n"
    ): String {
        return listOfNotNull(
            patient.responsibleSpecialistId?.let(supervisorNames::get)?.takeIf(String::isNotBlank),
            patient.responsibleResidentId?.let(residentNames::get)?.takeIf(String::isNotBlank)
        ).joinToString(roleSeparator)
    }

    fun buildNotesCell(patient: Patient): String {
        val sb = StringBuilder()
        if (patient.followUp.isNotBlank()) {
            sb.append("المتابعة: ")
            sb.append(patient.followUp)
        }
        if (patient.tasks.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("المهام: ").append(PatientTasks.summary(patient.tasks))
        }
        if (patient.labText.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("التحاليل: ")
            sb.append(patient.labText)
        }
        return sb.toString()
    }

    fun buildDiagnosisCell(patient: Patient, includeType: Boolean = true): String = buildString {
        if (includeType) append(patient.diagnosisType.arabicLabel)
        if (patient.badges.isNotEmpty()) {
            if (isNotEmpty()) append('\n')
            append(patient.badges.joinToString("\n") { badge ->
                val level = badge.priority?.takeIf { it == com.hos.rushdpatients.data.model.PatientBadgePriority.HIGH }
                    ?.let { " • ${it.arabicLabel}" }.orEmpty()
                "⚠ ${badge.text}$level"
            })
        }
        if (patient.initialDiagnosis.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(patient.initialDiagnosis)
        }
    }
}
