package com.hos.rushdpatients.ui.ward

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
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
    REPORT, ASSIGNMENTS, SETTINGS, DATA_MANAGEMENT,
    RECYCLE_BIN, ADMIN, PROJECT, ABOUT,
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
    additionalActions: @Composable () -> Unit = {}
) {
    val uiText = rememberUiStrings()
    val dayLabel = LocalWardDayLabel.current
    var preferencesMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var helpMenuExpanded by rememberSaveable { mutableStateOf(false) }

    ModalDrawerSheet {
        BoxWithConstraints(Modifier.fillMaxHeight()) {
            // Keep navigation usable on short windows/large fonts; identity stays in its own viewport.
            val headerMaxHeight = maxHeight * 0.4f
            Column(Modifier.fillMaxHeight().padding(horizontal = UiSpacing.section, vertical = UiSpacing.screen)) {
                Column(Modifier.heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState())) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = com.hos.rushdpatients.ui.theme.UiOpacity.containerMuted),
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
                                    Text(uiNumber(dayLabel),
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
                    com.hos.rushdpatients.ui.components.AppButton(
                        onClick = { onAction(WardDrawerAction.REPORT) }, enabled = state.shift != null,
                        modifier = Modifier.fillMaxWidth()) {
                        com.hos.rushdpatients.ui.components.AppActionLabel(UiAction.SEND_REPORT)
                    }

                    if (isAdmin) com.hos.rushdpatients.ui.components.AppOutlinedButton(
                        onClick = { onAction(WardDrawerAction.ASSIGNMENTS) }, enabled = state.shift != null,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(uiText(R.string.assignments_export_title))
                    }

                    WardDrawerRow(uiText(R.string.ui_ward_drawer_013), AppIcons.Sync) { onAction(WardDrawerAction.DATA_MANAGEMENT) }
                    WardDrawerRow(uiText(R.string.drawer_recycle_bin), AppIcons.Recycling) { onAction(WardDrawerAction.RECYCLE_BIN) }
                    DrawerToolGroup(uiText(R.string.ui_ward_drawer_017), AppIcons.Settings, preferencesMenuExpanded,
                        onToggle = { preferencesMenuExpanded = !preferencesMenuExpanded }) {
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_018), AppIcons.Settings) { onAction(WardDrawerAction.SETTINGS) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_019), AppIcons.Link) { onAction(WardDrawerAction.PROJECT) }
                    }
                    if (isAdmin) {
                        WardDrawerRow(uiText(R.string.ui_admin_screen_001), AppIcons.AdminPanelSettings) { onAction(WardDrawerAction.ADMIN) }
                    }
                    additionalActions()
                    DrawerToolGroup(uiText(R.string.ui_ward_drawer_026), AppIcons.HelpOutline, helpMenuExpanded,
                        onToggle = { helpMenuExpanded = !helpMenuExpanded }) {
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_027), AppIcons.Build) { onAction(WardDrawerAction.DIAGNOSTICS) }
                        WardDrawerRow(uiText(R.string.ui_ward_drawer_028), AppIcons.Info) { onAction(WardDrawerAction.ABOUT) }
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
    Surface(shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = com.hos.rushdpatients.ui.theme.UiOpacity.containerSoft)) {
        Column(Modifier.animateContentSize(tween(com.hos.rushdpatients.ui.theme.LocalUiStyle.current.expandMillis))) {
            com.hos.rushdpatients.ui.components.AppSectionHeader(label, icon = icon,
                expanded = expanded, onToggle = onToggle,
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = UiSpacing.screen))
            if (expanded) Column(Modifier.padding(start = UiSpacing.section, end = UiSpacing.small,
                bottom = UiSpacing.small)) { content() }
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
                    else colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else UiOpacity.disabledContent))
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) UiTypography.emphasizedWeight else FontWeight.Normal)
        }
    }
}
