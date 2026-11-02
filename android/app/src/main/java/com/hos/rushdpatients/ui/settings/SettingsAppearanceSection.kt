package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppChoiceChips
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.AppPalettePicker
import com.hos.rushdpatients.ui.theme.AppAppearance
import com.hos.rushdpatients.ui.theme.AppFontScale
import com.hos.rushdpatients.ui.theme.AppThemePreset
import com.hos.rushdpatients.ui.theme.UiSpacing

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
        AppChoiceChips(options = AppAppearance.entries.toList(), selected = state.appearance,
            onSelect = onAppearance, label = { it.arabicLabel(uiText) },
            title = uiText(R.string.ui_settings_appearance_section_001))
        Text(uiText(R.string.ui_settings_appearance_section_002), style = MaterialTheme.typography.labelMedium)
        AppPalettePicker(selected = state.appTheme, onSelected = onTheme)
        AppChoiceChips(options = AppFontScale.entries.toList(), selected = state.fontScale,
            onSelect = onFont, label = { it.arabicLabel(uiText) },
            title = uiText(R.string.ui_settings_appearance_section_003))
        SettingsUiControls(state.uiPreferences, onUiPreferences)
    }
}
