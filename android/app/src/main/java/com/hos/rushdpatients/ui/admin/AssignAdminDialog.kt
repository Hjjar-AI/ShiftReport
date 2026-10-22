package com.hos.rushdpatients.ui.admin

import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppChoiceRow
import androidx.compose.foundation.selection.selectableGroup
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.data.model.Doctor

@Composable
fun AssignAdminDialog(
    candidates: List<Doctor>,
    onConfirm: (Doctor) -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false
) {
    val uiText = rememberUiStrings()
    var selected by remember { mutableStateOf<Doctor?>(null) }

    AppAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(uiText(R.string.ui_assign_admin_dialog_001)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .appDialogBody()
                    .verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                if (candidates.isEmpty()) {
                    Text(uiText(R.string.ui_assign_admin_dialog_002))
                } else {
                    candidates.forEach { doctor ->
                        val isSelected = selected?.id == doctor.id
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(UiPadding.compact)
                            ) {
                                AppChoiceRow(label = doctor.fullName, selected = isSelected,
                                    onClick = { selected = doctor }, enabled = !saving)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                onClick = { selected?.let { onConfirm(it) } },
                enabled = selected != null && !saving
            ) { Text(uiText(R.string.ui_assign_admin_dialog_003)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, enabled = !saving) { Text(uiText(R.string.ui_cancel)) }
        }
    )
}

@Composable
fun RemoveAdminDialog(
    admins: List<Doctor>,
    onConfirm: (Doctor) -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false
) {
    val uiText = rememberUiStrings()
    var selected by remember { mutableStateOf<Doctor?>(null) }

    AppAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(uiText(R.string.ui_assign_admin_dialog_005)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .appDialogBody()
                    .verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                if (admins.isEmpty()) {
                    Text(uiText(R.string.ui_assign_admin_dialog_006))
                } else {
                    admins.forEach { doctor ->
                        val isSelected = selected?.id == doctor.id
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(UiPadding.compact)
                            ) {
                                AppChoiceRow(label = uiText(R.string.ui_assign_admin_dialog_007, doctor.fullName, doctor.rank), selected = isSelected,
                                    onClick = { selected = doctor }, enabled = !saving)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                onClick = { selected?.let { onConfirm(it) } },
                enabled = selected != null && !saving
            ) { Text(uiText(R.string.ui_assign_admin_dialog_008)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, enabled = !saving) { Text(uiText(R.string.ui_cancel)) }
        }
    )
}
