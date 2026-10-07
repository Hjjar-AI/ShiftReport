package com.hos.rushdpatients.domain.report

import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.network.telegram.Markdown
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextReportBuilder @Inject constructor() {

    private val dateFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("ar"))

    /**
     * Builds the report as a list of ready-to-send MarkdownV2 messages.
     */
    fun build(
        shift: Shift,
        patients: List<Patient>,
        doctors: List<Doctor>,
        summary: ReportSummary,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>
    ): List<String> {
        val title = buildTitle(shift, doctors)
        val patientBlocks = patients.map {
            buildPatientBlock(it, residentNames, supervisorNames)
        }
        val summaryBlock = buildSummary(summary)
        return ReportChunking.chunk(title, patientBlocks, summaryBlock)
    }

    fun buildTitle(shift: Shift, doctors: List<Doctor>): String {
        val dateText = Markdown.escape(shift.date.format(dateFmt))
        val doctorsMarkdown = doctors.joinToString(" \\+ ") { doctor ->
            val label = Markdown.escape(doctor.fullName)
            val id = doctor.telegramId
            if (id != null) Markdown.link(label, "tg://user?id=$id") else label
        }
        return buildString {
            append('*')
            append(Markdown.escape("تاريخ المناوبة: "))
            append(dateText)
            append('\n')
            append(Markdown.escape("تقرير أطباء المناوبة: "))
            append(doctorsMarkdown)
            append('*')
        }
    }

    fun buildPatientBlock(
        patient: Patient,
        residentNames: Map<String, String>,
        supervisorNames: Map<String, String>
    ): String = buildString {
        val header = buildString {
            append(patient.sortOrder)
            append(". ")
            append(patient.name)
            if (patient.hasCompanion) append(" (مرافق)")
        }
        append(Markdown.bold(header))

        append('\n')
        append(Markdown.escape(
            "الجنس: ${if (patient.gender == com.hos.rushdpatients.data.model.Gender.MALE) "ذكر" else "أنثى"}"
        ))
        patient.birthDate?.let { date ->
            append('\n')
            append(Markdown.escape("سنة الميلاد: ${date.year} (${patient.age} سنة)"))
        }
        append('\n')
        append(Markdown.escape("نوع التشخيص: ${patient.diagnosisType.arabicLabel}"))
        if (patient.badges.isNotEmpty()) {
            append('\n')
            append(Markdown.bold("⚠ " + patient.badges.joinToString(" • ") { badge ->
                badge.priority?.let { "${badge.text} (${it.arabicLabel})" } ?: badge.text
            }))
        }
        if (patient.initialDiagnosis.isNotBlank()) {
            append('\n')
            append(Markdown.escape("التشخيص الأولي: ${patient.initialDiagnosis}"))
        }
        if (patient.admittanceNumber.isNotBlank()) {
            append('\n')
            append(Markdown.escape("رقم القبول الحالي: ${patient.admittanceNumber}"))
        }
        patient.admittanceDate?.let { date ->
            append('\n')
            append(Markdown.escape("تاريخ الدخول: $date"))
            append(" ")
            append(Markdown.escape("(${patient.admittanceDays} يوم)"))
        }
        val residentName = patient.responsibleResidentId
            ?.let(residentNames::get)
            ?: "مقيم غير محدد"
        append('\n')
        append(Markdown.escape("المقيم المسؤول: $residentName"))
        val specialistName = patient.responsibleSpecialistId
            ?.let(supervisorNames::get)
            ?: "اختصاصي غير محدد"
        append('\n')
        append(Markdown.escape("الاختصاصي المسؤول: $specialistName"))
        if (patient.treatmentPlan.isNotBlank()) {
            append('\n')
            append(Markdown.underline("الخطة العلاجية"))
            append(Markdown.escape(":"))
            append('\n')
            append(Markdown.escape(patient.treatmentPlan))
        }
        if (patient.followUp.isNotBlank()) {
            append('\n')
            append(Markdown.underline("المتابعة"))
            append(Markdown.escape(":"))
            append('\n')
            append(Markdown.escape(patient.followUp))
        }
        if (patient.tasks.isNotEmpty()) {
            append('\n')
            append(Markdown.underline("المهام"))
            append('\n')
            append(Markdown.escape(PatientTasks.summary(
                patient.tasks, residentNames + supervisorNames)))
        }
        if (patient.labs.isNotBlank()) {
            append('\n')
            append(Markdown.underline("التحاليل"))
            append(Markdown.escape(":"))
            append('\n')
            append(Markdown.escape(patient.labs))
        }
    }

    fun buildSummary(summary: ReportSummary): String = Markdown.bold(buildString {
        append("عدد المرضى: ${summary.patientCount}")
        if (summary.psychoCount > 0) append(" (نفسي: ${summary.psychoCount})")
        append('\n')
        append("عدد المرافقين: ${summary.escortCount}")
    })
}
