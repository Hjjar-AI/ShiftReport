package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.model.Doctor

@Composable
fun ShiftDoctorPicker(
    allDoctors: List<Doctor>,
    initialSelected: List<String>,
    initialRevision: Long,
    max: Int = 3,
    saving: Boolean = false,
    onConfirm: (List<String>, Long, (Shift?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(initialSelected.toList()) }
    var revision by remember { mutableStateOf(initialRevision) }
    var rejected by remember { mutableStateOf<List<String>?>(null) }
    var latest by remember { mutableStateOf<Shift?>(null) }
    var reviewing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("أطباء المناوبة") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "اختر حتى $max أطباء",
                    style = MaterialTheme.typography.bodySmall
                )

                if (rejected != null) {
                    TextButton(onClick = { reviewing = true }, enabled = !saving) {
                        Text("مراجعة الاختيار المرفوض")
                    }
                }
                allDoctors.filterNot { it.isDeleted }.forEach { doctor ->
                    val isSelected = doctor.id in selected
                    val canSelect = selected.size < max || isSelected
                    Card(
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
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                enabled = canSelect && !saving,
                                onCheckedChange = { checked ->
                                    selected = if (checked) {
                                        selected + doctor.id
                                    } else {
                                        selected - doctor.id
                                    }
                                }
                            )
                            Text(
                                doctor.fullName,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val draft = selected.toList()
                    onConfirm(draft, revision) { saved ->
                        rejected = draft
                        latest = saved
                        reviewing = true
                    }
                },
                enabled = selected.isNotEmpty() && !saving
            ) { Text(if (saving) "جار الحفظ…" else "حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
        }
    )
    if (reviewing) {
        val saved = latest
        val draft = rejected.orEmpty()
        fun names(ids: List<String>) = ids.joinToString("، ") { id ->
            allDoctors.firstOrNull { it.id == id }?.fullName ?: id
        }
        StaleEditReviewDialog(
            differences = listOf(Triple("أطباء المناوبة", names(draft), names(saved?.doctorIds.orEmpty()))),
            available = saved != null,
            onKeepDraft = {
                if (saved != null) {
                    selected = draft
                    revision = saved.revision
                    reviewing = false
                }
            },
            onUseSaved = {
                if (saved != null) {
                    selected = saved.doctorIds
                    revision = saved.revision
                    reviewing = false
                }
            },
            onDismiss = { reviewing = false }
        )
    }
}
