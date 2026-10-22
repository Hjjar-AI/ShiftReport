package com.hos.rushdpatients.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.*

/** These controls affect this device only; the same callbacks work with demo memory state. */
@Composable
internal fun SettingsUiControls(value: UiPreferences, onChange: (UiPreferences) -> Unit) {
    val text = rememberUiStrings()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        AppSectionHeader(text(R.string.ui_custom_interface), expanded = expanded, onToggle = { expanded = !expanded },
            summary = UiStyleCatalog.profiles.firstOrNull { it.id == value.profile }?.label(text))
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
            AppChoiceChips(UiStyleCatalog.profiles.map { it.id }, value.profile,
                { onChange(value.copy(profile = it)) }, { id -> UiStyleCatalog.profiles.first { it.id == id }.label(text) },
                title = text(R.string.ui_style_profile))
            AppChoiceChips(UiDensity.entries.toList(), value.density,
                { onChange(value.copy(density = it)) }, { text(when (it) {
                    UiDensity.COMPACT -> R.string.ui_density_compact
                    UiDensity.NORMAL -> R.string.ui_density_normal
                    UiDensity.RELAXED -> R.string.ui_density_relaxed
                }) }, title = text(R.string.ui_density))
            AppChoiceChips(UiControlSize.entries.toList(), value.controlSize,
                { onChange(value.copy(controlSize = it)) }, { text(when (it) {
                    UiControlSize.DEFAULT -> R.string.ui_control_default
                    UiControlSize.COMPACT -> R.string.ui_density_compact
                    UiControlSize.STANDARD -> R.string.ui_density_normal
                    UiControlSize.COMFORTABLE -> R.string.ui_control_comfortable
                }) }, title = text(R.string.ui_control_size))
            AppToggleRow(text(R.string.ui_reduced_motion), value.reducedMotion,
                { onChange(value.copy(reducedMotion = it)) })
            AppToggleRow(text(R.string.ui_auto_hide_navigation), value.autoHideNavigation,
                { onChange(value.copy(autoHideNavigation = it)) })
            AppToggleRow(text(R.string.ui_compact_add_action), value.compactAddAction,
                { onChange(value.copy(compactAddAction = it)) })
            AppToggleRow(text(R.string.ui_expand_patient_header), value.expandPatientHeader,
                { onChange(value.copy(expandPatientHeader = it)) }, subtitle = text(R.string.ui_expand_patient_header_help))
            AppTextButton(onClick = { onChange(UiPreferences()) }) { Text(text(R.string.ui_reset_interface)) }
        }
    }
}
