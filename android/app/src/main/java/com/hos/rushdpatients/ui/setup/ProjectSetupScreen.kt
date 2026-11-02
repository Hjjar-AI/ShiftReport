package com.hos.rushdpatients.ui.setup

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppActionContent
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppButton
import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.ui.components.AppDialogHeader
import com.hos.rushdpatients.ui.components.AppDialogConfirmAction
import com.hos.rushdpatients.ui.components.AppDialogDismissAction
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import com.hos.rushdpatients.ui.theme.UiLayout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProjectSetupScreen(
    viewModel: ProjectSetupViewModel = hiltViewModel()
) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTopics by rememberSaveable(state.mode) { mutableStateOf(false) }
    var showInstructions by rememberSaveable(state.mode) { mutableStateOf(false) }
    var showResidents by rememberSaveable(state.mode) { mutableStateOf(false) }
    val openProvisioning = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::selectProvisioning) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = UiLayout.patientFormMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiSpacing.screen, vertical = UiSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
        ) {
            Text(
                uiText(R.string.ui_project_setup_screen_001),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = com.hos.rushdpatients.ui.theme.UiTypography.emphasizedWeight,
                textAlign = TextAlign.Center
            )
            Text(
                uiText(R.string.ui_project_setup_screen_002),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ProjectSetupChoices(selected = state.mode, enabled = !state.busy,
                onSelect = viewModel::setMode)

            if (state.mode == ProjectSetupMode.DEMO) {
                AppSection(uiText(R.string.ui_project_setup_screen_003)) {
                    Text(uiText(R.string.ui_project_setup_screen_004))
                    Text(uiText(R.string.ui_project_setup_screen_005),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (state.mode == ProjectSetupMode.JOIN) {
                ProjectJoinSections(
                    state = state,
                    onSelectProvisioning = { openProvisioning.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                    onSetProvisioningPassphrase = viewModel::setProvisioningPassphrase,
                    onUnlockProvisioning = viewModel::unlockProvisioning
                )
            }
            if (state.mode == ProjectSetupMode.CREATE) {
                ProjectCreateSections(
                    state = state,
                    showTopics = showTopics,
                    onToggleTopics = { showTopics = !showTopics },
                    showResidents = showResidents,
                    onToggleResidents = { showResidents = !showResidents },
                    onSetHospitalName = viewModel::setHospitalName,
                    onSetBotToken = viewModel::setBotToken,
                    onSetChatId = viewModel::setChatId,
                    onSetReportsTopicId = viewModel::setReportsTopicId,
                    onSetCsvTopicId = viewModel::setCsvTopicId,
                    onSetDoctorsTopicId = viewModel::setDoctorsTopicId,
                    onSetResidentsChatId = viewModel::setResidentsChatId,
                    onSetResidentsTopicId = viewModel::setResidentsTopicId,
                    onSetAdminName = viewModel::setAdminName,
                    onSetAdminTelegramId = viewModel::setAdminTelegramId,
                    onSetAdminGender = viewModel::setAdminGender,
                    onSetAdminClinicalRole = viewModel::setAdminClinicalRole,
                    onSetEncryptTelegram = viewModel::setEncryptTelegram
                )
            }
            AppTextButton(onClick = { showInstructions = !showInstructions },
                enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (showInstructions) uiText(R.string.ui_project_setup_screen_036) else uiText(R.string.ui_project_setup_screen_037))
            }
            if (showInstructions) InstructionCard(state.mode)
        }
        Column(Modifier.widthIn(max = UiLayout.patientFormMaxWidth).fillMaxWidth()
            .padding(horizontal = UiSpacing.screen, vertical = UiSpacing.small),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            state.error?.let {
                AppNotice(it, kind = NoticeKind.ERROR)
            }
            state.status?.let {
                AppNotice(it)
            }

            AppButton(
                onClick = viewModel::initialize,
                enabled = !state.busy && (state.mode != ProjectSetupMode.JOIN || state.importedProvisioning),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppActionContent(text = when (state.mode) {
                    ProjectSetupMode.DEMO -> uiText(R.string.ui_project_setup_screen_038)
                    ProjectSetupMode.JOIN -> uiText(R.string.ui_project_setup_screen_039)
                    ProjectSetupMode.CREATE -> uiText(R.string.ui_project_setup_screen_040)
                }, loading = state.busy)
            }
        }
    }
    if (state.confirmPinnedProjectReplacement) {
        AppAlertDialog(
            onDismissRequest = viewModel::dismissPinnedProjectReplacement,
            title = { AppDialogHeader(uiText(R.string.ui_setup_replace_pin_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(uiText(R.string.ui_setup_replace_pin_message))
                }
            },
            confirmButton = {
                AppDialogConfirmAction(
                    text = uiText(R.string.ui_setup_replace_pin_confirm),
                    destructive = true,
                    onClick = viewModel::confirmPinnedProjectReplacement
                )
            },
            dismissButton = {
                AppDialogDismissAction(onClick = viewModel::dismissPinnedProjectReplacement)
            }
        )
    }
}

@Composable
private fun InstructionCard(mode: ProjectSetupMode) {
    val uiText = rememberUiStrings()
    val text = when (mode) {
        ProjectSetupMode.DEMO ->
            uiText(R.string.ui_project_setup_screen_041)
        ProjectSetupMode.JOIN ->
            uiText(R.string.ui_project_setup_screen_042)
        ProjectSetupMode.CREATE ->
            uiText(R.string.ui_project_setup_screen_043)
    }
    com.hos.rushdpatients.ui.components.AppNotice(text,
        kind = com.hos.rushdpatients.ui.components.NoticeKind.ACCENT)
}
