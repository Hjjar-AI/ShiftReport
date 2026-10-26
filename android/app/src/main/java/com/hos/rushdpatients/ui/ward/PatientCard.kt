package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppMenuItem
import com.hos.rushdpatients.ui.components.AppMenu
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppIconButton
import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppChip
import com.hos.rushdpatients.ui.theme.ContainerColors
import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.AppActionLabel
import com.hos.rushdpatients.ui.components.ClinicalTextKind
import com.hos.rushdpatients.ui.components.AppClinicalSection
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiBorder
import com.hos.rushdpatients.ui.theme.UiPatientCard
import com.hos.rushdpatients.ui.components.uiDateTime
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.hos.rushdpatients.ui.components.AppDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.uiPlural
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.State
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientBadgePriority
import com.hos.rushdpatients.ui.theme.patientBadgeColors
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.theme.LocalClinicalColors

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
fun PatientCard(state: PatientCardState, actions: PatientCardActions, modifier: Modifier = Modifier) {
    val patient = state.patient
    val expanded = state.expanded
    val twoColumn = state.twoColumn
    val doctorNames = state.doctorNames
    val readOnly = state.readOnly
    val selected = state.selected
    val recentlyEdited = state.recentlyEdited
    val pinned = state.pinned
    val showViewControls = state.showViewControls
    val showIdentity = state.showIdentity
    val compact = state.compact
    val animateExpansion = state.animateExpansion
    val onClick = actions.onOpen
    val onLongClick = actions.onLongPress
    val onEdit = actions.onEdit
    val onDelete = actions.onDelete
    val onCopy = actions.onCopy
    val onPriorityChange = actions.onPriorityChange
    val onPinToggle = actions.onPinToggle
    val onExpandToggle = actions.onExpandToggle
    val uiText = rememberUiStrings()
    val dayLabel = LocalWardDayLabel.current
    val canExpand = patient.initialDiagnosis.isNotBlank() || patient.treatmentPlan.isNotBlank() ||
        patient.followUp.isNotBlank() || patient.labs.isNotBlank() || patient.tasks.isNotEmpty() ||
        patient.badges.isNotEmpty() || patient.hasCompanion
    val headerExpands = com.hos.rushdpatients.ui.theme.LocalUiPreferences.current.expandPatientHeader
    val cardDensity = UiPatientCard.density(compact)
    val sectionGap = cardDensity.sectionGap
    val taskCounts = remember(patient.tasks) { PatientTasks.counts(patient.tasks) }
    val badgesLabel = uiPlural(R.plurals.patient_badges, patient.badges.size)
    val pendingTasksLabel = uiPlural(R.plurals.patient_tasks_pending, taskCounts.pending)
    val clinicalColors = LocalClinicalColors.current
    val visibleBadges = remember(patient.badges, expanded) {
        if (expanded) patient.badges else patient.badges.filter { it.priority != PatientBadgePriority.LOW }
    }
    val statusEdge = when {
        com.hos.rushdpatients.domain.patient.PatientPredicates.urgent(patient) -> clinicalColors.urgent
        patient.badges.any { it.priority == PatientBadgePriority.MEDIUM } -> clinicalColors.warning
        taskCounts.pending > 0 -> clinicalColors.pending
        else -> null
    }
    val residentName = patient.responsibleResidentId?.let(doctorNames::get)
    val supervisorName = patient.responsibleSpecialistId?.let(doctorNames::get)

    val cardColor by animateColorAsState(
        targetValue = if (recentlyEdited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(com.hos.rushdpatients.ui.theme.LocalUiStyle.current.highlightMillis), label = "patientReturnHighlight"
    )
    val expansionMillis = if (!animateExpansion) 0 else if (expanded)
        com.hos.rushdpatients.ui.theme.LocalUiStyle.current.expandMillis
        else com.hos.rushdpatients.ui.theme.LocalUiStyle.current.collapseMillis
    // Compose 1.5 remembers the initial animateContentSize spec. Read the current
    // duration when each size animation starts, including instant bulk changes.
    val currentExpansionMillis = rememberUpdatedState(expansionMillis)
    val sizeAnimation = remember { PatientCardSizeSpec(currentExpansionMillis) }
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                contentDescription = buildString {
                    append(patient.displayName)
                    append(uiText(R.string.ui_patient_card_001)).append(patient.admittanceNumber)
                    append(uiText(R.string.ui_patient_card_002)).append(PatientIdentityText.personal(uiText, patient, dayLabel))
                    append(uiText(R.string.ui_patient_card_002)).append(patient.diagnosisType.uiLabel(uiText))
                    patient.responsibleResidentId?.let { id ->
                        doctorNames[id]?.let { append(uiText(R.string.ui_patient_card_003)).append(it) }
                    }
                    if (patient.badges.isNotEmpty()) {
                        append(uiText(R.string.ui_patient_card_002)).append(badgesLabel)
                    }
                    if (patient.tasks.isNotEmpty()) append(pendingTasksLabel)
                    if (pinned) append(uiText(R.string.ui_patient_card_005))
                }
                stateDescription = buildString {
                    append(if (patient.badges.isEmpty()) uiText(R.string.ui_patient_card_006) else uiText(R.string.ui_patient_card_007))
                    if (patient.isPriority) append(uiText(R.string.ui_patient_card_008))
                    append(if (expanded) uiText(R.string.ui_patient_card_009) else uiText(R.string.ui_patient_card_010))
                }
                customActions = buildList {
                    if (showViewControls) {
                        if (canExpand) add(CustomAccessibilityAction(if (expanded) uiText(R.string.ui_patient_card_011) else uiText(R.string.ui_patient_card_012)) { onExpandToggle(); true })
                        add(CustomAccessibilityAction(if (pinned) uiText(R.string.ui_patient_card_013) else uiText(R.string.ui_patient_card_014)) { onPinToggle(); true })
                        add(CustomAccessibilityAction(uiText(R.string.ui_patient_card_015)) { onClick(); true })
                        if (!readOnly) {
                            add(CustomAccessibilityAction(uiText(R.string.ui_edit_patient_dialog_001)) { onEdit(); true })
                            add(CustomAccessibilityAction(if (patient.isPriority) uiText(R.string.ui_patient_card_017) else uiText(R.string.ui_patient_card_018)) { onPriorityChange(!patient.isPriority); true })
                            add(CustomAccessibilityAction(uiText(R.string.ui_patient_card_019)) { onCopy(); true })
                            add(CustomAccessibilityAction(uiText(R.string.ui_patient_card_020)) { onDelete(); true })
                        }
                    }
                }
            }
            .combinedClickable(enabled = showViewControls, role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = uiText(R.string.ui_patient_card_015), onClick = onClick,
                onLongClickLabel = if (readOnly) null else uiText(R.string.ui_edit_patient_dialog_001),
                onLongClick = if (readOnly) null else onLongClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardColor),
        border = UiPatientCard.border(selected = selected || recentlyEdited)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    statusEdge?.let { color ->
                        val edge = UiSize.statusEdge.toPx()
                        drawRect(color, Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - edge else 0f, 0f),
                            Size(edge, size.height))
                    }
                }
                .animateContentSize(sizeAnimation)
                .padding(cardDensity.padding),
            verticalArrangement = Arrangement.spacedBy(sectionGap)
        ) {
            if (showIdentity) PatientIdentityHeader(
                patient = patient,
                readOnly = readOnly,
                expanded = expanded,
                pinned = pinned,
                showViewControls = showViewControls,
                canExpand = canExpand, headerExpands = headerExpands, animateExpansion = animateExpansion,
                onExpandToggle = onExpandToggle,
                onPinToggle = onPinToggle,
                onPriorityChange = onPriorityChange,
                onCopy = onCopy,
                onDelete = onDelete
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)
            ) {
                visibleBadges.forEach { badge ->
                    val badgeColors = patientBadgeColors(badge.priority)
                    AppChip(
                        text = buildString {
                            append(badge.text)
                            badge.priority?.let { append(" · ").append(it.uiLabel(uiText)) }
                        },
                        colors = badgeColors
                    )
                }
                if (!expanded && visibleBadges.size < patient.badges.size) AppChip(
                    uiPlural(R.plurals.patient_badges_more, patient.badges.size - visibleBadges.size),
                    ContainerColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                )
                DiagnosisChip(patient.diagnosisType)
                if (taskCounts.pending > 0) {
                    AppChip(uiPlural(R.plurals.patient_tasks_pending, taskCounts.pending), com.hos.rushdpatients.ui.theme.toneColors(com.hos.rushdpatients.ui.theme.Tone.PENDING))
                }
                if (expanded && patient.hasCompanion) {
                    AppChip(text = uiText(R.string.ui_patient_card_021), colors = ContainerColors(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface))
                }
            }

            if (supervisorName != null || residentName != null) {
                Text(listOfNotNull(supervisorName?.let { uiText(R.string.ui_patient_card_022, it) }, residentName?.let { uiText(R.string.ui_patient_card_023, it) })
                    .joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (patient.initialDiagnosis.isNotBlank()) {
                AppClinicalSection(uiText(R.string.ui_patient_card_024), patient.initialDiagnosis,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, showHeading = expanded)
            }


            // -------- Expanded detail --------
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(sectionGap)) {
                    AppDivider()
                    if (twoColumn) {
                        TwoColumnDetail(patient = patient, spacing = sectionGap)
                    } else {
                        SingleColumnDetail(patient = patient, spacing = sectionGap)
                    }
                    if (patient.tasks.isNotEmpty()) AppClinicalSection(uiText(R.string.ui_tasks),
                        PatientTasks.summary(patient.tasks))
                }
            }
            if (!showIdentity) Text(PatientIdentityText.details(uiText, patient, dayLabel),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded && showViewControls) FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                if (!readOnly) com.hos.rushdpatients.ui.components.AppButton(onClick = onEdit, style = com.hos.rushdpatients.ui.components.AppButtonStyle.TONAL) { Text(uiText(R.string.ui_ward_form_dialog_001)) }
                com.hos.rushdpatients.ui.components.AppTextButton(onClick = onClick) { AppActionLabel(UiAction.FULL_RECORD) }
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
    canExpand: Boolean,
    headerExpands: Boolean,
    animateExpansion: Boolean,
    onExpandToggle: () -> Unit,
    onPinToggle: () -> Unit,
    onPriorityChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val uiText = rememberUiStrings()
    val indicatorMillis = com.hos.rushdpatients.ui.theme.LocalUiStyle.current.indicatorMillis
    val targetRotation = if (expanded) 180f else 0f
    val chevronRotation by animateFloatAsState(targetRotation,
        animationSpec = if (animateExpansion && indicatorMillis > 0) tween(indicatorMillis) else snap(),
        label = "patientChevron")

    Row(
        modifier = Modifier.fillMaxWidth().then(
            if (showViewControls && headerExpands && canExpand) Modifier.heightIn(min = UiSpacing.touchTarget)
                .clickable(onClickLabel = if (expanded) uiText(R.string.ui_patient_card_027) else uiText(R.string.ui_patient_card_028),
                    role = androidx.compose.ui.semantics.Role.Button, onClick = onExpandToggle) else Modifier),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
    ) {
        PatientIdentityHeading(patient, modifier = Modifier.weight(1f), pinned = pinned,
            showPersonal = expanded && !showViewControls, showOrdinal = true, showDemographics = true)

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showViewControls) {
                if (!readOnly) AppIconButton(
                    label = if (patient.isPriority) uiText(R.string.ui_patient_card_017) else uiText(R.string.ui_patient_card_018),
                    icon = if (patient.isPriority) AppIcons.Star else AppIcons.StarBorder, onClick = { onPriorityChange(!patient.isPriority) })
                if (canExpand) AppIconButton(label = if (expanded) uiText(R.string.ui_patient_card_027) else uiText(R.string.ui_patient_card_028),
            icon = AppIcons.ExpandMore,
            iconModifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            onClick = onExpandToggle)
                PatientMenu(priority = patient.isPriority, onPriorityChange = onPriorityChange,
                    onCopy = onCopy, onDelete = onDelete, pinned = pinned, onPinToggle = onPinToggle,
                    readOnly = readOnly)
            }
        }
    }
}


