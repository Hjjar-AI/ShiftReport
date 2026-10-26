package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import com.hos.rushdpatients.ui.components.AppDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSecurityContent(state: SettingsUiState, viewModel: SettingsViewModel, onSignOut: () -> Unit) {
    val uiText = rememberUiStrings()
    AppToggleRow(
        title = uiText(R.string.ui_settings_preference_sections_014),
        subtitle = if (state.biometricAvailable) uiText(R.string.ui_settings_preference_sections_015)
        else uiText(R.string.ui_settings_preference_sections_016),
        checked = state.biometricEnabled,
        onCheckedChange = if (state.biometricAvailable) viewModel::setBiometric else null
    )

    Text(uiText(R.string.ui_settings_preference_sections_017), style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        listOf(1, 5, 15, 30).forEach { minutes ->
            AppFilterChip(
                selected = state.autoLockMinutes == minutes,
                onClick = { viewModel.setAutoLockMinutes(minutes) },
                label = { Text(uiText(R.string.ui_settings_preference_sections_018, minutes)) }
            )
        }
    }
    AppOutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_settings_preference_sections_019)) }
}
