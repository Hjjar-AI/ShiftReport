package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import java.time.Instant

@Composable
fun AddPatientDialog(
    doctors: List<Doctor>,
    draft: PatientDraft? = null,
    saving: Boolean = false,
    onDraftChanged: (PatientDraft) -> Unit = {},
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    PatientFormDialog(
        title = "إضافة مريض",
        initial = null,
        restoredDraft = draft,
        doctors = doctors,
        saving = saving,
        onDraftChanged = onDraftChanged,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun CopyPatientDialog(
    source: Patient,
    doctors: List<Doctor>,
    saving: Boolean = false,
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    val copy = remember(source) {
        source.copy(
            id = java.util.UUID.randomUUID().toString(),
            admittanceNumber = "",
            admittanceDate = null,
            isPriority = false,
            tasks = emptyList(),
            lastEditedByDoctorId = null,
            lastEditedByName = null,
            sortOrder = 0,
            updatedAt = Instant.now(),
            deletedAt = null
        )
    }
    PatientFormDialog(
        title = "نسخ بيانات مريض",
        initial = copy,
        restoredDraft = null,
        doctors = doctors,
        saving = saving,
        onDraftChanged = {},
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
