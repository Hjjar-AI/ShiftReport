package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppClinicalCard
import com.hos.rushdpatients.ui.components.AppIconButton
import com.hos.rushdpatients.ui.theme.UiLayout

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppCompactTopBar
import com.hos.rushdpatients.ui.components.uiNumber
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
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
    val uiText = rememberUiStrings()
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
                    AppCompactTopBar(
                        title = {
                            PatientIdentityHeading(patient, showFreshness = true,
                                nameStyle = MaterialTheme.typography.titleLarge)
                        },
                        navigation = {
                            AppIconButton(label = uiText(R.string.ui_patient_details_screen_001),
            icon = Icons.Filled.Close,
            onClick = onDismiss)
                        }
                    )
                    roundsNavigation?.let { navigation ->
                        PatientRoundsBar(navigation, enabled = navigationEnabled, onNavigate = onNavigateToPatient)
                    }
                    Surface(tonalElevation = UiElevation.navigation) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(UiPadding.content),
                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
                        ) {
                            PatientDetailSection.entries.forEach { option ->
                                val count = when (option) {
                                    PatientDetailSection.TASKS -> patient.tasks.count { !it.done }
                                    PatientDetailSection.WARNINGS -> patient.badges.size
                                    PatientDetailSection.HISTORY -> activity.count { it.patientId == patient.id }
                                    else -> 0
                                }
                                FilterChip(
                                    selected = section == option,
                                    onClick = { section = option },
                                    label = {
                                        Text(option.arabicLabel(uiText) + if (count > 0) " ${uiNumber(count)}" else "")
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
        state = PatientCardState(patient = patient,
            expanded = true,
            twoColumn = true,
            doctorNames = doctorNames,
            readOnly = true,
            showViewControls = false,
            showIdentity = false),
        actions = PatientCardActions.None
    )
                    PatientDetailSection.TASKS -> AppClinicalCard(
                        title = uiText(R.string.ui_patient_details_screen_002),
                        content = PatientTasks.summary(patient.tasks, doctorNames)
                            .ifBlank { uiText(R.string.ui_patient_details_screen_003) }
                    )
                    PatientDetailSection.WARNINGS -> AppClinicalCard(
                        title = uiText(R.string.ui_patient_details_screen_004),
                        content = patient.badges.joinToString("\n") { badge ->
                            val level = badge.priority?.let { uiText(R.string.ui_patient_details_screen_005, it.uiLabel(uiText)) }.orEmpty()
                            "• ${badge.text}$level"
                        }.ifBlank { uiText(R.string.ui_patient_details_screen_006) }
                    )
                    PatientDetailSection.HISTORY -> AppClinicalCard(
                        title = uiText(R.string.ui_patient_details_screen_007),
                        content = meaningfulTimeline(uiText, patient, activity)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    AppOutlinedButton(onClick = onCopy) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Text(uiText(R.string.ui_copy), Modifier.padding(start = UiSpacing.small))
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
                                if (patient.isPriority) uiText(R.string.ui_patient_card_017) else uiText(R.string.ui_patient_card_018),
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
                    listFraction = UiLayout.defaultListFraction, onListFractionChange = {}, modifier = Modifier.fillMaxSize(), primaryTitle = uiText(R.string.ui_ward_screen_005),
                    primary = { content() })
            }
        }
    }
}

private fun meaningfulTimeline(uiText: UiStrings, patient: Patient, activity: List<AuditEntryEntity>): String {
    val matching = activity.filter { it.patientId == patient.id }
        .sortedByDescending { it.atEpochMillis }
        .take(20)
    if (matching.isNotEmpty()) {
        return matching.joinToString("\n\n") { entry ->
            buildString {
                append(uiDateTime(Instant.ofEpochMilli(entry.atEpochMillis)))
                append(" · ").append(entry.actorName ?: uiText(R.string.ui_ward_activity_screen_012))
                append("\n").append(entry.detail.ifBlank { entry.action })
                entry.beforeValue?.takeIf(String::isNotBlank)?.let { append(uiText(R.string.ui_patient_details_screen_010)).append(it) }
                entry.afterValue?.takeIf(String::isNotBlank)?.let { append(uiText(R.string.ui_patient_details_screen_011)).append(it) }
            }
        }
    }
    return buildString {
        append(uiText(R.string.ui_patient_details_screen_012))
        append(patient.lastEditedByName ?: uiText(R.string.ui_unspecified))
        append("\n")
        append(uiDateTime(patient.updatedAt))
        if (patient.isPriority) append(uiText(R.string.ui_patient_details_screen_013))
        if (patient.badges.isNotEmpty()) {
            append(uiText(R.string.ui_patient_details_screen_014))
            append(patient.badges.joinToString(uiText(R.string.ui_patient_card_002)) { it.text })
        }
    }
}


private enum class PatientDetailSection(@StringRes private val arabicLabelRes: Int) {
    CLINICAL(R.string.ui_patient_details_screen_015),
    TASKS(R.string.ui_tasks),
    WARNINGS(R.string.ui_patient_details_screen_016),
    HISTORY(R.string.ui_patient_details_screen_017);

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}
