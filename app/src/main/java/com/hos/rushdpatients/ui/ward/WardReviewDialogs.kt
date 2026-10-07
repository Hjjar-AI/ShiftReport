package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.sync.ConflictChoice
import com.hos.rushdpatients.sync.PatientFieldConflict

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RolloverReviewDialog(
    patients: List<Patient>,
    decisions: Map<String, RolloverDecision>,
    onDecision: (String, RolloverDecision) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ترحيل مرضى المناوبة السابقة") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(patients, key = { it.id }) { patient ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(patient.name)
                            Text(patient.followUp, style = MaterialTheme.typography.bodySmall)
                            val pendingTasks = patient.tasks.count { !it.done }
                            if (pendingTasks > 0) Text("ستُرحّل $pendingTasks مهمة معلقة مع مواعيدها؛ المهام المكتملة تبقى في المناوبة السابقة.",
                                style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RolloverDecision.entries.forEach { decision ->
                                    FilterChip(
                                        selected = decisions[patient.id] == decision,
                                        onClick = {
                                            onDecision(patient.id, decision)
                                        },
                                        label = { Text(decision.arabicLabel) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                val count = decisions.values.count {
                    it in setOf(RolloverDecision.CONTINUE, RolloverDecision.CONTINUE_AND_EDIT, RolloverDecision.REASSIGN)
                }
                Text("تنفيذ القرارات ($count مستمر)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("تخطي") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PatientConflictDialog(
    conflicts: List<PatientFieldConflict>,
    choices: Map<String, ConflictChoice>,
    resolving: Boolean,
    onChoice: (String, ConflictChoice) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حل تعارضات المزامنة") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conflicts, key = { it.key }) { conflict ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(conflict.patientName, style = MaterialTheme.typography.titleSmall)
                            Text(conflict.fieldLabel, style = MaterialTheme.typography.labelLarge)
                            Text("هذا الجهاز: ${conflict.localValue}")
                            Text("النسخة المنشورة: ${conflict.remoteValue}")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = choices[conflict.key] == ConflictChoice.LOCAL,
                                    onClick = { onChoice(conflict.key, ConflictChoice.LOCAL) },
                                    label = { Text("استخدام المحلي") }
                                )
                                FilterChip(
                                    selected = choices[conflict.key] == ConflictChoice.REMOTE,
                                    onClick = { onChoice(conflict.key, ConflictChoice.REMOTE) },
                                    label = { Text("استخدام المنشور") }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !resolving &&
                    conflicts.all { it.key in choices }
            ) { Text(if (resolving) "جارٍ الدمج…" else "دمج ونشر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سلة المحذوفات") },
        text = {
            if (patients.isEmpty()) {
                Text("لا يوجد مرضى محذوفون في هذه المناوبة")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                TextButton(onClick = { onRestore(patient) }) {
                                    Text("استعادة")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار مناوبة محفوظة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("المناوبات الموجودة في آخر ملف CSV تم تنزيله:")
                options.forEachIndexed { index, option ->
                    TextButton(
                        onClick = { onSelect(option.shiftId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${index + 1}. ${option.date} — ${option.patientCount} مريض",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
