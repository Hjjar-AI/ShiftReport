package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var keepAssignments by androidx.compose.runtime.remember(patients) { androidx.compose.runtime.mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("بدء مناوبة اليوم من السابقة") },
        text = {
            Column(modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .65f)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(sourceLabel, style = MaterialTheme.typography.bodySmall)
                Text("نسخ جميع المرضى إلى سجلات جديدة في مناوبة اليوم، مع المهام غير المكتملة ومواعيدها فقط. المهام المكتملة تبقى في السابقة. هذا مستقل عن جلب بيانات تليجرام.")
                FilterChip(selected = keepAssignments, enabled = !saving,
                    onClick = { keepAssignments = !keepAssignments },
                    label = { Text("الاحتفاظ بالأطباء المسؤولين ومالكي المهام") })
                if (!keepAssignments) Text("ستُزال مسؤوليات الأطباء ومالكو المهام من النسخة الجديدة فقط.")
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(patients, key = { it.id }) { patient ->
                        Text(patient.name + " · مهام معلقة " + uiNumber(patient.tasks.count { !it.done }))
                    }
                }
                Text("تحفظ محلياً، ثم تراجع وتنشر. لا تستبدل مناوبة تحتوي مرضى.")
            }
        },
        confirmButton = {
            AppTextButton(onClick = { onConfirm(keepAssignments) }, enabled = !saving) {
                Text(if (saving) "جارٍ النسخ…" else "نسخ الجميع (${uiNumber(patients.size)})")
            }
        },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") } }
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
                                    Text("استعادة")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) { Text("إغلاق") }
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
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Text("المناوبات الموجودة في آخر ملف CSV تم تنزيله:")
                options.forEachIndexed { index, option ->
                    AppTextButton(
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
            AppTextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
