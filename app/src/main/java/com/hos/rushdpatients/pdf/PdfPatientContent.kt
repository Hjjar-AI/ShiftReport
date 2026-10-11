package com.hos.rushdpatients.pdf

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.task.PatientTasks

/** Clinical formatting shared by the table and linked patient cards. */
internal object PdfPatientContent {
    fun buildPatientCell(patient: Patient): String {
        val sb = StringBuilder()
        if (patient.isPriority) sb.append("[أولوية] ")
        sb.append(patient.name)
        patient.age?.let { age ->
            sb.append('\n')
            patient.birthDate?.let {
                sb.append(it.year)
                sb.append(" • ")
            }
            sb.append(age).append(' ').append(PdfStrings.YEAR_SUFFIX)
        }
        sb.append(" • ")
        sb.append(if (patient.gender == com.hos.rushdpatients.data.model.Gender.MALE) "ذكر" else "أنثى")
        if (patient.hasCompanion) {
            sb.append('\n')
            sb.append('(')
            sb.append(PdfStrings.ESCORT_LABEL)
            sb.append(')')
        }
        return sb.toString()
    }

    fun buildAdmitCell(patient: Patient): String {
        val sb = StringBuilder()
        if (patient.admittanceNumber.isNotBlank()) sb.append(patient.admittanceNumber)
        sb.append('\n')
        sb.append(PdfStrings.SEPARATOR)
        sb.append('\n')
        patient.admittanceDate?.let { date ->
            sb.append("يوم ")
            sb.append(patient.admittanceDays ?: 0)
            sb.append('\n')
            sb.append(date)
        }
        return sb.toString()
    }

    fun buildSupervisorResidentCell(
        patient: Patient,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>
    ): String {
        return listOfNotNull(
            patient.responsibleSpecialistId?.let(supervisorNames::get)?.takeIf(String::isNotBlank),
            patient.responsibleResidentId?.let(residentNames::get)?.takeIf(String::isNotBlank)
        ).joinToString("\n${PdfStrings.SEPARATOR}\n")
    }

    fun buildNotesCell(patient: Patient, doctorNames: Map<String, String>): String {
        val sb = StringBuilder()
        if (patient.followUp.isNotBlank()) {
            sb.append("المتابعة: ")
            sb.append(patient.followUp)
        }
        if (patient.tasks.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("المهام: ").append(PatientTasks.summary(patient.tasks, doctorNames, includeUnassigned = false, includeRoutinePriority = false))
        }
        if (patient.labs.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("التحاليل: ")
            sb.append(patient.labs)
        }
        return sb.toString()
    }

    fun buildDiagnosisCell(patient: Patient): String = buildString {
        append(patient.diagnosisType.arabicLabel)
        if (patient.badges.isNotEmpty()) {
            append('\n')
            append(patient.badges.joinToString("\n") { badge ->
                val level = badge.priority?.takeIf { it == com.hos.rushdpatients.data.model.PatientBadgePriority.HIGH }
                    ?.let { " · ${it.arabicLabel}" }.orEmpty()
                "⚠ ${badge.text}$level"
            })
        }
        if (patient.initialDiagnosis.isNotBlank()) {
            append('\n')
            append(patient.initialDiagnosis)
        }
    }
}
