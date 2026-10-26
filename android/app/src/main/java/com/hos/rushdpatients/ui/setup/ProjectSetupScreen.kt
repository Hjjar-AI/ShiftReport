package com.hos.rushdpatients.ui.setup

import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppActionContent
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppTextField
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.ui.components.PasswordField

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProjectSetupScreen(
    viewModel: ProjectSetupViewModel = hiltViewModel()
) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTopics by rememberSaveable(state.mode) { mutableStateOf(false) }
    var showInstructions by rememberSaveable(state.mode) { mutableStateOf(false) }
    val openProvisioning = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importProvisioning) }

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

            if (state.mode != ProjectSetupMode.DEMO) {
                if (state.mode == ProjectSetupMode.JOIN) {
                    AppSection(uiText(R.string.ui_project_setup_screen_006)) {
                        Text(
                            uiText(R.string.ui_project_setup_screen_007),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PasswordField(
                            value = state.provisioningPassphrase,
                            onValueChange = viewModel::setProvisioningPassphrase,
                            label = uiText(R.string.ui_project_setup_screen_008),
                            enabled = !state.busy
                        )
                        AppButton(
                            onClick = { openProvisioning.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                            enabled = !state.busy && state.provisioningPassphrase.length >= 10,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (state.importedProvisioning) uiText(R.string.ui_project_setup_screen_009) else uiText(R.string.ui_project_setup_screen_010)) }
                    }
                    if (state.importedProvisioning) {
                        AppSection(uiText(R.string.ui_project_setup_screen_011)) {
                            Text(state.hospitalName, fontWeight = com.hos.rushdpatients.ui.theme.UiTypography.emphasizedWeight)
                            Text(uiText(R.string.ui_project_setup_screen_012, com.hos.rushdpatients.ui.components.uiNumber(state.chatId)))
                            Text(if (state.encryptTelegram) uiText(R.string.ui_project_setup_screen_013) else uiText(R.string.ui_project_setup_screen_014))
                            Text(uiText(R.string.ui_project_setup_screen_015),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (state.mode == ProjectSetupMode.CREATE) {
                    AppSection(uiText(R.string.ui_project_setup_screen_016)) {
                        AppTextField(
                            value = state.hospitalName,
                            onValueChange = viewModel::setHospitalName,
                            label = { Text(uiText(R.string.ui_project_setup_screen_017)) },
                            singleLine = true,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PasswordField(
                            value = state.botToken,
                            onValueChange = viewModel::setBotToken,
                            label = uiText(R.string.ui_project_setup_screen_018),
                            enabled = !state.busy
                        )
                        NumberField(
                            value = state.chatId,
                            onValueChange = viewModel::setChatId,
                            label = uiText(R.string.ui_project_setup_screen_019),
                            signed = true,
                            enabled = !state.busy
                        )

                        AppTextButton(onClick = { showTopics = !showTopics }) {
                            Text(if (showTopics) uiText(R.string.ui_project_setup_screen_020) else uiText(R.string.ui_project_setup_screen_021))
                        }
                        if (showTopics) {
                            Text(
                                uiText(R.string.ui_project_setup_screen_022),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            NumberField(state.reportsTopicId, viewModel::setReportsTopicId, uiText(R.string.ui_project_setup_screen_023), enabled = !state.busy)
                            NumberField(state.csvTopicId, viewModel::setCsvTopicId, uiText(R.string.ui_project_setup_screen_025), enabled = !state.busy)
                            NumberField(state.doctorsTopicId, viewModel::setDoctorsTopicId, uiText(R.string.ui_project_setup_screen_026), enabled = !state.busy)
                        }
                    }
                }
            }

            if (state.mode == ProjectSetupMode.CREATE) {
                AppSection(uiText(R.string.ui_project_setup_screen_027)) {
                    AppToggleRow(title = uiText(R.string.ui_project_setup_screen_028), checked = state.encryptTelegram,
                        onCheckedChange = viewModel::setEncryptTelegram, enabled = !state.busy)
                    Text(uiText(R.string.ui_project_setup_screen_029),
                        style = MaterialTheme.typography.bodySmall)
                    Text(uiText(R.string.ui_project_setup_screen_030),
                        style = MaterialTheme.typography.bodySmall)
                }
                AppSection(uiText(R.string.ui_project_setup_screen_031)) {
                    AppTextField(
                        value = state.adminName,
                        onValueChange = viewModel::setAdminName,
                        label = { Text(uiText(R.string.ui_project_setup_screen_032)) },
                        singleLine = true,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    NumberField(
                        state.adminTelegramId,
                        viewModel::setAdminTelegramId,
                        uiText(R.string.ui_project_setup_screen_033),
                        enabled = !state.busy
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        AppFilterChip(
                            selected = state.adminGenderCode == "M",
                            onClick = { viewModel.setAdminGender("M") },
                            label = { Text(uiText(R.string.ui_male)) },
                            enabled = !state.busy
                        )
                        AppFilterChip(
                            selected = state.adminGenderCode == "F",
                            onClick = { viewModel.setAdminGender("F") },
                            label = { Text(uiText(R.string.ui_female)) },
                            enabled = !state.busy
                        )
                        AppFilterChip(
                            selected = state.adminClinicalRoleCode == "RESIDENT",
                            onClick = { viewModel.setAdminClinicalRole("RESIDENT") },
                            label = { Text(uiText(R.string.ui_project_setup_screen_034)) },
                            enabled = !state.busy
                        )
                        AppFilterChip(
                            selected = state.adminClinicalRoleCode == "SUPERVISOR",
                            onClick = { viewModel.setAdminClinicalRole("SUPERVISOR") },
                            label = { Text(uiText(R.string.ui_project_setup_screen_035)) },
                            enabled = !state.busy
                        )
                    }
                }

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

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    signed: Boolean = false,
    enabled: Boolean
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (signed) KeyboardType.Text else KeyboardType.Number
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
