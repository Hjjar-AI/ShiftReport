package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import com.hos.rushdpatients.ui.components.AppDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.sync.CsvSchema

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsSyncContent(state: SettingsUiState, viewModel: SettingsViewModel, showRecovery: Boolean, onToggleRecovery: () -> Unit, onUndoRequested: () -> Unit) {
    val uiText = rememberUiStrings()
    SettingsSubheading(uiText(R.string.ui_ward_drawer_013))
    Text(uiText(R.string.ui_settings_data_sections_001))

    state.lastCsvSync?.let {
        Text(
            uiText(R.string.ui_settings_data_sections_002, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    state.lastOperation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

    LoadingButton(
        text = uiText(R.string.ui_settings_data_sections_003),
        outlined = true,
        loading = state.syncing,
        enabled = !state.exportingCsv && !state.backupBusy,
        onClick = viewModel::bringLatest,
        modifier = Modifier.fillMaxWidth()
    )
    LoadingButton(
        text = uiText(R.string.ui_settings_data_sections_004),
        outlined = true,
        loading = state.syncing,
        enabled = !state.exportingCsv && !state.backupBusy,
        onClick = viewModel::uploadCurrent,
        modifier = Modifier.fillMaxWidth()
    )
    if (state.canUndoDownload) {
        AppTextButton(onClick = onToggleRecovery) {
            Text(if (showRecovery) uiText(R.string.ui_settings_data_sections_005) else uiText(R.string.ui_settings_data_sections_006))
        }
        if (showRecovery) {
            Text(uiText(R.string.ui_settings_data_sections_007),
                style = MaterialTheme.typography.bodySmall)
            AppOutlinedButton(onClick = onUndoRequested,
                enabled = !state.syncing && !state.backupBusy,
                modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_settings_data_sections_008)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsFilesContent(state: SettingsUiState, viewModel: SettingsViewModel, canManageProject: Boolean, onOpenVbaImport: () -> Unit) {
    val uiText = rememberUiStrings()
    SettingsSubheading(uiText(R.string.ui_ward_drawer_014))
    Text(uiText(R.string.ui_settings_data_sections_009), style = MaterialTheme.typography.bodySmall)
    LoadingButton(
        text = uiText(R.string.ui_settings_data_sections_010),
        outlined = true,
        loading = state.exportingCsv,
        onClick = viewModel::exportCsv,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.syncing
    )
    if (canManageProject) {
        AppOutlinedButton(
            onClick = onOpenVbaImport,
            modifier = Modifier.fillMaxWidth()
        ) { Text(uiText(R.string.ui_settings_data_sections_011)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsBackupContent(state: SettingsUiState, onCreateBackup: () -> Unit, onRestoreBackup: () -> Unit) {
    val uiText = rememberUiStrings()
    SettingsSubheading(uiText(R.string.ui_ward_drawer_015))
    Text(
        uiText(R.string.ui_settings_data_sections_012),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LoadingButton(
        text = uiText(R.string.ui_settings_data_sections_013),
        outlined = true,
        loading = state.backupBusy,
        enabled = !state.syncing,
        onClick = onCreateBackup,
        modifier = Modifier.fillMaxWidth()
    )
    AppOutlinedButton(
        onClick = onRestoreBackup,
        enabled = !state.backupBusy && !state.syncing,
        modifier = Modifier.fillMaxWidth()
    ) { Text(uiText(R.string.ui_settings_data_sections_014)) }
    AppDivider()
    SettingsSubheading(uiText(R.string.ui_settings_data_sections_015))
    Text(
        uiText(R.string.ui_settings_data_sections_016),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsStorageContent(state: SettingsUiState) {
    val uiText = rememberUiStrings()
    Text(uiText(R.string.ui_settings_data_sections_017, state.databaseSize))
    Text(uiText(R.string.ui_settings_data_sections_018, CsvSchema.CURRENT_VERSION))
    Text(uiText(R.string.ui_settings_data_sections_019, state.storedShifts))
    Text(uiText(R.string.ui_settings_data_sections_020, state.totalStoredPatients))
    Text(uiText(R.string.ui_settings_data_sections_021, state.deletedPatients))
    Text(uiText(R.string.ui_settings_data_sections_022, state.deviceId), style = MaterialTheme.typography.bodySmall)
    Text(
        uiText(R.string.ui_settings_data_sections_024, state.lastEncryptedBackup ?: uiText(R.string.ui_settings_data_sections_023)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
}
