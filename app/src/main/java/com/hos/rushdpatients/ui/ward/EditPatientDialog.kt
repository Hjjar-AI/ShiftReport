package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.Composable
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient

@Composable
fun EditPatientDialog(
    patient: Patient,
    doctors: List<Doctor>,
    saving: Boolean = false,
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    PatientFormDialog(
        title = "تعديل بيانات المريض",
        initial = patient,
        restoredDraft = null,
        doctors = doctors,
        saving = saving,
        onDraftChanged = {},
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
