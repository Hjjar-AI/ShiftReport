package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.theme.UiTypography
import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextField
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.hos.rushdpatients.ui.components.AppDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.ui.components.LoadingButton
import com.hos.rushdpatients.ui.theme.UiSpacing

@Composable
internal fun SettingsSection(
    title: String,
    showHeader: Boolean,
    summary: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    onPositioned: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth().onGloballyPositioned { onPositioned(it.positionInParent().y.roundToInt()) }) {
        Column(Modifier.fillMaxWidth().padding(com.hos.rushdpatients.ui.theme.uiContentPadding()),
            verticalArrangement = Arrangement.spacedBy(com.hos.rushdpatients.ui.theme.uiContentGap())) {
            if (showHeader) com.hos.rushdpatients.ui.components.AppSectionHeader(title = title,
                expanded = if (onToggle == null) null else expanded, onToggle = onToggle, summary = if (expanded) null else summary)
            if (expanded) {
                if (showHeader) AppDivider()
                content()
            }
        }
    }
}

@Composable
internal fun SettingsSubheading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = UiTypography.emphasizedWeight
    )
}

@Composable
internal fun SupervisorGroupEditor(
    supervisor: Doctor,
    saving: Boolean,
    enabled: Boolean,
    onSave: (Doctor, String) -> Unit
) {
    val uiText = rememberUiStrings()
    var baseline by remember(supervisor.id) { mutableStateOf(supervisor) }
    var chatId by remember(supervisor.id) {
        mutableStateOf(supervisor.supervisorGroupChatId?.toString().orEmpty())
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UiSpacing.micro),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)
    ) {
        Text(supervisor.fullName, style = MaterialTheme.typography.bodyMedium)
        AppTextField(
            value = chatId,
            onValueChange = { value ->
                chatId = value.filter { it.isDigit() || it == '-' }
            },
            label = { Text(uiText(R.string.ui_settings_section_layout_001)) },
            supportingText = { Text("-1001234567890") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        if (supervisor != baseline) {
            AppNotice(uiText(R.string.ui_settings_section_layout_002), kind = com.hos.rushdpatients.ui.components.NoticeKind.WARNING)
            AppTextButton(onClick = {
                baseline = supervisor
                chatId = supervisor.supervisorGroupChatId?.toString().orEmpty()
            }, enabled = enabled) { Text(uiText(R.string.ui_settings_section_layout_003)) }
        }
        LoadingButton(
            outlined = true,
            text = if (chatId.isBlank()) uiText(R.string.ui_settings_section_layout_004) else uiText(R.string.ui_save),
            loading = saving,
            onClick = { onSave(baseline, chatId) },
            enabled = enabled && supervisor == baseline && chatId.trim() != supervisor.supervisorGroupChatId?.toString().orEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal enum class SettingsSectionId(
    @StringRes private val titleRes: Int,
    val workspace: SettingsWorkspace,
    val adminOnly: Boolean = false
) {
    APPEARANCE(R.string.ui_demo_secondary_screens_014, SettingsWorkspace.APPEARANCE),
    REPORT(R.string.ui_settings_section_layout_030, SettingsWorkspace.REPORTS),
    SYNC(R.string.ui_sync_center_patients, SettingsWorkspace.DATA),
    DOCTORS(R.string.ui_sync_center_doctors, SettingsWorkspace.DATA),
    FILES(R.string.ui_ward_drawer_014, SettingsWorkspace.DATA),
    BACKUP(R.string.ui_settings_section_layout_031, SettingsWorkspace.DATA),
    CONNECTION(R.string.ui_ward_drawer_019, SettingsWorkspace.PROJECT),
    INVITATIONS(R.string.ui_ward_drawer_022, SettingsWorkspace.PROJECT, true),
    DESTINATIONS(R.string.ui_ward_drawer_021, SettingsWorkspace.PROJECT, true),
    RESET(R.string.settings_advanced_admin, SettingsWorkspace.DATA, true),
    SECURITY(R.string.ui_settings_section_layout_032, SettingsWorkspace.SECURITY),
    STORAGE(R.string.ui_settings_section_layout_033, SettingsWorkspace.DIAGNOSTICS);

    fun title(uiText: UiStrings): String = uiText(titleRes)
}

internal fun SettingsSectionId.summary(uiText: UiStrings, state: SettingsUiState): String = when (this) {
    SettingsSectionId.APPEARANCE -> uiText(R.string.ui_settings_section_layout_005, state.appearance.arabicLabel(uiText), state.appTheme.arabicLabel(uiText), state.fontScale.arabicLabel(uiText))
    SettingsSectionId.REPORT -> if (state.reportAsPdf) "PDF · ${state.pdfStyle.uiLabel(uiText)}" else uiText(R.string.ui_settings_section_layout_016)
    SettingsSectionId.SYNC -> syncCenterSummary(uiText, state.patientsPending, state.lastCsvSync)
    SettingsSectionId.DOCTORS -> syncCenterSummary(uiText, state.doctorsPending, state.lastDoctorsSync)
    SettingsSectionId.FILES -> uiText(R.string.ui_settings_section_layout_018)
    SettingsSectionId.BACKUP -> uiText(R.string.ui_settings_section_layout_019)
    SettingsSectionId.CONNECTION -> uiText(R.string.ui_settings_section_layout_020)
    SettingsSectionId.INVITATIONS -> uiText(R.string.ui_settings_section_layout_021)
    SettingsSectionId.DESTINATIONS -> uiText(R.string.ui_settings_section_layout_022)
    SettingsSectionId.RESET -> uiText(R.string.ui_settings_section_layout_023)
    SettingsSectionId.SECURITY -> uiText(R.string.ui_settings_section_layout_024, state.autoLockMinutes) + if (state.biometricEnabled) uiText(R.string.ui_settings_section_layout_025) else uiText(R.string.ui_settings_section_layout_026)
    SettingsSectionId.STORAGE -> uiText(R.string.ui_settings_section_layout_027, state.databaseSize, state.storedShifts)
}

private fun syncCenterSummary(uiText: UiStrings, pending: Boolean?, lastContact: String?): String =
    listOf(uiText(when (pending) {
        true -> R.string.ui_sync_center_pending_short
        false -> R.string.ui_sync_center_local_short
        null -> R.string.ui_sync_center_loading
    }), uiText(R.string.ui_sync_center_last_short, lastContact ?: uiText(R.string.ui_unknown))).joinToString(" · ")
