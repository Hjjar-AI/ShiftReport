package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hos.rushdpatients.data.model.Patient
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatientDetailsScreen(
    patient: Patient,
    doctorNames: Map<String, String>,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var section by rememberSaveable(patient.id) {
        mutableStateOf(PatientDetailSection.OVERVIEW)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.safeDrawingPadding(),
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    patient.name,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text(
                                    "ملف المريض · رقم الدخول ${patient.admittanceNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "إغلاق ملف المريض")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PatientDetailSection.entries.forEach { option ->
                            FilterChip(
                                selected = section == option,
                                onClick = { section = option },
                                label = { Text(option.arabicLabel) }
                            )
                        }
                    }
                    when (section) {
                        PatientDetailSection.OVERVIEW,
                        PatientDetailSection.CLINICAL -> PatientCard(
                            patient = patient,
                            expanded = section == PatientDetailSection.CLINICAL,
                            twoColumn = true,
                            doctorNames = doctorNames,
                            readOnly = true,
                            onClick = {},
                            onDelete = {}
                        )
                        PatientDetailSection.TASKS -> DetailTextSection(
                            title = "مهام ومتابعة المناوبة",
                            content = patient.followUp.lineSequence()
                                .filter { it.isNotBlank() }
                                .joinToString("\n")
                                .ifBlank { "لا توجد مهام أو متابعة مسجلة" }
                        )
                        PatientDetailSection.WARNINGS -> DetailTextSection(
                            title = "التحذيرات الفعالة",
                            content = patient.warningFlags.sortedBy { it.ordinal }
                                .joinToString("\n") { flag ->
                                    val detail = patient.warningDetails[flag].orEmpty()
                                    "• ${flag.arabicLabel}${detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
                                }
                                .ifBlank { "لا توجد تحذيرات فعالة" }
                        )
                        PatientDetailSection.HISTORY -> DetailTextSection(
                            title = "آخر تحديث محلي",
                            content = buildString {
                                append(patient.lastEditedByName ?: "غير محدد")
                                append("\n")
                                append(
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                        .format(patient.updatedAt.atZone(ZoneId.systemDefault()))
                                )
                            }
                        )
                    }
                    if (!readOnly) {
                        Button(
                            onClick = onEdit,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text("تعديل بيانات المريض", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTextSection(title: String, content: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(content, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private enum class PatientDetailSection(val arabicLabel: String) {
    OVERVIEW("نظرة عامة"),
    CLINICAL("سريري"),
    TASKS("المهام"),
    WARNINGS("التحذيرات"),
    HISTORY("السجل")
}
