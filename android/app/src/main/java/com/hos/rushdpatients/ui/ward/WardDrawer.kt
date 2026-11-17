package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppCollapsibleSection
import com.hos.rushdpatients.ui.components.AppText

import com.hos.rushdpatients.ui.components.AppIcons
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiOpacity
import com.hos.rushdpatients.ui.theme.UiSpacing

internal enum class WardDrawerAction {
    REPORT, DELIVERY_HISTORY, ASSIGNMENTS, SETTINGS, DATA_MANAGEMENT,
    RECYCLE_BIN, ADMIN, PROJECT, ABOUT, GUIDE,
    FILES, BACKUP, INVITATIONS, DESTINATIONS, RESET, DIAGNOSTICS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WardDrawer(
    state: WardUiState,
    currentDoctorName: String?,
    isAdmin: Boolean,
    isPermanentAdmin: Boolean,
    online: Boolean,
    onAction: (WardDrawerAction) -> Unit,
    onPublishData: () -> Unit = {},
    additionalActions: @Composable () -> Unit = {},
    uploadAvailable: Boolean = online
) {
    val uiText = rememberUiStrings()
    val dayLabel = LocalWardDayLabel.current
    var dataFilesMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var preferencesMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var helpMenuExpanded by rememberSaveable { mutableStateOf(false) }

    ModalDrawerSheet {
        BoxWithConstraints(Modifier.fillMaxHeight()) {
            // Keep navigation usable on short windows/large fonts; identity stays in its own viewport.
            val headerMaxHeight = maxHeight * 0.4f
            Column(Modifier.fillMaxHeight().padding(horizontal = UiSpacing.section, vertical = UiSpacing.screen)) {
                Column(Modifier.heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState())) {
                    com.hos.rushdpatients.ui.components.AppInsetSurface(com.hos.rushdpatients.ui.components.InsetLevel.MUTED,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(UiSpacing.section),
                            verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.section),
                                verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                    AppText(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                    AppText(state.hospitalName.ifBlank { uiText(R.string.ui_ward_drawer_001) },
                                        style = MaterialTheme.typography.titleMedium, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                    AppText(uiText(R.string.ui_ward_drawer_002, uiNumber(state.patients.size)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                                    AppText(if (isPermanentAdmin) uiText(R.string.ui_ward_drawer_003) else if (isAdmin) uiText(R.string.ui_ward_drawer_004) else uiText(R.string.ui_ward_drawer_005),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    AppText(currentDoctorName ?: uiText(R.string.ui_ward_drawer_006),
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                                        textAlign = TextAlign.End, overflow = TextOverflow.Ellipsis)
                                    AppText(uiNumber(dayLabel),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            AppText(if (online) state.syncStatus.arabicLabel(uiText) else uiText(R.string.ui_ward_drawer_007),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(top = UiSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.screen)) {
                    DrawerSectionLabel(uiText(R.string.drawer_quick_actions))
                    com.hos.rushdpatients.ui.components.AppButton(
                        onClick = { onAction(WardDrawerAction.REPORT) }, enabled = state.shift != null,
                        modifier = Modifier.fillMaxWidth()) {
                        com.hos.rushdpatients.ui.components.AppActionLabel(UiAction.SEND_REPORT)
                    }

                    com.hos.rushdpatients.ui.components.AppOutlinedButton(
                        onClick = onPublishData,
                        enabled = state.shift != null && uploadAvailable && !state.loading && !state.saving && !state.syncing,
                        modifier = Modifier.fillMaxWidth()) {
                        AppText(uiText(R.string.sync_upload_data_only))
                    }

                    if (isAdmin) com.hos.rushdpatients.ui.components.AppOutlinedButton(
                        onClick = { onAction(WardDrawerAction.ASSIGNMENTS) }, enabled = state.shift != null,
                        modifier = Modifier.fillMaxWidth()) {
                        AppText(uiText(R.string.assignments_export_title))
                    }

                    com.hos.rushdpatients.ui.components.AppDivider()
                    DrawerToolGroup(uiText(R.string.drawer_data_files), AppIcons.FolderOpen, dataFilesMenuExpanded,
                        onToggle = { dataFilesMenuExpanded = !dataFilesMenuExpanded }) {
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_013), AppIcons.Sync) { onAction(WardDrawerAction.DATA_MANAGEMENT) }
                        WardDrawerRow(uiText(R.string.ui_ward_synchronization_006), AppIcons.History,
                            enabled = state.shift != null) { onAction(WardDrawerAction.DELIVERY_HISTORY) }
                        WardDrawerRow(uiText(R.string.drawer_exports_imports), AppIcons.FolderOpen) { onAction(WardDrawerAction.FILES) }
                        WardDrawerRow(uiText(R.string.ui_settings_section_layout_031), AppIcons.Backup) { onAction(WardDrawerAction.BACKUP) }
                        WardDrawerRow(uiText(R.string.drawer_recycle_bin), AppIcons.Recycling) { onAction(WardDrawerAction.RECYCLE_BIN) }
                    }

                    DrawerToolGroup(uiText(R.string.drawer_settings_administration), AppIcons.Settings, preferencesMenuExpanded,
                        onToggle = { preferencesMenuExpanded = !preferencesMenuExpanded }) {
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_018), AppIcons.Settings) { onAction(WardDrawerAction.SETTINGS) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_019), AppIcons.Link) { onAction(WardDrawerAction.PROJECT) }
                        if (isAdmin) WardDrawerRow(uiText(R.string.ui_admin_screen_001), AppIcons.AdminPanelSettings) {
                            onAction(WardDrawerAction.ADMIN)
                        }
                    }
                    DrawerToolGroup(uiText(R.string.ui_ward_drawer_026), AppIcons.HelpOutline, helpMenuExpanded,
                        onToggle = { helpMenuExpanded = !helpMenuExpanded }) {
                        WardDrawerRow(uiText(R.string.guide_title), AppIcons.HelpOutline) { onAction(WardDrawerAction.GUIDE) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_027), AppIcons.Build) { onAction(WardDrawerAction.DIAGNOSTICS) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_028), AppIcons.Info) { onAction(WardDrawerAction.ABOUT) }
                    }
                    additionalActions()
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(label: String) {
    AppText(label, modifier = Modifier.padding(horizontal = UiSpacing.screen).semantics { heading() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** All navigation categories share the same heading, disclosure and child-row treatment. */
@Composable
private fun DrawerToolGroup(label: String, icon: ImageVector, expanded: Boolean,
    onToggle: () -> Unit, content: @Composable () -> Unit) {
    com.hos.rushdpatients.ui.components.AppInsetSurface(com.hos.rushdpatients.ui.components.InsetLevel.SOFT,
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.animateContentSize(tween(com.hos.rushdpatients.ui.theme.LocalUiStyle.current.expandMillis))) {
            AppCollapsibleSection(expanded = expanded, header = {
                com.hos.rushdpatients.ui.components.AppSectionHeader(label, icon = icon,
                    expanded = expanded, onToggle = onToggle,
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = UiSpacing.screen))
            }) {
                Column(Modifier.padding(start = UiSpacing.section, end = UiSpacing.small,
                    bottom = UiSpacing.small)) { content() }
            }
        }
    }
}

@Composable
private fun WardDrawerRow(label: String, icon: ImageVector,
    selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    com.hos.rushdpatients.ui.components.AppSelectableSurface(selected = selected, onClick = onClick,
        enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, unselectedColor = Color.Transparent) {
        Row(Modifier.padding(horizontal = UiSpacing.section, vertical = UiSpacing.screen),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.section),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(com.hos.rushdpatients.ui.theme.UiSize.iconMedium),
                tint = if (selected) LocalContentColor.current
                    else colors.onSurfaceVariant.copy(alpha = if (enabled) UiOpacity.enabled else UiOpacity.disabledContent))
            AppText(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) UiTypography.emphasizedWeight else FontWeight.Normal)
        }
    }
}
