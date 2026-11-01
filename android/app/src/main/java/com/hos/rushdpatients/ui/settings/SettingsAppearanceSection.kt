package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.AppPalettePicker
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.ui.theme.UiSpacing

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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
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
