package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import java.time.Instant

@Composable
fun AddPatientDialog(
    doctors: List<Doctor>,
    draft: PatientDraft? = null,
    saving: Boolean = false,
    position: String = "",
    positionLimit: Int? = null,
    onPositionChange: (String) -> Unit = {},
    onDraftChanged: (PatientDraft) -> Unit = {},
    onConfirm: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    var insertionExpanded by rememberSaveable { mutableStateOf(position.isNotBlank()) }
    PatientFormDialog(
        title = "إضافة مريض",
        initial = null,
        restoredDraft = draft,
        doctors = doctors,
        saving = saving,
        entryOptions = {
            if (positionLimit != null) Column(Modifier.fillMaxWidth()) {
                AppSectionHeader(
                    title = "موضع الإدراج — " + if (position.isBlank()) "اختياري" else uiNumber(position),
                    expanded = insertionExpanded, onToggle = { insertionExpanded = !insertionExpanded })
                if (insertionExpanded) {
                    com.hos.rushdpatients.ui.components.AppTextField(
                        value = position, onValueChange = onPositionChange,
                        label = { Text("الموضع ضمن القائمة: ${uiNumber(1)}–${uiNumber(positionLimit)}") },
                        supportingText = { Text("اتركه فارغاً للإضافة المعتادة؛ اختيار موضع يمسح الترتيب التلقائي ويحافظ على الأولوية أولاً") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        enabled = !saving, singleLine = true)
                    if (position.isNotBlank()) AppTextButton(onClick = { onPositionChange("") }, enabled = !saving) {
                        Text("إلغاء الموضع والإضافة المعتادة")
                    }
                }
            }
        },
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
