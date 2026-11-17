package com.hos.rushdpatients.ui.setup

import com.hos.rushdpatients.ui.components.AppText

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
internal fun ProjectSetupChoices(
    selected: ProjectSetupMode,
    enabled: Boolean,
    onSelect: (ProjectSetupMode) -> Unit
) {
    com.hos.rushdpatients.ui.components.AppAdaptiveRow(
        itemCount = ProjectSetupMode.entries.size, minimumRowWidth = UiLayout.setupChoicesMinWidth,
        modifier = Modifier.selectableGroup(), rowGap = UiSpacing.rowGap,
        columnGap = UiSpacing.small
    ) { index, cellModifier ->
        val mode = ProjectSetupMode.entries[index]
        SetupChoice(mode, selected == mode, enabled, { onSelect(mode) }, cellModifier)
    }
}

@Composable
private fun SetupChoice(
    mode: ProjectSetupMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val uiText = rememberUiStrings()
    val (title, description) = when (mode) {
        ProjectSetupMode.DEMO -> uiText(R.string.ui_project_setup_choices_001) to uiText(R.string.ui_project_setup_choices_002)
        ProjectSetupMode.JOIN -> uiText(R.string.ui_project_setup_choices_003) to uiText(R.string.ui_project_setup_choices_004)
        ProjectSetupMode.CREATE -> uiText(R.string.ui_project_setup_choices_005) to uiText(R.string.ui_project_setup_choices_006)
    }
    com.hos.rushdpatients.ui.components.AppSelectableSurface(
        selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick,
        modifier = modifier, shape = MaterialTheme.shapes.large, bordered = true) {
        Column(Modifier.fillMaxWidth().padding(UiSpacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                RadioButton(selected = selected, onClick = null, enabled = enabled)
                AppText(title, style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center)
            }
            AppText(description, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
