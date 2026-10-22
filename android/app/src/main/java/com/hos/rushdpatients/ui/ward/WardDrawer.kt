package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiTypography
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiOpacity
import com.hos.rushdpatients.ui.theme.UiSpacing

internal enum class WardDrawerAction {
    ALL_PATIENTS, REPORT, ACTIVITY, SETTINGS, DATA_MANAGEMENT, DASHBOARD, MY_PATIENTS, FILTERS,
    SHIFT_DOCTORS, PREVIOUS, RECYCLE_BIN, ADMIN, PROJECT, ABOUT, START_SHIFT,
    FILES, BACKUP, INVITATIONS, DESTINATIONS, RESET, DIAGNOSTICS, ANNOUNCEMENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WardDrawer(
    state: WardUiState,
    currentDoctorName: String?,
    isAdmin: Boolean,
    isPermanentAdmin: Boolean,
    online: Boolean,
    viewMode: WardViewMode,
    allPatientsSelected: Boolean,
    filtersSelected: Boolean,
    onAction: (WardDrawerAction) -> Unit
) {
    val uiText = rememberUiStrings()
    var dailyMenuExpanded by rememberSaveable { mutableStateOf(true) }
    var preferencesMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var dataMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var adminMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var helpMenuExpanded by rememberSaveable { mutableStateOf(false) }

    ModalDrawerSheet {
        BoxWithConstraints(Modifier.fillMaxHeight()) {
            // Keep navigation usable on short windows/large fonts; identity stays in its own viewport.
            val headerMaxHeight = maxHeight * 0.4f
            Column(Modifier.fillMaxHeight().padding(horizontal = UiSpacing.section, vertical = UiSpacing.screen)) {
                Column(Modifier.heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState())) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                        Column(Modifier.padding(UiSpacing.section),
                            verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.section),
                                verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                    Text(state.hospitalName.ifBlank { uiText(R.string.ui_ward_drawer_001) },
                                        style = MaterialTheme.typography.titleMedium, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(uiText(R.string.ui_ward_drawer_002, uiNumber(state.patients.size)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                    Text(if (isPermanentAdmin) uiText(R.string.ui_ward_drawer_003) else if (isAdmin) uiText(R.string.ui_ward_drawer_004) else uiText(R.string.ui_ward_drawer_005),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(currentDoctorName ?: uiText(R.string.ui_ward_drawer_006),
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                                        textAlign = TextAlign.End, overflow = TextOverflow.Ellipsis)
                                    Text(uiNumber(state.shift?.date ?: "—"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(if (online) state.syncStatus.arabicLabel(uiText) else uiText(R.string.ui_ward_drawer_007),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(top = UiSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
                    DrawerToolGroup(uiText(R.string.ui_ward_drawer_008), Icons.Filled.ViewAgenda, dailyMenuExpanded,
                        onToggle = { dailyMenuExpanded = !dailyMenuExpanded }) {
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_009), Icons.Filled.ViewAgenda, allPatientsSelected) { onAction(WardDrawerAction.ALL_PATIENTS) }
                        WardDrawerRow(uiText(R.string.ui_handover_dashboard_011), Icons.Filled.Person, viewMode == WardViewMode.MINE) { onAction(WardDrawerAction.MY_PATIENTS) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_010), Icons.Filled.Dashboard, viewMode == WardViewMode.DASHBOARD) { onAction(WardDrawerAction.DASHBOARD) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_011), Icons.Filled.FilterList, filtersSelected) { onAction(WardDrawerAction.FILTERS) }
                        WardDrawerRow(uiText(R.string.ui_shift_doctor_picker_001), Icons.Filled.Groups, enabled = !state.isReadOnly && state.shift != null) { onAction(WardDrawerAction.SHIFT_DOCTORS) }
                        WardDrawerRow(UiAction.SEND_REPORT.label(uiText), UiAction.SEND_REPORT.icon,
                            enabled = state.shift != null) { onAction(WardDrawerAction.REPORT) }
                    }

                    DrawerToolGroup(uiText(R.string.ui_ward_drawer_012), Icons.Filled.Folder, dataMenuExpanded,
                        onToggle = { dataMenuExpanded = !dataMenuExpanded }) {
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_013), Icons.Filled.Sync) { onAction(WardDrawerAction.DATA_MANAGEMENT) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_014), Icons.Filled.Description) { onAction(WardDrawerAction.FILES) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_015), Icons.Filled.Backup) { onAction(WardDrawerAction.BACKUP) }
                        WardDrawerRow(uiText(R.string.ui_ward_review_dialogs_001), Icons.Filled.History,
                            enabled = !state.isReadOnly && !state.syncing && !state.saving && state.patients.isEmpty()) { onAction(WardDrawerAction.START_SHIFT) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_016), Icons.Filled.History, enabled = !state.syncing && !state.saving) { onAction(WardDrawerAction.PREVIOUS) }
                        WardDrawerRow(uiText(R.string.ui_ward_review_dialogs_009), Icons.Filled.Recycling) { onAction(WardDrawerAction.RECYCLE_BIN) }
                    }
                    DrawerToolGroup(uiText(R.string.ui_ward_drawer_017), Icons.Filled.Settings, preferencesMenuExpanded,
                        onToggle = { preferencesMenuExpanded = !preferencesMenuExpanded }) {
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_018), Icons.Filled.Settings) { onAction(WardDrawerAction.SETTINGS) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_019), Icons.Filled.Link) { onAction(WardDrawerAction.PROJECT) }
                    }
                    if (isAdmin) {
                        DrawerToolGroup(uiText(R.string.ui_ward_drawer_020), Icons.Filled.AdminPanelSettings, adminMenuExpanded,
                            onToggle = { adminMenuExpanded = !adminMenuExpanded }) {
                            WardDrawerRow(uiText(R.string.ui_admin_screen_001), Icons.Filled.Groups) { onAction(WardDrawerAction.ADMIN) }
                            WardDrawerRow(uiText(R.string.ui_ward_drawer_021), Icons.Filled.PictureAsPdf) { onAction(WardDrawerAction.DESTINATIONS) }
                            WardDrawerRow(uiText(R.string.ui_ward_drawer_022), Icons.Filled.PersonAdd) { onAction(WardDrawerAction.INVITATIONS) }
                            WardDrawerRow(uiText(R.string.ui_ward_drawer_023), Icons.Filled.Campaign) { onAction(WardDrawerAction.ANNOUNCEMENT) }
                            WardDrawerRow(uiText(R.string.ui_ward_drawer_024), Icons.Filled.History) { onAction(WardDrawerAction.ACTIVITY) }
                            if (isPermanentAdmin) WardDrawerRow(uiText(R.string.ui_ward_drawer_025), Icons.Filled.RestartAlt) { onAction(WardDrawerAction.RESET) }
                        }
                    }
                    DrawerToolGroup(uiText(R.string.ui_ward_drawer_026), Icons.Filled.HelpOutline, helpMenuExpanded,
                        onToggle = { helpMenuExpanded = !helpMenuExpanded }) {
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_027), Icons.Filled.Build) { onAction(WardDrawerAction.DIAGNOSTICS) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_028), Icons.Filled.Info) { onAction(WardDrawerAction.ABOUT) }
                    }
                }
            }
        }
    }
}

