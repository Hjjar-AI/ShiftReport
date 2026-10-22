package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.ward.PatientCardState
import com.hos.rushdpatients.ui.ward.PatientCardActions
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.ui.components.AppPalettePicker
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.ui.theme.RushdPatientsTheme
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.ward.PatientCard

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAppearanceSection(
    state: SettingsUiState,
    onAppearance: (AppAppearance) -> Unit,
    onTheme: (AppThemePreset) -> Unit,
    onFont: (AppFontScale) -> Unit,
    onUiPreferences: (com.hos.rushdpatients.ui.theme.UiPreferences) -> Unit
) {
    val uiText = rememberUiStrings()
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            Text(uiText(R.string.ui_settings_appearance_section_001), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                AppAppearance.entries.forEach { option ->
                    AppFilterChip(selected = state.appearance == option,
                        onClick = { onAppearance(option) }, label = { Text(option.arabicLabel(uiText)) })
                }
            }
            Text(uiText(R.string.ui_settings_appearance_section_002), style = MaterialTheme.typography.labelMedium)
            AppPalettePicker(selected = state.appTheme, onSelected = onTheme)
            Text(uiText(R.string.ui_settings_appearance_section_003), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                AppFontScale.entries.forEach { option ->
                    AppFilterChip(selected = state.fontScale == option,
                        onClick = { onFont(option) }, label = { Text(option.arabicLabel(uiText)) })
                }
            }
            SettingsUiControls(state.uiPreferences, onUiPreferences)
        }
    }
    val fontScale = com.hos.rushdpatients.ui.theme.uiLayoutScale()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= UiLayout.splitMinWidth * fontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
                controls(Modifier.weight(1f))
                SettingsPatientCardPreview(state, Modifier.weight(1f))
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            controls(Modifier.fillMaxWidth())
            SettingsPatientCardPreview(state)
        }
    }
}

/** A real card renderer with fixed, memory-only dummy content and no clinical actions. */
@Composable
internal fun SettingsPatientCardPreview(state: SettingsUiState, modifier: Modifier = Modifier) {
    val uiText = rememberUiStrings()
    val sample = remember {
        Patient(id = "appearance-preview", name = uiText(R.string.ui_settings_appearance_section_004), admittanceNumber = uiText(R.string.ui_settings_appearance_section_005),
            initialDiagnosis = uiText(R.string.ui_settings_appearance_section_006),
            treatmentPlan = uiText(R.string.ui_settings_appearance_section_007),
            followUp = uiText(R.string.ui_settings_appearance_section_008), labs = "Hb: 12.5 g/dL",
            responsibleResidentId = "preview-resident", responsibleSpecialistId = "preview-supervisor")
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        Text(uiText(R.string.ui_settings_appearance_section_009), style = MaterialTheme.typography.labelLarge)
        Text(uiText(R.string.ui_settings_appearance_section_010),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Font scale comes from the same app-wide density provider as the ward.
        RushdPatientsTheme(preset = state.appTheme, appearance = state.appearance, uiPreferences = state.uiPreferences) {
            PatientCard(
        state = PatientCardState(patient = sample,
            expanded = true,
            twoColumn = state.patientTwoColumn,
            compact = state.patientCompactDensity,
            doctorNames = mapOf("preview-resident" to uiText(R.string.ui_settings_appearance_section_011), "preview-supervisor" to uiText(R.string.ui_settings_appearance_section_012)),
            readOnly = true,
            showViewControls = false),
        actions = PatientCardActions.None
    )
        }
    }
}
