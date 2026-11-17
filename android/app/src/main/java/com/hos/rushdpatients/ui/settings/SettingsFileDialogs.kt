package com.hos.rushdpatients.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun SettingsFileDialogs(state: SettingsUiState, canManageProject: Boolean,
    viewModel: SettingsViewModel, fileState: SettingsFileState, provisioningState: SettingsProvisioningState) {
    var backupDialogMode by fileState::backupDialogMode
    var backupPassword by fileState::backupPassword
    var pendingImportUri by fileState::pendingImportUri
    var pendingRestorePassword by fileState::pendingRestorePassword
    var confirmBackupRestore by fileState::confirmBackupRestore
    var provisioningDialog by provisioningState::visible
    var provisioningPassphrase by provisioningState::passphrase
    backupDialogMode?.let { mode ->
        SettingsBackupPasswordDialog(
            exporting = mode == "export", backupPassword = backupPassword,
            onPasswordChange = { backupPassword = it },
            onConfirm = {
                if (mode == "export") {
                    viewModel.prepareEncryptedBackup(backupPassword)
                } else {
                    pendingRestorePassword = backupPassword
                    confirmBackupRestore = true
                }
                backupPassword = ""
                backupDialogMode = null
            },
            onDismiss = {
                backupDialogMode = null
                backupPassword = ""
                if (mode == "restore") pendingImportUri = null
            }
        )
    }

    if (canManageProject && provisioningDialog) {
        val directDownloads = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        fun dismissProvisioning() {
            provisioningDialog = false
            provisioningPassphrase = ""
        }
        SettingsProvisioningDialog(
            provisioningPassphrase = provisioningPassphrase,
            busy = state.provisioningBusy, directDownloads = directDownloads,
            onPassphraseChange = { provisioningPassphrase = it },
            onShare = {
                viewModel.prepareProjectProvisioning(provisioningPassphrase, share = true)
                dismissProvisioning()
            },
            onSave = {
                viewModel.prepareProjectProvisioning(provisioningPassphrase)
                dismissProvisioning()
            },
            onConfirm = {
                viewModel.prepareProjectProvisioning(provisioningPassphrase,
                    share = !directDownloads, saveToDownloads = directDownloads)
                dismissProvisioning()
            },
            onDismiss = { dismissProvisioning() }
        )
    }

    state.publicationReviewText?.let { reviewText ->
        SettingsPublicationReviewDialog(reviewText = reviewText, busy = state.syncing,
            onConfirm = viewModel::confirmPublication, onDismiss = viewModel::cancelPublicationReview)
    }

    if (confirmBackupRestore) {
        SettingsBackupRestoreDialog(
            onConfirm = {
                val uri = pendingImportUri
                val password = pendingRestorePassword
                confirmBackupRestore = false
                pendingImportUri = null
                pendingRestorePassword = ""
                if (uri != null) viewModel.restoreEncryptedBackup(uri, password)
            },
            onDismiss = {
                confirmBackupRestore = false
                pendingRestorePassword = ""
                pendingImportUri = null
            }
        )
    }
}
