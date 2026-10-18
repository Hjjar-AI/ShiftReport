package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppCheckRow
import com.hos.rushdpatients.ui.components.AppSearchField
import com.hos.rushdpatients.ui.components.EmptyState
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ShiftDoctorPicker(
    allDoctors: List<Doctor>,
    initialSelected: List<String>,
    initialRevision: Long,
    saving: Boolean = false,
    onConfirm: (List<String>, Long, (Shift?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(initialSelected.toList()) }
    var revision by remember { mutableStateOf(initialRevision) }
    var rejected by remember { mutableStateOf<List<String>?>(null) }
    var latest by remember { mutableStateOf<Shift?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val activeDoctors = allDoctors.filterNot { it.isDeleted }
    val matchingDoctors = activeDoctors.filter { ArabicSearchNormalizer.matches(query, it.fullName) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("أطباء المناوبة") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                Text(
                    "المختارون: ${uiNumber(selected.size)} · دون حد أقصى",
                    style = MaterialTheme.typography.bodySmall
                )
                AppSearchField(query = query, onQueryChange = { query = it },
                    label = "بحث باسم الطبيب", enabled = !saving)

                if (rejected != null) {
                    AppTextButton(onClick = { reviewing = true }, enabled = !saving) {
                        Text("مراجعة الاختيار المرفوض")
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                ) {
                    if (matchingDoctors.isEmpty()) {
                        EmptyState(title = if (activeDoctors.isEmpty()) "لا يوجد أطباء متاحون" else "لا توجد أسماء مطابقة",
                            actionLabel = if (query.isBlank()) null else "مسح البحث", onAction = { query = "" })
                    }
                    matchingDoctors.forEach { doctor ->
                        val isSelected = doctor.id in selected
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            AppCheckRow(label = doctor.fullName, checked = isSelected, enabled = !saving,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + doctor.id else selected - doctor.id
                                })
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
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
            AppTextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
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
