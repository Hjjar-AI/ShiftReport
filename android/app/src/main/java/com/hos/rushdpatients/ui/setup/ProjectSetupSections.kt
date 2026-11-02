package com.hos.rushdpatients.ui.setup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*

/** Presentation only; the screen retains launchers, expansion state and ViewModel callbacks. */
@Composable
internal fun ProjectJoinSections(
    state: ProjectSetupUiState,
    onSelectProvisioning: () -> Unit,
    onSetProvisioningPassphrase: (String) -> Unit,
    onUnlockProvisioning: () -> Unit
) {
    val uiText = rememberUiStrings()
    AppSection(uiText(R.string.ui_project_setup_screen_006)) {
        Text(
            uiText(R.string.ui_project_setup_screen_007),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppButton(
            onClick = onSelectProvisioning,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.joinFileSelected) uiText(R.string.ui_project_setup_screen_009) else uiText(R.string.ui_project_setup_screen_010)) }
        if (state.joinFileSelected && !state.importedProvisioning) Text(uiText(R.string.setup_join_file_selected),
            style = MaterialTheme.typography.bodySmall)
        PasswordField(
            value = state.provisioningPassphrase,
            onValueChange = onSetProvisioningPassphrase,
            label = uiText(R.string.ui_project_setup_screen_008),
            enabled = !state.busy
        )
        if (!state.importedProvisioning) AppButton(
            onClick = onUnlockProvisioning,
            enabled = !state.busy && state.joinFileSelected &&
                state.provisioningPassphrase.length >= com.hos.rushdpatients.config.ProjectProvisioningManager.MIN_PASSWORD_LENGTH,
            modifier = Modifier.fillMaxWidth()
        ) { Text(uiText(R.string.setup_unlock_join_file)) }
        else Text(uiText(R.string.setup_join_file_unlocked), style = MaterialTheme.typography.bodySmall)
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

@Composable
internal fun ProjectCreateSections(
    state: ProjectSetupUiState,
    showTopics: Boolean,
    onToggleTopics: () -> Unit,
    showResidents: Boolean,
    onToggleResidents: () -> Unit,
    onSetHospitalName: (String) -> Unit,
    onSetBotToken: (String) -> Unit,
    onSetChatId: (String) -> Unit,
    onSetReportsTopicId: (String) -> Unit,
    onSetCsvTopicId: (String) -> Unit,
    onSetDoctorsTopicId: (String) -> Unit,
    onSetResidentsChatId: (String) -> Unit,
    onSetResidentsTopicId: (String) -> Unit,
    onSetAdminName: (String) -> Unit,
    onSetAdminTelegramId: (String) -> Unit,
    onSetAdminGender: (String) -> Unit,
    onSetAdminClinicalRole: (String) -> Unit,
    onSetEncryptTelegram: (Boolean) -> Unit
) {
    val uiText = rememberUiStrings()
    AppNotice(uiText(R.string.setup_create_overview), kind = NoticeKind.ACCENT)
    AppSection(uiText(R.string.ui_project_setup_screen_016)) {
        AppTextField(
            value = state.hospitalName,
            onValueChange = onSetHospitalName,
            label = { Text(uiText(R.string.ui_project_setup_screen_017)) },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
        Text(uiText(R.string.setup_prepare_bot), style = MaterialTheme.typography.bodySmall)
        PasswordField(
            value = state.botToken,
            onValueChange = onSetBotToken,
            label = uiText(R.string.ui_project_setup_screen_018),
            enabled = !state.busy
        )
        Text(uiText(R.string.setup_prepare_group), style = MaterialTheme.typography.bodySmall)
        TelegramIdField(
            value = state.chatId,
            onValueChange = onSetChatId,
            label = uiText(R.string.ui_project_setup_screen_019),
            signed = true,
            enabled = !state.busy,
            hint = uiText(R.string.setup_group_id_help)
        )
        Text(uiText(R.string.setup_group_topic_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        AppTextButton(onClick = onToggleTopics, enabled = !state.busy) {
            Text(if (showTopics) uiText(R.string.ui_project_setup_screen_020) else uiText(R.string.ui_project_setup_screen_021))
        }
        if (showTopics) {
            Text(
                uiText(R.string.ui_project_setup_screen_022),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelegramIdField(state.reportsTopicId, onSetReportsTopicId, uiText(R.string.ui_project_setup_screen_023), enabled = !state.busy,
                hint = uiText(R.string.setup_report_topic_help))
            TelegramIdField(state.csvTopicId, onSetCsvTopicId, uiText(R.string.ui_project_setup_screen_025), enabled = !state.busy,
                hint = uiText(R.string.setup_data_topic_help))
            TelegramIdField(state.doctorsTopicId, onSetDoctorsTopicId, uiText(R.string.ui_project_setup_screen_026), enabled = !state.busy,
                hint = uiText(R.string.setup_doctors_topic_help))
        }
        if (!showTopics && listOf(state.reportsTopicId, state.csvTopicId, state.doctorsTopicId).any { it.isNotBlank() }) {
            Text(uiText(R.string.setup_topics_retained), style = MaterialTheme.typography.bodySmall)
        }
        AppTextButton(onClick = onToggleResidents, enabled = !state.busy) {
            Text(uiText(if (showResidents) R.string.setup_hide_residents else R.string.setup_show_residents))
        }
        if (showResidents) {
            Text(uiText(R.string.residents_destination_hint), style = MaterialTheme.typography.bodySmall)
            TelegramIdField(state.residentsChatId, onSetResidentsChatId,
                uiText(R.string.residents_group_optional), signed = true, enabled = !state.busy,
                hint = uiText(R.string.setup_residents_group_help))
            TelegramIdField(state.residentsTopicId, onSetResidentsTopicId,
                uiText(R.string.residents_topic_optional), enabled = !state.busy,
                hint = uiText(R.string.setup_residents_topic_help))
        } else if (state.residentsChatId.isNotBlank()) {
            Text(uiText(R.string.setup_residents_retained), style = MaterialTheme.typography.bodySmall)
        }
    }
    AppSection(uiText(R.string.ui_project_setup_screen_031)) {
        AppTextField(
            value = state.adminName,
            onValueChange = onSetAdminName,
            label = { Text(uiText(R.string.ui_project_setup_screen_032)) },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
        TelegramIdField(
            state.adminTelegramId,
            onSetAdminTelegramId,
            uiText(R.string.ui_project_setup_screen_033),
            enabled = !state.busy,
            hint = uiText(R.string.setup_admin_id_help)
        )
        AppChoiceChips(options = listOf("M", "F"), selected = state.adminGenderCode,
            onSelect = onSetAdminGender, enabled = !state.busy,
            title = uiText(R.string.ui_patient_demographic_fields_010),
            label = { uiText(if (it == "M") R.string.ui_male else R.string.ui_female) })
        AppChoiceChips(options = listOf("RESIDENT", "SUPERVISOR"), selected = state.adminClinicalRoleCode,
            onSelect = onSetAdminClinicalRole, enabled = !state.busy,
            title = uiText(R.string.setup_admin_role),
            label = { uiText(if (it == "RESIDENT") R.string.ui_project_setup_screen_034 else R.string.ui_project_setup_screen_035) })
    }
    AppSection(uiText(R.string.ui_project_setup_screen_027)) {
        AppToggleRow(title = uiText(R.string.ui_project_setup_screen_028), checked = state.encryptTelegram,
            onCheckedChange = onSetEncryptTelegram, enabled = !state.busy)
        Text(uiText(R.string.ui_project_setup_screen_029),
            style = MaterialTheme.typography.bodySmall)
        Text(uiText(R.string.ui_project_setup_screen_030),
            style = MaterialTheme.typography.bodySmall)
    }
}
