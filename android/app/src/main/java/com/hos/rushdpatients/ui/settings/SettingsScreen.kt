package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.theme.UiTypography
import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Lock
import com.hos.rushdpatients.ui.components.AppDestinationRow
import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.components.AppSectionIndex
import com.hos.rushdpatients.ui.components.activeAppSection
import com.hos.rushdpatients.ui.components.PasswordField
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
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

enum class SettingsWorkspace(@StringRes private val titleRes: Int, @StringRes private val descriptionRes: Int) {
    PREFERENCES(R.string.ui_ward_drawer_018, R.string.ui_settings_screen_034),
    APPEARANCE(R.string.ui_settings_screen_035, R.string.ui_settings_screen_036),
    WORKFLOW(R.string.ui_settings_section_layout_029, R.string.ui_settings_screen_037),
    REPORTS(R.string.ui_settings_screen_038, R.string.ui_settings_screen_039),
    SECURITY(R.string.ui_settings_section_layout_032, R.string.ui_settings_screen_040),
    DATA(R.string.ui_ward_drawer_013, R.string.ui_settings_screen_041),
    FILES(R.string.ui_ward_drawer_014, R.string.ui_settings_screen_042),
    BACKUP(R.string.ui_settings_section_layout_031, R.string.ui_settings_screen_043),
    PROJECT(R.string.ui_ward_drawer_019, R.string.ui_settings_screen_044),
    INVITATIONS(R.string.ui_ward_drawer_022, R.string.ui_settings_screen_045),
    DESTINATIONS(R.string.ui_ward_drawer_021, R.string.ui_settings_screen_046),
    RESET(R.string.ui_ward_drawer_025, R.string.ui_settings_screen_047),
    DIAGNOSTICS(R.string.ui_ward_drawer_027, R.string.ui_settings_screen_048);

