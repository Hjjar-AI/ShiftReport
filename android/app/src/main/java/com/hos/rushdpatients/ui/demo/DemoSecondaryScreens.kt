package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.domain.report.ReportReadiness
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.settings.SettingsAppearanceSection
import com.hos.rushdpatients.ui.settings.SettingsUiState
import com.hos.rushdpatients.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DemoReportContent(
    state: DemoWardState, onDoctors: () -> Unit,
    onExport: (PdfExportOptions, PdfStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiText = rememberUiStrings()
    var style by rememberSaveable { mutableStateOf(PdfStyle.CLASSIC) }
    var orientation by rememberSaveable { mutableStateOf(PdfOrientation.PORTRAIT) }
    var color by rememberSaveable { mutableStateOf(PdfColorPreset.TEAL) }
    var weight by rememberSaveable { mutableStateOf(PdfRuleWeight.MEDIUM) }
    var contrast by rememberSaveable { mutableStateOf(PdfRuleContrast.MEDIUM) }
    var shading by rememberSaveable { mutableStateOf(PdfRowShading.AUTO) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var showContents by rememberSaveable { mutableStateOf(false) }
    val warnings = ReportReadiness.warnings(state.patients)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        AppSection(uiText(R.string.ui_shift_doctor_picker_001), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer),
            titleColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Text(state.shiftDoctors.joinToString(uiText(R.string.ui_patient_card_002)) { it.fullName }.ifBlank { uiText(R.string.ui_demo_secondary_screens_001) })
            AppOutlinedButton(onClick = onDoctors, enabled = !state.exporting, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(R.string.ui_report_preview_screen_022))
            }
        }
        AppSection(uiText(R.string.ui_demo_secondary_screens_002)) {
            Text(uiText(R.string.ui_demo_secondary_screens_003, state.date, uiNumber(state.summary.patientCount), uiNumber(state.summary.escortCount)))
            Text(uiText(R.string.ui_demo_secondary_screens_004), style = MaterialTheme.typography.bodySmall)
            AppPdfStylePicker(selected = style, onSelected = { style = it }, enabled = !state.exporting)
            Text(uiText(R.string.ui_demo_secondary_screens_005),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (warnings.isNotEmpty()) AppSection(uiText(R.string.ui_report_preview_screen_046)) {
            warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        AppTextButton(onClick = { advanced = !advanced }, enabled = !state.exporting) {
            Text(if (advanced) uiText(R.string.ui_settings_report_section_004) else uiText(R.string.ui_settings_report_section_005))
        }
        if (advanced) AppSection(uiText(R.string.ui_settings_report_section_005)) {
            if (style != PdfStyle.CARDS) AppToggleRow(uiText(R.string.ui_demo_secondary_screens_006),
                checked = orientation == PdfOrientation.LANDSCAPE,
                onCheckedChange = { orientation = if (it) PdfOrientation.LANDSCAPE else PdfOrientation.PORTRAIT },
                enabled = !state.exporting)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                PdfColorPreset.entries.forEach { value ->
                    AppFilterChip(selected = color == value, onClick = { color = value },
                        enabled = !state.exporting, label = { Text(value.uiLabel(uiText)) })
                }
            }
            AppPdfAppearanceControls(weight, contrast, shading, dark = false,
                onWeight = { weight = it }, onContrast = { contrast = it }, onShading = { shading = it },
                onPreset = { weight = it.weight; contrast = it.contrast; shading = it.shading },
                enabled = !state.exporting, showShading = style != PdfStyle.CARDS)
        }
        LoadingButton(text = uiText(R.string.ui_demo_secondary_screens_007), loading = state.exporting,
            enabled = !state.exporting && state.patients.isNotEmpty() && state.shiftDoctorIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(), onClick = {
                onExport(PdfExportOptions(orientation = orientation, colorPreset = color,
                    ruleWeight = weight, ruleContrast = contrast, rowShading = shading), style)
            })
        AppTextButton(onClick = { showContents = !showContents }) {
            Text(if (showContents) uiText(R.string.ui_demo_secondary_screens_008) else uiText(R.string.ui_demo_secondary_screens_009))
        }
        if (showContents) state.ordered.forEach { patient ->
            AppSection("${uiNumber(patient.sortOrder)}. ${patient.displayName}") {
                AppClinicalSection(uiText(R.string.ui_patient_card_024), patient.initialDiagnosis)
                AppClinicalSection(uiText(R.string.ui_demo_secondary_screens_010), patient.treatmentPlan)
                AppClinicalSection(uiText(R.string.ui_patient_form_dialog_012), patient.followUp)
                AppClinicalSection(uiText(R.string.ui_labs), patient.labs, kind = ClinicalTextKind.LABS)
                Text(PatientTasks.summary(patient.tasks, state.names), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun DemoTeamContent(
    state: DemoWardState, onDoctors: () -> Unit, onActor: (String) -> Unit, modifier: Modifier = Modifier
) {
    val uiText = rememberUiStrings()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        AppSection(uiText(R.string.ui_shift_doctor_picker_001)) {
            Text(state.shiftDoctors.joinToString(uiText(R.string.ui_patient_card_002)) { it.fullName })
            AppOutlinedButton(onClick = onDoctors, modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_report_preview_screen_023)) }
        }
        AppSection(uiText(R.string.ui_demo_secondary_screens_011)) {
            Text(uiText(R.string.ui_demo_secondary_screens_012), style = MaterialTheme.typography.bodySmall)
            state.doctors.forEach { doctor ->
                AppChoiceRow(label = "${doctor.fullName} · ${doctor.clinicalRole.uiLabel(uiText)}",
                    selected = state.actorId == doctor.id, onClick = { onActor(doctor.id) })
            }
        }
    }
}

@Composable
internal fun DemoPreferencesContent(
    appearance: AppAppearance, onAppearance: (AppAppearance) -> Unit,
    accent: AppThemePreset, onAccent: (AppThemePreset) -> Unit,
    font: AppFontScale, onFont: (AppFontScale) -> Unit,
    expanded: Boolean, twoColumn: Boolean, compact: Boolean,
    saveAndNext: Boolean, onSaveAndNext: (Boolean) -> Unit,
    uiPreferences: com.hos.rushdpatients.ui.theme.UiPreferences,
    onUiPreferences: (com.hos.rushdpatients.ui.theme.UiPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiText = rememberUiStrings()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        AppNotice(uiText(R.string.ui_demo_secondary_screens_013))
        AppSection(uiText(R.string.ui_demo_secondary_screens_014)) {
            SettingsAppearanceSection(SettingsUiState(appearance = appearance, appTheme = accent, uiPreferences = uiPreferences,
                fontScale = font, patientDetailsExpanded = expanded,
                patientTwoColumn = twoColumn, patientCompactDensity = compact),
                onAppearance = onAppearance, onTheme = onAccent, onFont = onFont, onUiPreferences = onUiPreferences)
        }
        AppSection(uiText(R.string.ui_demo_secondary_screens_015)) {
            AppToggleRow(uiText(R.string.ui_edit_patient_dialog_002), checked = saveAndNext, onCheckedChange = onSaveAndNext)
            Text(uiText(R.string.ui_demo_secondary_screens_016),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
