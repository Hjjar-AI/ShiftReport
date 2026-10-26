package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppSectionHeader
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.AppDialogDismissAction
import com.hos.rushdpatients.ui.components.AppDialogConfirmAction
import com.hos.rushdpatients.ui.components.AppDialogHeader
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppCheckRow
import com.hos.rushdpatients.ui.components.AppSearchField
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ShiftDoctorPicker(
    allDoctors: List<Doctor>,
    initialSelected: List<String>,
    saving: Boolean = false,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    var selected by remember { mutableStateOf(initialSelected.toList()) }
    var query by remember { mutableStateOf("") }
    val activeDoctors = allDoctors.filterNot { it.isDeleted }
    val matchingDoctors = activeDoctors.filter { ArabicSearchNormalizer.matches(query, it.fullName) }

    var confirmDiscard by remember { mutableStateOf(false) }
    val requestDismiss: () -> Unit = {
        if (!saving) { if (selected.toSet() != initialSelected.toSet()) confirmDiscard = true else onDismiss() }
    }
    if (!confirmDiscard) WardFormDialog(
        onDismissRequest = requestDismiss,
        title = { AppDialogHeader(uiText(R.string.ui_shift_doctor_picker_001)) },
        text = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                Text(
                    uiText(R.string.ui_shift_doctor_picker_002, uiNumber(selected.size)),
                    style = MaterialTheme.typography.bodySmall
                )
                AppSearchField(query = query, onQueryChange = { query = it },
                    label = uiText(R.string.ui_shift_doctor_picker_003), enabled = !saving)

                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                ) {
                    if (matchingDoctors.isEmpty()) {
                        EmptyState(title = if (activeDoctors.isEmpty()) uiText(R.string.ui_shift_doctor_picker_005) else uiText(R.string.ui_shift_doctor_picker_006),
                            actionLabel = if (query.isBlank()) null else uiText(R.string.ui_shift_doctor_picker_007), onAction = { query = "" })
                    }
                    doctorChoiceGroups(uiText, matchingDoctors, activeDoctors).forEach { (team, members) ->
                        AppSectionHeader(team)
                        members.forEach { doctor ->
                            AppCheckRow(label = doctor.fullName, checked = doctor.id in selected, enabled = !saving,
                                onCheckedChange = { checked ->
                                    selected = if (checked) (selected + doctor.id).distinct() else selected - doctor.id
                                })
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppDialogConfirmAction(text = UiAction.SAVE.label(uiText), loading = saving,
                onClick = {
                    val draft = selected.toList()
                    onConfirm(draft)
                },
                enabled = selected.isNotEmpty() && !saving
            )
        },
        dismissButton = {
            AppDialogDismissAction(onClick = requestDismiss, enabled = !saving)
        }
    )
    if (confirmDiscard) ConfirmDialog(title = uiText(R.string.ui_patient_form_dialog_039),
        message = uiText(R.string.ui_shift_doctor_picker_008), confirmText = uiText(R.string.ui_patient_task_editor_029),
        dismissText = uiText(R.string.ui_shift_doctor_picker_009), destructive = true,
        onConfirm = { confirmDiscard = false; onDismiss() }, onDismiss = { confirmDiscard = false })
}
