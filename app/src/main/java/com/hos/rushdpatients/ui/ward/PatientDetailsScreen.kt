package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.window.layout.FoldingFeature
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatientDetailsScreen(
    patient: Patient,
    doctorNames: Map<String, String>,
    activity: List<AuditEntryEntity> = emptyList(),
    readOnly: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit = {},
    onPriorityChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    embedded: Boolean = false,
    foldingFeature: FoldingFeature? = null
) {
    val haptics = LocalHapticFeedback.current
    var section by rememberSaveable(patient.id, key = "patient-detail-section") {
        mutableStateOf(PatientDetailSection.OVERVIEW)
    }
    val detailScroll = rememberSaveable(patient.id, key = "patient-detail-scroll", saver = ScrollState.Saver) { ScrollState(0) }
    val content: @Composable () -> Unit = {
        Scaffold(
            modifier = (if (embedded) Modifier else Modifier.safeDrawingPadding()).imePadding(),
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    patient.name,
                                    modifier = Modifier.semantics { heading() },
                                    maxLines = 2
                                )
                                Text(
                                    "رقم القبول الحالي ${patient.admittanceNumber} · ${freshnessText(patient.updatedAt)}",
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
                    Surface(tonalElevation = 2.dp) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PatientDetailSection.entries.forEach { option ->
                                val count = when (option) {
                                    PatientDetailSection.TASKS -> patient.followUp.lineSequence().count { it.isNotBlank() }
                                    PatientDetailSection.WARNINGS -> patient.badges.size
                                    PatientDetailSection.HISTORY -> activity.size.coerceAtLeast(1)
                                    else -> 0
                                }
                                FilterChip(
                                    selected = section == option,
                                    onClick = { section = option },
                                    label = {
                                        Text(option.arabicLabel + if (count > 0) " $count" else "")
                                    }
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!readOnly) {
                    Surface(tonalElevation = 3.dp) {
                        Button(
                            onClick = onEdit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text("تعديل بيانات المريض", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(detailScroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (section) {
                    PatientDetailSection.OVERVIEW,
                    PatientDetailSection.CLINICAL -> PatientCard(
                        patient = patient,
                        expanded = section == PatientDetailSection.CLINICAL,
                        twoColumn = true,
                        doctorNames = doctorNames,
                        readOnly = true,
                        showViewControls = false,
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
                        title = "شارة المريض",
                        content = patient.badges.joinToString("\n") { badge ->
                            val level = badge.priority?.let { " · أولوية ${it.arabicLabel}" }.orEmpty()
                            "• ${badge.text}$level"
                        }.ifBlank { "لا توجد شارات" }
                    )
                    PatientDetailSection.HISTORY -> DetailTextSection(
                        title = "السجل الزمني المهم",
                        content = meaningfulTimeline(patient, activity)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCopy) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Text("نسخ", Modifier.padding(start = 6.dp))
                    }
                    if (!readOnly) {
                        Button(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPriorityChange(!patient.isPriority)
                        }) {
                            Icon(
                                if (patient.isPriority) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null
                            )
                            Text(
                                if (patient.isPriority) "إلغاء الأولوية" else "تحديد أولوية",
                                Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    if (embedded) {
        Surface(Modifier.fillMaxSize()) { content() }
    } else {
        Dialog(onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize()) {
                WardAdaptivePanes(enableSplit = false, foldingFeature = foldingFeature,
                    listFraction = .42f, onListFractionChange = {}, modifier = Modifier.fillMaxSize(), primaryTitle = "ملف المريض",
                    primary = { content() })
            }
        }
    }
}

private fun meaningfulTimeline(patient: Patient, activity: List<AuditEntryEntity>): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val matching = activity.filter { it.patientId == patient.id }
        .sortedByDescending { it.atEpochMillis }
        .take(20)
    if (matching.isNotEmpty()) {
        return matching.joinToString("\n\n") { entry ->
            buildString {
                append(formatter.format(Instant.ofEpochMilli(entry.atEpochMillis).atZone(ZoneId.systemDefault())))
                append(" · ").append(entry.actorName ?: "النظام")
                append("\n").append(entry.detail.ifBlank { entry.action })
                entry.beforeValue?.takeIf(String::isNotBlank)?.let { append("\nقبل: ").append(it) }
                entry.afterValue?.takeIf(String::isNotBlank)?.let { append("\nبعد: ").append(it) }
            }
        }
    }
    return buildString {
        append("آخر تعديل محلي · ")
        append(patient.lastEditedByName ?: "غير محدد")
        append("\n")
        append(formatter.format(patient.updatedAt.atZone(ZoneId.systemDefault())))
        if (patient.isPriority) append("\nالمريض محدد كأولوية")
        if (patient.badges.isNotEmpty()) {
            append("\nشارات: ")
            append(patient.badges.joinToString("، ") { it.text })
        }
    }
}

private fun freshnessText(updatedAt: Instant): String {
    val minutes = java.time.Duration.between(updatedAt, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "محدّث الآن"
        minutes < 60 -> "منذ $minutes دقيقة"
        minutes < 1440 -> "منذ ${minutes / 60} ساعة"
        else -> "منذ ${minutes / 1440} يوم"
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
    WARNINGS("الشارة"),
    HISTORY("السجل")
}
