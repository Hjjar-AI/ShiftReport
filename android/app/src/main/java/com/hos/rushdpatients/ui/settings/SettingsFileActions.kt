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

/** Sensitive form state is memory-only and remains scoped to the Settings composition. */
internal class SettingsFileState {
    var backupDialogMode by mutableStateOf<String?>(null)
    var backupPassword by mutableStateOf("")
    var pendingImportUri by mutableStateOf<Uri?>(null)
    var pendingRestorePassword by mutableStateOf("")
    var confirmBackupRestore by mutableStateOf(false)
}
internal class SettingsProvisioningState {
    var visible by mutableStateOf(false)
    var passphrase by mutableStateOf("")
}
internal class SettingsFileActions(val openBackup: () -> Unit)

@Composable
internal fun rememberSettingsFileActions(state: SettingsUiState, canManageProject: Boolean,
    viewModel: SettingsViewModel, fileState: SettingsFileState): SettingsFileActions {
    val context = LocalContext.current
    val uiText = rememberUiStrings()
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> viewModel.exportEncryptedBackup(uri) }
    LaunchedEffect(state.backupReady) {
        if (state.backupReady) {
            viewModel.backupPickerLaunched()
            try {
                createBackup.launch("Rushd_Backup_${LocalDate.now()}.rpb")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.backupPickerFailed()
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            fileState.pendingImportUri = uri
            fileState.backupPassword = ""
            fileState.backupDialogMode = "restore"
        }
    }
    val createProvisioning = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ProjectProvisioningManager.EXPORT_MIME_TYPE)
    ) { uri ->
        viewModel.exportProjectProvisioning(uri)
    }

    LaunchedEffect(state.provisioningReady, canManageProject) {
        if (canManageProject && state.provisioningReady) {
            viewModel.provisioningPickerLaunched()
            try {
                createProvisioning.launch("ShiftReport_Join_${LocalDate.now()}.${ProjectProvisioningManager.EXPORT_EXTENSION}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.provisioningPickerFailed()
            }
        }
    }

    LaunchedEffect(state.provisioningShareUri, canManageProject) {
        if (!canManageProject) return@LaunchedEffect
        val uri = state.provisioningShareUri ?: return@LaunchedEffect
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = ProjectProvisioningManager.EXPORT_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, uiText(R.string.ui_settings_screen_010), uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, uiText(R.string.ui_settings_screen_011)))
            viewModel.provisioningShareHandled(failed = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            viewModel.provisioningShareHandled(failed = true)
        }
    }

    return remember(openBackup) { SettingsFileActions { openBackup.launch(arrayOf("application/octet-stream", "*/*")) } }
}
