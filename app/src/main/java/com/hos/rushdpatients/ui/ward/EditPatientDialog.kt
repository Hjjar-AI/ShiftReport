package com.hos.rushdpatients.ui.ward

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient

@Composable
fun EditPatientDialog(
    patient: Patient,
    doctors: List<Doctor>,
    saving: Boolean = false,
    onConfirm: (Patient, (Patient?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var initial by remember(patient.id) { mutableStateOf(patient) }
    var baseline by remember(patient.id) { mutableStateOf(patient) }
    var rejected by remember(patient.id) { mutableStateOf<Patient?>(null) }
    var latest by remember(patient.id) { mutableStateOf<Patient?>(null) }
    var reviewing by remember(patient.id) { mutableStateOf(false) }
    PatientFormDialog(
        title = "تعديل بيانات المريض",
        initial = initial,
        savedBaseline = baseline,
        restoredDraft = null,
        doctors = doctors,
        saving = saving,
        reviewAction = {
            if (rejected != null) {
                TextButton(onClick = { reviewing = true }, enabled = !saving) {
                    Text("مراجعة المسودة المرفوضة والنسخة المحفوظة")
                }
            }
        },
        onDraftChanged = {},
        onConfirm = { draft ->
            onConfirm(draft) { saved ->
                rejected = draft
                latest = saved
                reviewing = true
            }
        },
        onDismiss = onDismiss
    )
    if (reviewing) {
        val draft = rejected ?: return
        val saved = latest
        StaleEditReviewDialog(
            differences = saved?.let { patientDifferences(draft, it, doctors) }.orEmpty(),
            available = saved != null && !saved.isDeleted,
            onKeepDraft = {
                if (saved != null && !saved.isDeleted) {
                    // Preserve current persistence metadata; clinical replacements were explicitly reviewed.
                    baseline = saved
                    initial = draft.copy(
                        revision = saved.revision,
                        sortOrder = saved.sortOrder,
                        updatedAt = saved.updatedAt,
                        deletedAt = saved.deletedAt,
                        lastEditedByDoctorId = saved.lastEditedByDoctorId,
                        lastEditedByName = saved.lastEditedByName
                    )
                    reviewing = false
                }
            },
            onUseSaved = {
                if (saved != null && !saved.isDeleted) {
                    baseline = saved
                    initial = saved
                    reviewing = false
                }
            },
            onDismiss = { reviewing = false }
        )
    }
}

private fun patientDifferences(
    draft: Patient, saved: Patient, doctors: List<Doctor>
): List<Triple<String, String, String>> {
    val names = doctors.associate { it.id to it.fullName }
    fun yesNo(value: Boolean) = if (value) "نعم" else "لا"
    return buildList {
        fun <T> compare(
            label: String, value: (Patient) -> T,
            format: (T) -> String = { it?.toString().orEmpty() }
        ) {
            val draftValue = value(draft)
            val savedValue = value(saved)
            if (draftValue != savedValue) add(Triple(label, format(draftValue), format(savedValue)))
        }
        compare("رقم القبول الحالي", { it.admittanceNumber })
        compare("تاريخ الدخول", { it.admittanceDate })
        compare("الجنس", { it.gender }) { if (it == Gender.MALE) "ذكر" else "أنثى" }
        compare("الاسم", { it.name })
        compare("تاريخ الميلاد", { it.birthDate })
        compare("وجود مرافق", { it.hasCompanion }, ::yesNo)
        compare("نوع التشخيص", { it.diagnosisType }) { it.arabicLabel }
        compare("التشخيص", { it.initialDiagnosis })
        compare("الخطة العلاجية", { it.treatmentPlan })
        compare("المتابعة", { it.followUp })
        compare("التحاليل", { it.labs })
        compare("المقيم", { it.responsibleResidentId }) { id -> id?.let { names[it] ?: it }.orEmpty() }
        compare("الاختصاصي", { it.responsibleSpecialistId }) { id -> id?.let { names[it] ?: it }.orEmpty() }
        compare("الشارات", { it.badges }) { badges ->
            badges.joinToString("\n") { "${it.text} (${it.priority?.arabicLabel ?: "دون مستوى"})" }
        }
        compare("الأولوية", { it.isPriority }, ::yesNo)
    }
}