@Composable
private fun SingleColumnDetail(patient: Patient, spacing: Dp) {
    val uiText = rememberUiStrings()
    val sections = listOf(Triple(uiText(R.string.ui_patient_form_dialog_011), patient.treatmentPlan, ClinicalTextKind.CLINICAL),
        Triple(uiText(R.string.ui_patient_form_dialog_012), patient.followUp, ClinicalTextKind.CLINICAL),
        Triple(uiText(R.string.ui_labs), patient.labs, ClinicalTextKind.LABS)).filter { it.second.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        sections.forEachIndexed { index, (title, content, kind) ->
            if (index > 0) AppDivider()
            AppClinicalSection(title, content, kind = kind)
        }
    }
}

@Composable
private fun TwoColumnDetail(patient: Patient, spacing: Dp) {
    val uiText = rememberUiStrings()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < UiLayout.patientCardColumnsMinWidth) {
            SingleColumnDetail(patient, spacing)
            return@BoxWithConstraints
        }
        val dividerColor = MaterialTheme.colorScheme.outlineVariant
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(dividerColor, Offset(size.width / 2, 0f),
                        Offset(size.width / 2, size.height), UiBorder.normalWidth.toPx())
                },
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
            if (patient.treatmentPlan.isNotBlank()) {
                AppClinicalSection(uiText(R.string.ui_patient_form_dialog_011), patient.treatmentPlan)
            }
        }


        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            if (patient.followUp.isNotBlank()) {
                AppClinicalSection(uiText(R.string.ui_patient_form_dialog_012), patient.followUp)
            }
            if (patient.labs.isNotBlank()) {
                if (patient.followUp.isNotBlank()) AppDivider()
                AppClinicalSection(uiText(R.string.ui_labs), patient.labs, kind = ClinicalTextKind.LABS)
            }
            if (patient.followUp.isBlank() && patient.labs.isBlank()) {
                Text(
                    text = uiText(R.string.ui_patient_card_029),
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
    val uiText = rememberUiStrings()
    var menu by remember { mutableStateOf(false) }
    Box {
        AppIconButton(label = uiText(R.string.ui_patient_card_030),
            icon = AppIcons.MoreVert,
            onClick = { menu = true },
            modifier = Modifier.size(UiSpacing.touchTarget),
            iconModifier = Modifier.size(UiSize.iconMedium))
        AppMenu(expanded = menu, onDismissRequest = { menu = false }) {
            AppMenuItem(text = { Text(if (pinned) uiText(R.string.ui_patient_card_031) else uiText(R.string.ui_patient_card_032)) },
                leadingIcon = { Icon(if (pinned) AppIcons.Bookmark else AppIcons.BookmarkBorder, contentDescription = null) },
                onClick = { menu = false; onPinToggle() }, enabled = !readOnly)
            if (!readOnly) {
                AppMenuItem(text = { Text(if (priority) uiText(R.string.ui_patient_card_017) else uiText(R.string.ui_patient_card_018)) },
                    onClick = { menu = false; onPriorityChange(!priority) })
                AppMenuItem(text = { Text(uiText(R.string.ui_patient_card_019)) }, onClick = { menu = false; onCopy() })
                AppMenuItem(text = { Text(uiText(R.string.ui_ward_patient_dialogs_001)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun DiagnosisChip(type: DiagnosisType) {
    val uiText = rememberUiStrings()
    AppChip(text = type.uiLabel(uiText), colors = ContainerColors(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant), outlined = true)
}

private class PatientCardSizeSpec(private val duration: State<Int>) : FiniteAnimationSpec<IntSize> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<IntSize, V>): VectorizedFiniteAnimationSpec<V> {
        val millis = duration.value
        return (if (millis > 0) tween<IntSize>(millis) else snap<IntSize>()).vectorize(converter)
    }
}
