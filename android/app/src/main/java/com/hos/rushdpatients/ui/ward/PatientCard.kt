package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.AppActionLabel
import com.hos.rushdpatients.ui.components.ClinicalTextKind
import com.hos.rushdpatients.ui.components.AppClinicalSection
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiBorder
import com.hos.rushdpatients.ui.theme.UiMotion
import com.hos.rushdpatients.ui.theme.UiPatientCard
import com.hos.rushdpatients.ui.components.uiDateTime
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.relativeFreshness
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadgePriority
import com.hos.rushdpatients.ui.theme.patientBadgeColors
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.LocalClinicalColors

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
fun PatientCard(
    patient: Patient,
    expanded: Boolean,
    twoColumn: Boolean,
    doctorNames: Map<String, String>,
    readOnly: Boolean = false,
    selected: Boolean = false,
    recentlyEdited: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick,
    onEdit: () -> Unit = onClick,
    onBadgeEdit: () -> Unit = onEdit,
    onDelete: () -> Unit,
    onCopy: () -> Unit = {},
    onPriorityChange: (Boolean) -> Unit = {},
    pinned: Boolean = false,
    onPinToggle: () -> Unit = {},
    onExpandToggle: () -> Unit = {},
    showViewControls: Boolean = true,
    showIdentity: Boolean = true,
    compact: Boolean = false,
    animateExpansion: Boolean = false,
    taskNowEpochMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    val cardDensity = UiPatientCard.density(compact)
    val sectionGap = cardDensity.sectionGap
    val taskCounts = PatientTasks.counts(patient.tasks, taskNowEpochMillis)
    val clinicalColors = LocalClinicalColors.current
    val visibleBadges = if (expanded) patient.badges else patient.badges.filter { it.priority != PatientBadgePriority.LOW }
    val residentName = patient.responsibleResidentId?.let(doctorNames::get)
    val supervisorName = patient.responsibleSpecialistId?.let(doctorNames::get)

    val cardColor by animateColorAsState(
        targetValue = if (recentlyEdited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(UiMotion.highlightMillis), label = "patientReturnHighlight"
    )
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                contentDescription = buildString {
                    append(patient.name)
                    append("، رقم القبول الحالي ").append(patient.admittanceNumber)
                    append("، ").append(patient.diagnosisType.arabicLabel)
                    patient.responsibleResidentId?.let { id ->
                        doctorNames[id]?.let { append("، المقيم ").append(it) }
                    }
                    if (patient.badges.isNotEmpty()) {
                        append("، ").append(patient.badges.size).append(" شارات")
                    }
                    if (patient.tasks.isNotEmpty()) append("، مهام معلقة ${uiNumber(taskCounts.pending)}، منها متأخرة ${uiNumber(taskCounts.overdue)}")
                    if (pinned) append("، مثبت مؤقتاً")
                    append("، ").append(relativeFreshness(patient.updatedAt))
                }
                stateDescription = buildString {
                    append(if (patient.badges.isEmpty()) "دون شارات" else "شارات فعالة")
                    if (patient.isPriority) append("، أولوية")
                    append(if (expanded) "، التفاصيل موسعة" else "، التفاصيل مطوية")
                }
                customActions = buildList {
                    if (showViewControls) {
                        add(CustomAccessibilityAction(if (expanded) "طي تفاصيل المريض" else "توسيع تفاصيل المريض") { onExpandToggle(); true })
                        add(CustomAccessibilityAction(if (pinned) "إلغاء التثبيت المؤقت" else "تثبيت مؤقت") { onPinToggle(); true })
                        add(CustomAccessibilityAction("فتح تفاصيل المريض") { onLongClick(); true })
                        if (!readOnly) {
                            add(CustomAccessibilityAction("تعديل بيانات المريض") { onEdit(); true })
                            add(CustomAccessibilityAction("تعديل شارات المريض") { onBadgeEdit(); true })
                            add(CustomAccessibilityAction(if (patient.isPriority) "إلغاء الأولوية" else "تحديد أولوية") { onPriorityChange(!patient.isPriority); true })
                            add(CustomAccessibilityAction("نسخ كمريض جديد") { onCopy(); true })
                            add(CustomAccessibilityAction("نقل المريض إلى السلة مع التأكيد") { onDelete(); true })
                        }
                    }
                }
            }
            .combinedClickable(enabled = showViewControls, onClick = onClick, onLongClick = onLongClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardColor),
        border = UiBorder.stroke(selected = selected || recentlyEdited)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardDensity.padding),
            verticalArrangement = Arrangement.spacedBy(sectionGap)
        ) {
            if (showIdentity) PatientIdentityHeader(
                patient = patient,
                readOnly = readOnly,
                expanded = expanded,
                pinned = pinned,
                showViewControls = showViewControls,
                onExpandToggle = onExpandToggle,
                onPinToggle = onPinToggle,
                onPriorityChange = onPriorityChange,
                onCopy = onCopy,
                onDelete = onDelete
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                DiagnosisChip(patient.diagnosisType)
                if (expanded && patient.hasCompanion) {
                    SmallChip(text = "مرافق", container = MaterialTheme.colorScheme.surface,
                        content = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (supervisorName != null || residentName != null) {
                Text(listOfNotNull(supervisorName?.let { "مشرف: $it" }, residentName?.let { "مقيم: $it" })
                    .joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (visibleBadges.isNotEmpty() || (!expanded && patient.badges.isNotEmpty())) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)
                ) {
                    visibleBadges.forEach { badge ->
                        val badgeColors = patientBadgeColors(badge.priority)
                        SmallChip(
                            text = buildString {
                                append(badge.text)
                                badge.priority?.let { append(" · ").append(it.arabicLabel) }
                            },
                            container = badgeColors.container,
                            content = badgeColors.content
                        )
                    }
                    if (!expanded && visibleBadges.size < patient.badges.size) SmallChip(
                        "${patient.badges.size - visibleBadges.size} شارات أخرى",
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (taskCounts.pending > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                    SmallChip("${uiNumber(taskCounts.pending)} مهمة معلقة", MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    if (taskCounts.overdue > 0) SmallChip("${uiNumber(taskCounts.overdue)} متأخرة",
                        clinicalColors.urgentContainer, clinicalColors.onUrgentContainer)
                }
            }

            if (patient.initialDiagnosis.isNotBlank()) {
                AppClinicalSection("التشخيص", patient.initialDiagnosis,
                    maxLines = if (expanded) Int.MAX_VALUE else 2)
            }


            // -------- Expanded detail --------
            AnimatedVisibility(
                visible = expanded,
                enter = if (animateExpansion) expandVertically(animationSpec = tween(UiMotion.expandMillis)) else EnterTransition.None,
                exit = if (animateExpansion) shrinkVertically(animationSpec = tween(UiMotion.collapseMillis)) else ExitTransition.None
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(sectionGap)) {
                    if (patient.tasks.isNotEmpty()) AppClinicalSection("المهام",
                        PatientTasks.summary(patient.tasks, doctorNames, nowEpochMillis = taskNowEpochMillis))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (twoColumn) {
                        TwoColumnDetail(patient = patient, spacing = sectionGap)
                    } else {
                        SingleColumnDetail(patient = patient, spacing = sectionGap)
                    }
                }
            }
            if (!showIdentity) Text(PatientIdentityText.details(patient),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded && showViewControls) com.hos.rushdpatients.ui.components.AppTextButton(
                onClick = onLongClick, modifier = Modifier.fillMaxWidth()) { AppActionLabel(UiAction.FULL_RECORD) }
            if (expanded) patient.lastEditedByName?.let { editor ->
                val time = remember(patient.updatedAt) {
                    uiDateTime(patient.updatedAt, "MM-dd HH:mm")
                }
                Text(
                    text = "آخر تعديل: $editor · $time · ${relativeFreshness(patient.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------- Expanded layouts ----------------

@Composable
private fun PatientIdentityHeader(
    patient: Patient,
    readOnly: Boolean,
    expanded: Boolean,
    pinned: Boolean,
    showViewControls: Boolean,
    onExpandToggle: () -> Unit,
    onPinToggle: () -> Unit,
    onPriorityChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = MaterialTheme.shapes.small,
            modifier = Modifier.sizeIn(minWidth = UiSpacing.touchTarget, minHeight = UiSpacing.touchTarget)) {
            Box(contentAlignment = Alignment.Center) {
                Text(com.hos.rushdpatients.ui.components.uiNumber(patient.sortOrder), fontWeight = FontWeight.Bold)
            }
        }
        PatientIdentityHeading(patient, modifier = Modifier.weight(1f), pinned = pinned,
            showPersonal = expanded && !showViewControls)

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showViewControls) {
                IconButton(onClick = onExpandToggle) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "طي التفاصيل" else "توسيع التفاصيل")
                }
                PatientMenu(priority = patient.isPriority, onPriorityChange = onPriorityChange,
                    onCopy = onCopy, onDelete = onDelete, pinned = pinned, onPinToggle = onPinToggle,
                    readOnly = readOnly)
            }
        }
    }
}


@Composable
private fun SingleColumnDetail(patient: Patient, spacing: Dp) {
    val sections = listOf(Triple("الخطة العلاجية", patient.treatmentPlan, ClinicalTextKind.CLINICAL),
        Triple("المتابعة", patient.followUp, ClinicalTextKind.CLINICAL),
        Triple("التحاليل", patient.labs, ClinicalTextKind.LABS)).filter { it.second.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        sections.forEachIndexed { index, (title, content, kind) ->
            if (index > 0) Divider(color = MaterialTheme.colorScheme.outlineVariant)
            AppClinicalSection(title, content, kind = kind)
        }
    }
}

@Composable
private fun TwoColumnDetail(patient: Patient, spacing: Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < UiLayout.patientCardColumnsMinWidth) {
            SingleColumnDetail(patient, spacing)
            return@BoxWithConstraints
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
            if (patient.treatmentPlan.isNotBlank()) {
                AppClinicalSection("الخطة العلاجية", patient.treatmentPlan)
            }
        }

        Box(
            modifier = Modifier
                .width(UiBorder.normalWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            if (patient.followUp.isNotBlank()) {
                AppClinicalSection("المتابعة", patient.followUp)
            }
            if (patient.labs.isNotBlank()) {
                if (patient.followUp.isNotBlank()) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                AppClinicalSection("التحاليل", patient.labs, kind = ClinicalTextKind.LABS)
            }
            if (patient.followUp.isBlank() && patient.labs.isBlank()) {
                Text(
                    text = "لا توجد متابعة أو تحاليل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
        }
    }
}

// ---------------- Sub-composables ----------------

@Composable
private fun PatientMenu(priority: Boolean, onPriorityChange: (Boolean) -> Unit,
    onCopy: () -> Unit, onDelete: () -> Unit, pinned: Boolean, onPinToggle: () -> Unit, readOnly: Boolean) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.size(UiSpacing.touchTarget)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "خيارات المريض", modifier = Modifier.size(UiSize.iconMedium))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (pinned) "إلغاء التثبيت" else "تثبيت للعرض") },
                leadingIcon = { Icon(if (pinned) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = null) },
                onClick = { menu = false; onPinToggle() })
            if (!readOnly) {
                DropdownMenuItem(text = { Text(if (priority) "إلغاء الأولوية" else "تحديد أولوية") },
                    onClick = { menu = false; onPriorityChange(!priority) })
                DropdownMenuItem(text = { Text("نسخ كمريض جديد") }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(text = { Text("نقل إلى السلة") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun DiagnosisChip(type: DiagnosisType) {
    SmallChip(text = type.arabicLabel, container = Color.Transparent,
        content = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SmallChip(text: String, container: Color, content: Color) {
    Surface(
        color = container,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(UiPadding.compact),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            textAlign = TextAlign.Center
        )
    }
}
