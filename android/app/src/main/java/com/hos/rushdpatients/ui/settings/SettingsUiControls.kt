package com.hos.rushdpatients.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.*

/** Accessibility preference; spacing uses the shared layout defaults. */
@Composable
internal fun SettingsUiControls(value: UiPreferences, onChange: (UiPreferences) -> Unit) {
    val text = rememberUiStrings()
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
        AppToggleRow(text(R.string.ui_reduced_motion), value.reducedMotion,
            { onChange(value.copy(reducedMotion = it).simplified()) })
    }
}
