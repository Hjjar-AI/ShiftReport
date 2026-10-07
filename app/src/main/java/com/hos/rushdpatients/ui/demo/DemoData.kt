package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadge
import com.hos.rushdpatients.data.model.PatientBadgePriority
import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.data.model.TaskPriority
import java.time.Instant
import java.time.LocalDate

internal object DemoData {
    fun doctors(): List<Doctor> = listOf("أرز", "بلوط", "صنوبر", "زيتون", "صفصاف", "نخيل").mapIndexed { index, name ->
        Doctor(id = "demo-doctor-$index", fullName = "د. $name", firstName = name, lastName = "تجريبي",
            gender = if (index % 2 == 0) Gender.MALE else Gender.FEMALE,
            clinicalRole = if (index < 3) ClinicalRole.RESIDENT else ClinicalRole.SUPERVISOR)
    }

    fun patients(doctors: List<Doctor>): List<Patient> {
        val now = Instant.now().toEpochMilli()
        return listOf("شاي بالنعناع", "قهوة عربية", "سحلب", "كاكاو ساخن", "زنجبيل", "يانسون", "بابونج", "قرفة")
            .mapIndexed { index, name ->
                Patient(
                    id = "demo-patient-$index", name = name, admittanceNumber = (100 + index).toString(),
                    admittanceDate = LocalDate.now().minusDays(index.toLong()),
                    birthDate = LocalDate.of(1980 + index * 4, 1, 1),
                    gender = if (index % 2 == 0) Gender.FEMALE else Gender.MALE,
                    hasCompanion = index % 3 == 0,
                    diagnosisType = DiagnosisType.entries[index % DiagnosisType.entries.size],
                    initialDiagnosis = "حالة توضيحية ${index + 1} — بيانات وهمية لعرض تنسيق التقرير",
                    treatmentPlan = "بند خطة تجريبي أول\nبند خطة تجريبي ثانٍ — لا يمثل وصفة علاجية",
                    followUp = "ملاحظات متابعة توضيحية للمناوبة القادمة",
                    labs = "Hb = ${12 + index % 3} g/dL\nNa = 140 mmol/L (بيانات وهمية)",
                    responsibleResidentId = if (index == 6) null else doctors[index % 3].id,
                    responsibleSpecialistId = if (index == 6) null else doctors[3 + index % 3].id,
                    badges = when (index % 4) {
                        0 -> listOf(PatientBadge("متابعة عاجلة تجريبية", PatientBadgePriority.HIGH))
                        1 -> listOf(PatientBadge("مراجعة الخطة", PatientBadgePriority.MEDIUM))
                        2 -> listOf(PatientBadge("ملاحظة روتينية", PatientBadgePriority.LOW))
                        else -> emptyList()
                    },
                    tasks = listOf(
                        PatientTask(id = "demo-task-$index-a", description = "مراجعة نتائج تجريبية",
                            priority = if (index % 2 == 0) TaskPriority.HIGH else TaskPriority.NORMAL,
                            dueAtEpochMillis = now + if (index % 2 == 0) -3_600_000L else 7_200_000L,
                            ownerDoctorId = if (index % 2 == 0) doctors[0].id else null,
                            ownerName = if (index % 2 == 0) doctors[0].fullName else null),
                        PatientTask(id = "demo-task-$index-b", description = "تحديث ملاحظة التسليم",
                            done = index % 3 == 0,
                            completedByDoctorId = if (index % 3 == 0) doctors[0].id else null,
                            completedByName = if (index % 3 == 0) doctors[0].fullName else null,
                            completedAtEpochMillis = if (index % 3 == 0) now - 1_800_000L else null)
                    ),
                    isPriority = index == 2, sortOrder = index + 1, revision = 1,
                    lastEditedByDoctorId = doctors[0].id, lastEditedByName = doctors[0].fullName
                )
            }
    }
}
