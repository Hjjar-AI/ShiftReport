package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.model.Patient

@Composable
internal fun EditPatientDialog(
    patient: Patient,
    doctors: List<Doctor>,
    saving: Boolean = false,
    onConfirm: (Patient, (Patient?) -> Unit) -> Unit,
    onConfirmAndNext: ((Patient, (Patient?) -> Unit) -> Unit)? = null,
    roundsNavigation: PatientRoundsNavigation? = null,
    saveAndNextEnabled: Boolean = false,
    onSaveAndNextChanged: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    var initial by remember(patient.id) { mutableStateOf(patient) }
    var baseline by remember(patient.id) { mutableStateOf(patient) }
    var rejected by remember(patient.id) { mutableStateOf<Patient?>(null) }
    var latest by remember(patient.id) { mutableStateOf<Patient?>(null) }
    var reviewing by remember(patient.id) { mutableStateOf(false) }
    fun submit(draft: Patient, advance: Boolean) {
        val onStale: (Patient?) -> Unit = { saved ->
            rejected = draft
            latest = saved
            reviewing = true
        }
        if (advance) onConfirmAndNext?.invoke(draft, onStale) else onConfirm(draft, onStale)
    }
    PatientFormDialog(
        title = "تعديل بيانات المريض",
        initial = initial,
        savedBaseline = baseline,
        restoredDraft = null,
        doctors = doctors,
        saving = saving,
        reviewAction = {
            roundsNavigation?.let { navigation ->
                Text("المريض ${com.hos.rushdpatients.ui.components.uiNumber(navigation.index + 1)} من ${com.hos.rushdpatients.ui.components.uiNumber(navigation.total)}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                com.hos.rushdpatients.ui.components.AppToggleRow(
                    title = "حفظ والتالي أثناء الجولة", checked = saveAndNextEnabled,
                    onCheckedChange = onSaveAndNextChanged, enabled = !saving)
                if (navigation.nextId == null) Text("آخر مريض في القائمة الحالية؛ الحفظ ينهي الجولة.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            if (rejected != null) {
                AppTextButton(onClick = { reviewing = true }, enabled = !saving) {
                    Text("مراجعة المسودة المرفوضة والنسخة المحفوظة")
                }
            }
        },
        onDraftChanged = {},
        onConfirm = { submit(it, false) },
        onConfirmAndNext = if (onConfirmAndNext == null) null else { draft -> submit(draft, true) },
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
        compare("المهام", { it.tasks }) { PatientTasks.summary(it, names) }
        compare("المقيم", { it.responsibleResidentId }) { id -> id?.let { names[it] ?: it }.orEmpty() }
        compare("المشرف", { it.responsibleSpecialistId }) { id -> id?.let { names[it] ?: it }.orEmpty() }
        compare("الشارات", { it.badges }) { badges ->
            badges.joinToString("\n") { "${it.text} (${it.priority?.arabicLabel ?: "دون مستوى"})" }
        }
        compare("الأولوية", { it.isPriority }, ::yesNo)
    }
}
