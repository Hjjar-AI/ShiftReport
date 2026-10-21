package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppActionLabel
import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.data.model.Patient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RolloverReviewDialog(
    patients: List<Patient>,
    saving: Boolean,
    sourceLabel: String,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    var keepAssignments by androidx.compose.runtime.remember(patients) { androidx.compose.runtime.mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(uiText(R.string.ui_ward_review_dialogs_001)) },
        text = {
            Column(modifier = Modifier.appDialogBody(UiDialog.reviewBodyMaxHeight)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(sourceLabel, style = MaterialTheme.typography.bodySmall)
                Text(uiText(R.string.ui_ward_review_dialogs_002))
                FilterChip(selected = keepAssignments, enabled = !saving,
                    onClick = { keepAssignments = !keepAssignments },
                    label = { Text(uiText(R.string.ui_ward_review_dialogs_003)) })
                if (!keepAssignments) Text(uiText(R.string.ui_ward_review_dialogs_004))
                LazyColumn(modifier = Modifier.appDialogBody(UiDialog.compactBodyMaxHeight)) {
                    items(patients, key = { it.id }) { patient ->
                        Text(patient.name + uiText(R.string.ui_ward_review_dialogs_005) + uiNumber(patient.tasks.count { !it.done }))
                    }
                }
                Text(uiText(R.string.ui_ward_review_dialogs_006))
            }
        },
        confirmButton = {
            AppTextButton(onClick = { onConfirm(keepAssignments) }, enabled = !saving) {
                Text(if (saving) uiText(R.string.ui_ward_review_dialogs_007) else uiText(R.string.ui_ward_review_dialogs_008, uiNumber(patients.size)))
            }
        },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !saving) { Text(uiText(R.string.ui_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatientRecycleBinDialog(
    patients: List<Patient>,
    readOnly: Boolean,
    onRestore: (Patient) -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(R.string.ui_ward_review_dialogs_009)) },
        text = {
            if (patients.isEmpty()) {
                Text(uiText(R.string.ui_ward_review_dialogs_010))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    items(patients, key = { it.id }) { patient ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(patient.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    patient.admittanceNumber,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (!readOnly) {
                                AppTextButton(onClick = { onRestore(patient) }) {
                                    AppActionLabel(UiAction.RESTORE)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) { Text(uiText(R.string.ui_close)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShiftSnapshotPickerDialog(
    options: List<ShiftSnapshotOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(R.string.ui_ward_review_dialogs_012)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Text(uiText(R.string.ui_ward_review_dialogs_013))
                options.forEachIndexed { index, option ->
                    AppTextButton(
                        onClick = { onSelect(option.shiftId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            uiText(R.string.ui_ward_review_dialogs_014, index + 1, option.date, option.patientCount),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(uiText(R.string.ui_cancel)) }
        }
    )
}
