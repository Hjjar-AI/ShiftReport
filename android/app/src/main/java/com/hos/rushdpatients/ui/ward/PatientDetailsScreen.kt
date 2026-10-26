package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppFilterChip
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
import com.hos.rushdpatients.ui.theme.UiElevation
import com.hos.rushdpatients.ui.components.uiDateTime
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.window.layout.FoldingFeature
import com.hos.rushdpatients.data.model.Patient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PatientDetailsScreen(
    patient: Patient,
    doctorNames: Map<String, String>,
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
    val session = rememberSaveable(saver = PatientDetailsSession.Saver) { PatientDetailsSession() }
    val position = session.forPatient(patient.id)
    var section by position::section
    val detailScroll = position.scroll
    val content: @Composable () -> Unit = {
        Scaffold(
            modifier = (if (embedded) Modifier else Modifier.safeDrawingPadding()).imePadding(),
            topBar = {
                Column {
                    AppCompactTopBar(
                        title = {
                            PatientIdentityHeading(patient,
                                nameStyle = MaterialTheme.typography.titleLarge)
                        },
                        navigation = {
                            AppIconButton(label = uiText(R.string.ui_patient_details_screen_001),
            icon = AppIcons.Close,
            onClick = onDismiss)
                        }
                    )
                    Surface(tonalElevation = UiElevation.raised) {
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
                                    else -> 0
                                }
                                AppFilterChip(
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
                if (roundsNavigation != null || !readOnly) {
                    Surface(tonalElevation = com.hos.rushdpatients.ui.theme.LocalUiStyle.current.actionBarElevation) {
                        if (roundsNavigation != null) {
                            PatientRoundsBar(roundsNavigation, enabled = navigationEnabled, onNavigate = onNavigateToPatient,
                                centerAction = if (readOnly) null else ({ buttonModifier ->
                                    AppButton(onClick = onEdit, modifier = buttonModifier, enabled = navigationEnabled) {
                                        AppActionLabel(UiAction.EDIT)
                                    }
                                }))
                        } else if (!readOnly) {
                            AppButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(UiPadding.content)) {
                                AppActionLabel(UiAction.EDIT)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .patientRoundsSwipe(patient.id, roundsNavigation, navigationEnabled, onNavigateToPatient)
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
                        content = PatientTasks.summary(patient.tasks)
                            .ifBlank { uiText(R.string.ui_patient_details_screen_003) }
                    )
                    PatientDetailSection.WARNINGS -> AppClinicalCard(
                        title = uiText(R.string.ui_patient_details_screen_004),
                        content = patient.badges.joinToString("\n") { badge ->
                            val level = badge.priority?.let { uiText(R.string.ui_patient_details_screen_005, it.uiLabel(uiText)) }.orEmpty()
                            "• ${badge.text}$level"
                        }.ifBlank { uiText(R.string.ui_patient_details_screen_006) }
                    )

                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    AppOutlinedButton(onClick = onCopy) {
                        Icon(AppIcons.ContentCopy, contentDescription = null)
                        Text(uiText(R.string.ui_copy), Modifier.padding(start = UiSpacing.small))
                    }
                    if (!readOnly) {
                        AppOutlinedButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPriorityChange(!patient.isPriority)
                        }) {
                            Icon(
                                if (patient.isPriority) AppIcons.Star else AppIcons.StarBorder,
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

/** Only presentation state is saved; the dialog/scaffold stay mounted across patient changes. */
private class PatientDetailsPosition(section: PatientDetailSection = PatientDetailSection.CLINICAL, scroll: Int = 0) {
    var section by mutableStateOf(section)
    val scroll = ScrollState(scroll)
}

private class PatientDetailsSession {
    private val positions = mutableMapOf<String, PatientDetailsPosition>()
    fun forPatient(id: String) = positions.getOrPut(id) { PatientDetailsPosition() }

    companion object {
        val Saver = listSaver<PatientDetailsSession, Any>(
            save = { session -> session.positions.flatMap { (id, position) ->
                listOf(id, position.section.name, position.scroll.value)
            } },
            restore = { saved -> PatientDetailsSession().apply {
                saved.chunked(3).forEach { entry ->
                    positions[entry[0] as String] = PatientDetailsPosition(
                        runCatching { PatientDetailSection.valueOf(entry[1] as String) }.getOrDefault(PatientDetailSection.CLINICAL), entry[2] as Int)
                }
            } }
        )
    }
}

private enum class PatientDetailSection(@StringRes private val arabicLabelRes: Int) {
    CLINICAL(R.string.ui_patient_details_screen_015),
    TASKS(R.string.ui_tasks),
    WARNINGS(R.string.ui_patient_details_screen_016);

    fun arabicLabel(uiText: UiStrings): String = uiText(arabicLabelRes)
}