/** All navigation categories share the same heading, disclosure and child-row treatment. */
@Composable
private fun DrawerToolGroup(label: String, icon: ImageVector, expanded: Boolean,
    onToggle: () -> Unit, content: @Composable () -> Unit) {
    val uiText = rememberUiStrings()
    Surface(shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
        Column {
            Surface(onClick = onToggle, color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
                    .semantics { stateDescription = if (expanded) uiText(R.string.ui_ward_drawer_029) else uiText(R.string.ui_patient_form_dialog_024) }) {
                Row(Modifier.padding(UiSpacing.section), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(com.hos.rushdpatients.ui.theme.UiSize.iconMedium),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = UiTypography.emphasizedWeight)
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) uiText(R.string.ui_ward_drawer_030, label) else uiText(R.string.ui_ward_drawer_031, label),
                        modifier = Modifier.size(com.hos.rushdpatients.ui.theme.UiSize.iconMedium), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded) Column(Modifier.padding(start = UiSpacing.section, end = UiSpacing.small,
                bottom = UiSpacing.small)) { content() }
        }
    }
}

@Composable
private fun WardDrawerRow(label: String, icon: ImageVector,
    selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val foreground = (if (selected) colors.onPrimaryContainer else colors.onSurface)
        .let { if (enabled) it else it.copy(alpha = UiOpacity.disabledContent) }
    Surface(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touchTarget)
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else Color.Transparent,
        contentColor = foreground) {
        Row(Modifier.padding(horizontal = UiSpacing.section, vertical = UiSpacing.screen),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.section),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(com.hos.rushdpatients.ui.theme.UiSize.iconMedium),
                tint = if (selected) LocalContentColor.current
                    else colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else UiOpacity.disabledContent))
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) UiTypography.emphasizedWeight else FontWeight.Normal)
        }
    }
}
