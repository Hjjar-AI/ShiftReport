package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.settings.SettingsAppearanceSection
import com.hos.rushdpatients.ui.settings.SettingsUiState
import com.hos.rushdpatients.ui.theme.*

@Composable
internal fun DemoPreferencesContent(
    appearance: AppAppearance, onAppearance: (AppAppearance) -> Unit,
    accent: AppThemePreset, onAccent: (AppThemePreset) -> Unit,
    font: AppFontScale, onFont: (AppFontScale) -> Unit,
    expanded: Boolean, twoColumn: Boolean, compact: Boolean,
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
    }
}
