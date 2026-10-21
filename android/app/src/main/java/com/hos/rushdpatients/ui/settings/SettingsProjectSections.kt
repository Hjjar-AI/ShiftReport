package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.connection.ProjectConnectionAction
import com.hos.rushdpatients.ui.components.LoadingButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsConnectionContent(canManageProject: Boolean) {
    val uiText = rememberUiStrings()
    Text(
        uiText(R.string.ui_settings_project_sections_001),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ProjectConnectionAction(isAdmin = canManageProject)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsResetContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    val uiText = rememberUiStrings()
    if (state.isPermanentAdmin) {
        SettingsSubheading(uiText(R.string.ui_settings_project_sections_002))
        Text(uiText(R.string.ui_settings_project_sections_003),
            style = MaterialTheme.typography.bodySmall)
        AppOutlinedButton(onClick = viewModel::prepareEmptyStart,
            enabled = !state.emptyStartBusy && !state.syncing && !state.backupBusy && !state.provisioningBusy,
            modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_settings_project_sections_004), color = MaterialTheme.colorScheme.error) }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsInvitationsContent(state: SettingsUiState, onCreateInvitation: () -> Unit) {
    val uiText = rememberUiStrings()
    Text(uiText(R.string.ui_settings_project_sections_005), style = MaterialTheme.typography.labelMedium)
    Text(
        uiText(R.string.ui_settings_project_sections_006),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LoadingButton(
        text = uiText(R.string.ui_settings_project_sections_007),
        outlined = true,
        loading = state.provisioningBusy,
        onClick = onCreateInvitation,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsDestinationsContent(state: SettingsUiState, viewModel: SettingsViewModel, onOpenDoctors: () -> Unit) {
    val uiText = rememberUiStrings()
    AppOutlinedButton(onClick = onOpenDoctors) { Text(uiText(R.string.ui_settings_project_sections_008)) }
    Text(uiText(R.string.ui_settings_project_sections_009), style = MaterialTheme.typography.labelMedium)
    Text(
        uiText(R.string.ui_settings_project_sections_010),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (state.supervisors.isEmpty()) {
        Text(
            uiText(R.string.ui_settings_project_sections_011),
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        state.supervisors.forEach { supervisor ->
            androidx.compose.runtime.key(supervisor.id) {
                SupervisorGroupEditor(
                    supervisor = supervisor,
                    saving = state.savingSupervisorGroupId == supervisor.id,
                    enabled = state.savingSupervisorGroupId == null,
                    onSave = { expected, value ->
                        viewModel.setSupervisorGroupChatId(expected, value)
                    }
                )
            }
        }
    }
}
