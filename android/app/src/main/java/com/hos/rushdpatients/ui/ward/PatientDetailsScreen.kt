package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.AppActionLabel
import com.hos.rushdpatients.ui.components.AppClinicalSection
import com.hos.rushdpatients.ui.theme.UiElevation
import com.hos.rushdpatients.ui.components.uiDateTime
import com.hos.rushdpatients.ui.components.relativeFreshness
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.domain.task.PatientTasks
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.window.layout.FoldingFeature
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.db.entity.AuditEntryEntity
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PatientDetailsScreen(
    patient: Patient,
    doctorNames: Map<String, String>,
    activity: List<AuditEntryEntity> = emptyList(),
    readOnly: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit = {},
    onPriorityChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    embedded: Boolean = false,
    foldingFeature: FoldingFeature? = null,
    roundsNavigation: PatientRoundsNavigation? = null,
    navigationEnabled: Boolean = true,
    onNavigateToPatient: (String) -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    var section by rememberSaveable(patient.id, key = "patient-detail-section") {
        mutableStateOf(PatientDetailSection.CLINICAL)
    }
    val detailScroll = rememberSaveable(patient.id, key = "patient-detail-scroll", saver = ScrollState.Saver) { ScrollState(0) }
    val content: @Composable () -> Unit = {
        Scaffold(
            modifier = (if (embedded) Modifier else Modifier.safeDrawingPadding()).imePadding(),
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            PatientIdentityHeading(patient, showFreshness = true,
                                nameStyle = MaterialTheme.typography.titleLarge)
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "إغلاق ملف المريض")
                            }
                        }
                    )
                    roundsNavigation?.let { navigation ->
                        PatientRoundsBar(navigation, enabled = navigationEnabled, onNavigate = onNavigateToPatient)
                    }
                    Surface(tonalElevation = UiElevation.navigation) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(UiPadding.content),
                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
                        ) {
                            PatientDetailSection.entries.forEach { option ->
                                val count = when (option) {
                                    PatientDetailSection.TASKS -> patient.tasks.count { !it.done }
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
                    Surface(tonalElevation = UiElevation.actionBar) {
                        AppButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(UiPadding.content)
                        ) {
                            AppActionLabel(UiAction.EDIT)
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
                    .padding(UiSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                when (section) {
                    PatientDetailSection.CLINICAL -> PatientCard(
                        patient = patient,
                        expanded = true,
                        twoColumn = true,
                        doctorNames = doctorNames,
                        readOnly = true,
                        showViewControls = false,
                        showIdentity = false,
                        onClick = {},
                        onDelete = {}
                    )
                    PatientDetailSection.TASKS -> DetailTextSection(
                        title = "مهام المريض",
                        content = PatientTasks.summary(patient.tasks, doctorNames)
                            .ifBlank { "لا توجد مهام مسجلة" }
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    AppOutlinedButton(onClick = onCopy) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Text("نسخ", Modifier.padding(start = UiSpacing.small))
                    }
                    if (!readOnly) {
                        AppOutlinedButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPriorityChange(!patient.isPriority)
                        }) {
                            Icon(
                                if (patient.isPriority) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null
                            )
                            Text(
                                if (patient.isPriority) "إلغاء الأولوية" else "تحديد أولوية",
                                Modifier.padding(start = UiSpacing.small)
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
    val matching = activity.filter { it.patientId == patient.id }
        .sortedByDescending { it.atEpochMillis }
        .take(20)
    if (matching.isNotEmpty()) {
        return matching.joinToString("\n\n") { entry ->
            buildString {
                append(uiDateTime(Instant.ofEpochMilli(entry.atEpochMillis)))
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
        append(uiDateTime(patient.updatedAt))
        if (patient.isPriority) append("\nالمريض محدد كأولوية")
        if (patient.badges.isNotEmpty()) {
            append("\nشارات: ")
            append(patient.badges.joinToString("، ") { it.text })
        }
    }
}


@Composable
private fun DetailTextSection(title: String, content: String) {
    com.hos.rushdpatients.ui.components.AppCard {
        AppClinicalSection(title, content, modifier = Modifier.padding(UiPadding.content), emptyText = "لا توجد بيانات مسجلة")
    }
}

private enum class PatientDetailSection(val arabicLabel: String) {
    CLINICAL("الملف السريري"),
    TASKS("المهام"),
    WARNINGS("الشارة"),
    HISTORY("السجل")
}