    fun title(uiText: UiStrings): String = uiText(titleRes)
    fun description(uiText: UiStrings): String = uiText(descriptionRes)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onOpenVbaImport: () -> Unit,
    workspace: SettingsWorkspace = SettingsWorkspace.PREFERENCES,
    onOpenWorkspace: (SettingsWorkspace) -> Unit = {},
    onOpenDoctors: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canManageProject = isAdmin && state.isAdmin
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val settingsScroll = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<String, Int>() }
    val sections = remember(canManageProject, state.isPermanentAdmin, workspace) {
        SettingsSectionId.entries.filter { it.workspace == workspace && (canManageProject || !it.adminOnly) && (it != SettingsSectionId.RESET || state.isPermanentAdmin) }
    }
    val sectionTitles = sections.map { it.title(uiText) }
    val settingsScope = rememberCoroutineScope()
    state.emptyStartReview?.takeIf { canManageProject && state.isPermanentAdmin }?.let { review ->
        AlertDialog(onDismissRequest = viewModel::dismissEmptyStart,
            title = { Text(uiText(R.string.ui_settings_screen_001)) },
            text = { Column(Modifier.appDialogBody().verticalScroll(rememberScrollState())) { Text(review) } },
            confirmButton = { AppTextButton(onClick = viewModel::confirmEmptyStart, enabled = !state.emptyStartBusy) {
                Text(uiText(R.string.ui_settings_screen_002), color = MaterialTheme.colorScheme.error)
            } }, dismissButton = { AppTextButton(onClick = viewModel::dismissEmptyStart) { Text(uiText(R.string.ui_cancel)) } })
    }
    if (state.emptyStartBusy) AlertDialog(onDismissRequest = {}, title = { Text(uiText(R.string.ui_settings_screen_003)) },
        text = { com.hos.rushdpatients.ui.components.AppLoadingState(uiText(R.string.ui_settings_screen_004)) }, confirmButton = {})
    state.emptyStartResult?.let { result ->
        AlertDialog(onDismissRequest = viewModel::dismissEmptyStart, title = { Text(uiText(R.string.ui_settings_screen_005)) },
            text = { Text(result) }, confirmButton = { AppTextButton(onClick = viewModel::dismissEmptyStart) { Text(uiText(R.string.ui_settings_screen_006)) } })
    }
    var showRecovery by rememberSaveable { mutableStateOf(false) }
    var confirmUndoDownload by remember { mutableStateOf(false) }
    if (confirmUndoDownload) com.hos.rushdpatients.ui.components.ConfirmDialog(
        title = uiText(R.string.ui_settings_screen_007),
        message = uiText(R.string.ui_settings_screen_008),
        confirmText = uiText(R.string.ui_settings_screen_009),
        onConfirm = { confirmUndoDownload = false; viewModel.undoLastDownload() },
        onDismiss = { confirmUndoDownload = false })

    var backupDialogMode by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestorePassword by remember { mutableStateOf("") }
    var confirmBackupRestore by remember { mutableStateOf(false) }
    var advancedPdf by rememberSaveable { mutableStateOf(false) }
    var provisioningDialog by remember(canManageProject) { mutableStateOf(false) }
    var provisioningPassphrase by remember(canManageProject) { mutableStateOf("") }

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
            pendingImportUri = uri
            backupPassword = ""
            backupDialogMode = "restore"
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

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            val result = snackbar.showSnackbar(
                message = it,
                actionLabel = if (state.retryAction != null) uiText(R.string.ui_retry) else null
            )
            viewModel.dismissSnackbar()
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.retryLastAction()
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = workspace.title(uiText), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (sections.size > 1) {
                val activeSection = activeAppSection(sectionTitles, sectionOffsets, settingsScroll)
                AppSectionIndex(sections = sectionTitles, selected = activeSection, label = { it },
                    onSelect = { title ->
                        settingsScope.launch {
                            sectionOffsets[title]?.let { offset ->
                                settingsScroll.animateScrollTo(offset.coerceIn(0, settingsScroll.maxValue))
                            }
                        }
                    }, modifier = Modifier.padding(horizontal = UiSpacing.medium))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small)
                    .verticalScroll(settingsScroll),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                if (workspace == SettingsWorkspace.PREFERENCES) AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(UiPadding.content),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.micro)
                    ) {
                        Text(
                            text = state.doctorName.ifBlank { "—" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = UiTypography.emphasizedWeight
                        )
                        Text(
                            text = uiText(R.string.ui_settings_screen_012, state.role.ifBlank { "—" }),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    workspace.description(uiText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = UiSpacing.tiny)
                )

                sections.forEach { section ->
                    SettingsSection(
                        title = section.title(uiText),
                        showHeader = sections.size > 1,
                        summary = section.summary(uiText, state),
                        expanded = true,
                        onToggle = null,
                        onPositioned = { sectionOffsets[section.title(uiText)] = it }
                    ) {
                        when (section) {
                            SettingsSectionId.APPEARANCE -> SettingsAppearanceSection(
                                state = state,
                                onAppearance = viewModel::setAppearance,
                                onTheme = viewModel::setAppTheme,
                                onFont = viewModel::setFontScale
                            )
                            SettingsSectionId.PATIENT_CARDS -> SettingsPatientCardsContent(state, viewModel)
                            SettingsSectionId.WORKFLOW -> SettingsWorkflowContent(state, viewModel)
                            SettingsSectionId.REPORT -> SettingsReportContent(state, viewModel, advancedPdf, { advancedPdf = !advancedPdf })
                            SettingsSectionId.SYNC -> SettingsSyncContent(state, viewModel, showRecovery, { showRecovery = !showRecovery }, { confirmUndoDownload = true })
                            SettingsSectionId.FILES -> SettingsFilesContent(state, viewModel, canManageProject, onOpenVbaImport)
                            SettingsSectionId.BACKUP -> SettingsBackupContent(state, { backupPassword = ""; backupDialogMode = "export" }, { openBackup.launch(arrayOf("application/octet-stream", "*/*")) })
                            SettingsSectionId.CONNECTION -> SettingsConnectionContent(canManageProject)
                            SettingsSectionId.RESET -> SettingsResetContent(state, viewModel)
                            SettingsSectionId.INVITATIONS -> SettingsInvitationsContent(state, { provisioningPassphrase = ""; provisioningDialog = true })
                            SettingsSectionId.DESTINATIONS -> SettingsDestinationsContent(state, viewModel, onOpenDoctors)
                            SettingsSectionId.SECURITY -> SettingsSecurityContent(state, viewModel, onSignOut)
                            SettingsSectionId.STORAGE -> SettingsStorageContent(state)
                        }
                    }
                }

                if (workspace == SettingsWorkspace.PREFERENCES) {
                    listOf(SettingsWorkspace.APPEARANCE, SettingsWorkspace.WORKFLOW,
                        SettingsWorkspace.REPORTS, SettingsWorkspace.SECURITY).forEach { destination ->
                        AppDestinationRow(title = destination.title(uiText), summary = destination.description(uiText),
                            icon = when (destination) {
                                SettingsWorkspace.APPEARANCE -> Icons.Filled.Palette
                                SettingsWorkspace.WORKFLOW -> Icons.Filled.Route
                                SettingsWorkspace.REPORTS -> Icons.Filled.PictureAsPdf
                                else -> Icons.Filled.Lock
                            }, onClick = { onOpenWorkspace(destination) })
                    }
                }
                if (sections.isEmpty() && workspace != SettingsWorkspace.PREFERENCES) {
                    AppNotice(uiText(R.string.ui_settings_screen_013))
                }
                Spacer(Modifier.height(UiSpacing.small))
            }
        }
    }

    backupDialogMode?.let { mode ->
        AlertDialog(
            onDismissRequest = {
                backupDialogMode = null
                backupPassword = ""
                if (mode == "restore") pendingImportUri = null
            },
            title = {
                Text(if (mode == "export") uiText(R.string.ui_settings_screen_014) else uiText(R.string.ui_settings_screen_015))
            },
            text = {
                PasswordField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = uiText(R.string.ui_settings_screen_016)
                )
            },
            confirmButton = {
                AppTextButton(
                    enabled = backupPassword.length >= 8,
                    onClick = {
                        if (mode == "export") {
                            viewModel.prepareEncryptedBackup(backupPassword)
                        } else {
                            pendingRestorePassword = backupPassword
                            confirmBackupRestore = true
                        }
                        backupPassword = ""
                        backupDialogMode = null
                    }
                ) { Text(if (mode == "export") uiText(R.string.ui_settings_screen_017) else uiText(R.string.ui_restore)) }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    backupDialogMode = null
                    backupPassword = ""
                    if (mode == "restore") pendingImportUri = null
                }) { Text(uiText(R.string.ui_cancel)) }
            }
        )
    }

    if (canManageProject && provisioningDialog) {
        val directDownloads = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        AlertDialog(
            onDismissRequest = {
                provisioningDialog = false
                provisioningPassphrase = ""
            },
            title = { Text(uiText(R.string.ui_settings_screen_019)) },
            text = {
                Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text(uiText(R.string.ui_settings_screen_020))
                    PasswordField(
                        value = provisioningPassphrase,
                        onValueChange = { provisioningPassphrase = it },
                        label = uiText(R.string.ui_settings_screen_021)
                    )
                    Text(if (directDownloads) uiText(R.string.ui_settings_screen_022)
                        else uiText(R.string.ui_settings_screen_023),
                        style = MaterialTheme.typography.bodySmall)
                    if (directDownloads) AppTextButton(
                        enabled = provisioningPassphrase.length >= 10 && !state.provisioningBusy,
                        onClick = {
                            viewModel.prepareProjectProvisioning(provisioningPassphrase, share = true)
                            provisioningPassphrase = ""
                            provisioningDialog = false
                        }, modifier = Modifier.fillMaxWidth()
                    ) { Text(uiText(R.string.ui_settings_screen_024)) }
                    AppTextButton(
                        enabled = provisioningPassphrase.length >= 10 && !state.provisioningBusy,
                        onClick = {
                            viewModel.prepareProjectProvisioning(provisioningPassphrase)
                            provisioningPassphrase = ""
                            provisioningDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
                    ) { Text(uiText(R.string.ui_settings_screen_025)) }
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = provisioningPassphrase.length >= 10 && !state.provisioningBusy,
                    onClick = {
                        viewModel.prepareProjectProvisioning(provisioningPassphrase,
                            share = !directDownloads, saveToDownloads = directDownloads)
                        provisioningPassphrase = ""
                        provisioningDialog = false
                    }
                ) { Text(if (directDownloads) uiText(R.string.ui_settings_screen_026) else uiText(R.string.ui_settings_screen_027)) }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    provisioningDialog = false
                    provisioningPassphrase = ""
                }) { Text(uiText(R.string.ui_cancel)) }
            }
        )
    }

    state.publicationReviewText?.let { reviewText ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPublicationReview,
            title = { Text(uiText(R.string.ui_settings_screen_028)) },
            text = {
                Column(Modifier.appDialogBody(UiDialog.reviewBodyMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text(reviewText)
                    Text(uiText(R.string.ui_settings_screen_029))
                }
            },
            confirmButton = {
                AppTextButton(onClick = viewModel::confirmPublication, enabled = !state.syncing) {
                    Text(uiText(R.string.ui_settings_screen_030))
                }
            },
            dismissButton = {
                AppTextButton(onClick = viewModel::cancelPublicationReview, enabled = !state.syncing) { Text(uiText(R.string.ui_cancel)) }
            }
        )
    }

    if (confirmBackupRestore) {
        AlertDialog(
            onDismissRequest = {
                confirmBackupRestore = false
                pendingRestorePassword = ""
                pendingImportUri = null
            },
            title = { Text(uiText(R.string.ui_settings_screen_031)) },
            text = {
                Text(uiText(R.string.ui_settings_screen_032))
            },
            confirmButton = {
                AppTextButton(onClick = {
                    val uri = pendingImportUri
                    val password = pendingRestorePassword
                    confirmBackupRestore = false
                    pendingImportUri = null
                    pendingRestorePassword = ""
                    if (uri != null) viewModel.restoreEncryptedBackup(uri, password)
                }) { Text(uiText(R.string.ui_settings_screen_033)) }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    confirmBackupRestore = false
                    pendingRestorePassword = ""
                    pendingImportUri = null
                }) { Text(uiText(R.string.ui_cancel)) }
            }
        )
    }
}
