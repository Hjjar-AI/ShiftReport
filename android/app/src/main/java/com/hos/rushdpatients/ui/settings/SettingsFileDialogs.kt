package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.AppText

import com.hos.rushdpatients.ui.components.AppSnackbarHost
import com.hos.rushdpatients.ui.components.AppReadingPane
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.ui.theme.UiTypography
import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppDestinationRow
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.AppSectionIndex
import com.hos.rushdpatients.ui.components.activeAppSection
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.ClipData
import android.content.Intent
import java.time.LocalDate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.config.ProjectProvisioningManager
import com.hos.rushdpatients.ui.theme.UiSpacing
import kotlinx.coroutines.CancellationException

@Composable
internal fun SettingsFileDialogs(state: SettingsUiState, canManageProject: Boolean,
    viewModel: SettingsViewModel, fileState: SettingsFileState, provisioningState: SettingsProvisioningState) {
    val uiText = rememberUiStrings()
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
