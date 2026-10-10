package com.hos.rushdpatients.ui.admin

import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Doctor

@Composable
fun AssignAdminDialog(
    candidates: List<Doctor>,
    onConfirm: (Doctor) -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false
) {
    var selected by remember { mutableStateOf<Doctor?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("ترقية إلى مدير") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                if (candidates.isEmpty()) {
                    Text("لا يوجد أطباء متاحون للترقية")
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
                                    .padding(UiPadding.content)
                            ) {
                                AppTextButton(onClick = { selected = doctor }, enabled = !saving) {
                                    Text(doctor.fullName)
                                }
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
            ) { Text("ترقية") }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
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
    var selected by remember { mutableStateOf<Doctor?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("إزالة من المديرين") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                if (admins.isEmpty()) {
                    Text("لا يوجد مديرون")
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
                                    .padding(UiPadding.content)
                            ) {
                                AppTextButton(onClick = { selected = doctor }, enabled = !saving) {
                                    Text("${doctor.fullName} (رتبة ${doctor.rank})")
                                }
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
            ) { Text("إزالة") }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
        }
    )
}
