package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.domain.doctor.DoctorMergeChoice
import com.hos.rushdpatients.domain.doctor.DoctorRegistryConflict

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DoctorConflictReviewDialog(
    conflicts: List<DoctorRegistryConflict>,
    saving: Boolean,
    onConfirm: (Map<String, DoctorMergeChoice>) -> Unit,
    onDismiss: () -> Unit
) {
    var choices by remember(conflicts) { mutableStateOf(emptyMap<String, DoctorMergeChoice>()) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("مراجعة دمج سجل الأطباء") },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                item {
                    Text("اختر قيمة لكل تعارض. التغييرات غير المتعارضة تُدمج تلقائياً؛ تطبيق الدمج يحفظ السجل ويحاول مزامنته.")
                }
                items(conflicts, key = { it.key }) { conflict ->
                    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                        Text(conflict.doctorName, style = MaterialTheme.typography.titleSmall)
                        Text(conflict.fieldLabel)
                        Text("الخيار المحلي: ${conflict.localValue}")
                        Text("الخيار المنشور: ${conflict.remoteValue}")
                        FilterChip(
                            selected = choices[conflict.key] == DoctorMergeChoice.LOCAL,
                            onClick = { choices = choices + (conflict.key to DoctorMergeChoice.LOCAL) },
                            enabled = !saving,
                            label = { Text("اعتماد الخيار المحلي") }
                        )
                        FilterChip(
                            selected = choices[conflict.key] == DoctorMergeChoice.REMOTE,
                            onClick = { choices = choices + (conflict.key to DoctorMergeChoice.REMOTE) },
                            enabled = !saving,
                            label = { Text("اعتماد الخيار المنشور") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppButton(onClick = { onConfirm(choices) },
                enabled = !saving && conflicts.all { it.key in choices }) {
                Text(if (saving) "جارٍ التطبيق…" else "تطبيق الدمج والمزامنة")
            }
        },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !saving) { Text("لاحقاً") } }
    )
}
