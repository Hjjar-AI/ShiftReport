package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.NoticeKind
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

@Composable
internal fun SettingsSyncOverview(
    state: SettingsUiState, viewModel: SettingsViewModel, canManageDoctors: Boolean,
    onOpenDoctors: () -> Unit, onRetry: () -> Unit
) {
    val uiText = rememberUiStrings()
    val idle = !state.syncing && !state.backupBusy && !state.exportingCsv
    AppNotice(
        message = uiText(if (state.online) R.string.ui_sync_center_online else R.string.ui_sync_center_offline),
        kind = if (state.online) NoticeKind.INFO else NoticeKind.WARNING
    )
    state.syncIssue?.let { issue ->
        val doctorRecovery = state.syncRecovery == SettingsSyncRecovery.DOCTORS
        AppNotice(
            message = issue + if (doctorRecovery && !canManageDoctors) "\n" + uiText(R.string.ui_recovery_ask_admin) else "",
            kind = NoticeKind.WARNING,
            actionLabel = when {
                doctorRecovery && canManageDoctors -> uiText(R.string.ui_recovery_open_doctors)
                state.syncRecovery == SettingsSyncRecovery.PATIENTS -> uiText(R.string.ui_settings_data_sections_004)
                state.syncRecovery == null -> uiText(R.string.ui_retry)
                else -> null
            },
            onAction = when {
                doctorRecovery && canManageDoctors -> onOpenDoctors
                state.syncRecovery == SettingsSyncRecovery.PATIENTS -> viewModel::uploadCurrent
                state.syncRecovery == null -> onRetry
                else -> null
            }, actionEnabled = idle
        )
        if (state.syncRecovery != null) AppTextButton(onClick = onRetry, enabled = idle && state.online) {
            Text(uiText(R.string.ui_sync_center_retry))
        }
    }
    state.lastOperation?.let { Text(it, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
internal fun SettingsSyncContent(
    state: SettingsUiState, viewModel: SettingsViewModel, showRecovery: Boolean,
    onToggleRecovery: () -> Unit, onUndoRequested: () -> Unit, onFetchRequested: () -> Unit
) {
    val uiText = rememberUiStrings()
    val idle = !state.syncing && !state.backupBusy && !state.exportingCsv
    Text(uiText(R.string.ui_sync_center_current_shift, com.hos.rushdpatients.util.ShiftDate.current().toString()),
        style = MaterialTheme.typography.bodySmall)
    Text(uiText(when (state.patientsPending) {
        true -> R.string.ui_sync_center_patients_pending
        false -> R.string.ui_sync_center_no_pending
        null -> R.string.ui_sync_center_loading
    }))
    Text(uiText(R.string.ui_settings_data_sections_002, state.lastCsvSync ?: uiText(R.string.ui_unknown)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(uiText(R.string.ui_settings_data_sections_001), style = MaterialTheme.typography.bodySmall)
    LoadingButton(
        text = uiText(R.string.ui_settings_data_sections_003), outlined = true,
        loading = state.syncing, enabled = idle && state.online,
        onClick = onFetchRequested, modifier = Modifier.fillMaxWidth()
    )
    LoadingButton(
        text = uiText(R.string.sync_publish_all), outlined = true,
        loading = state.syncing, enabled = idle && state.online,
        onClick = viewModel::uploadCurrent, modifier = Modifier.fillMaxWidth()
    )
    if (state.canUndoDownload) {
        AppTextButton(onClick = onToggleRecovery, enabled = idle) {
            Text(if (showRecovery) uiText(R.string.ui_settings_data_sections_005) else uiText(R.string.ui_settings_data_sections_006))
        }
        if (showRecovery) {
            Text(uiText(R.string.ui_settings_data_sections_007), style = MaterialTheme.typography.bodySmall)
            AppOutlinedButton(onClick = onUndoRequested, enabled = idle,
                modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_settings_data_sections_008)) }
        }
    }
    SettingsPublicationHistory(state, viewModel)
}

@Composable
internal fun SettingsDoctorSyncContent(state: SettingsUiState, canManageDoctors: Boolean, onOpenDoctors: () -> Unit) {
    val uiText = rememberUiStrings()
    val idle = !state.syncing && !state.backupBusy && !state.exportingCsv
    Text(uiText(when (state.doctorsPending) {
        true -> R.string.ui_sync_center_doctors_pending
        false -> R.string.ui_sync_center_no_pending
        null -> R.string.ui_sync_center_loading
    }))
    Text(uiText(R.string.ui_sync_center_doctors_last, state.lastDoctorsSync ?: uiText(R.string.ui_unknown)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(uiText(R.string.ui_sync_center_doctors_help), style = MaterialTheme.typography.bodySmall)
    if (canManageDoctors) AppOutlinedButton(onClick = onOpenDoctors, enabled = idle,
        modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_recovery_open_doctors)) }
    else if (state.doctorsPending == true) Text(uiText(R.string.ui_recovery_ask_admin),
        style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsFilesContent(state: SettingsUiState, viewModel: SettingsViewModel, canManageProject: Boolean, onOpenVbaImport: () -> Unit, showHeading: Boolean = true) {
    val uiText = rememberUiStrings()
    if (showHeading) SettingsSubheading(uiText(R.string.ui_ward_drawer_014))
    Text(uiText(R.string.ui_settings_data_sections_009), style = MaterialTheme.typography.bodySmall)
    LoadingButton(
        text = uiText(R.string.ui_settings_data_sections_010),
        outlined = true,
        loading = state.exportingCsv,
        onClick = viewModel::exportCsv,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.syncing && !state.backupBusy
    )
    if (canManageProject) {
        AppOutlinedButton(
            onClick = onOpenVbaImport,
            enabled = !state.syncing && !state.backupBusy && !state.exportingCsv,
            modifier = Modifier.fillMaxWidth()
        ) { Text(uiText(R.string.ui_settings_data_sections_011)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsBackupContent(state: SettingsUiState, onCreateBackup: () -> Unit, onRestoreBackup: () -> Unit, showHeading: Boolean = true) {
    val uiText = rememberUiStrings()
    if (showHeading) SettingsSubheading(uiText(R.string.ui_ward_drawer_015))
    Text(
        uiText(R.string.ui_settings_data_sections_012),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(uiText(R.string.ui_settings_data_sections_024, state.lastEncryptedBackup ?: uiText(R.string.ui_unknown)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LoadingButton(
        text = uiText(R.string.ui_settings_data_sections_013),
        outlined = true,
        loading = state.backupBusy,
        enabled = !state.syncing && !state.exportingCsv,
        onClick = onCreateBackup,
        modifier = Modifier.fillMaxWidth()
    )
    AppOutlinedButton(
        onClick = onRestoreBackup,
        enabled = !state.backupBusy && !state.syncing && !state.exportingCsv,
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
    Text(uiText(R.string.diagnostics_screenshots_private))
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
