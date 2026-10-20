package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiAction
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.R

internal enum class WardDrawerAction {
    ALL_PATIENTS, REPORT, ACTIVITY, SETTINGS, DATA_MANAGEMENT, DASHBOARD, MY_PATIENTS, FILTERS,
    SHIFT_DOCTORS, PREVIOUS, RECYCLE_BIN, ADMIN, PROJECT, ABOUT, START_SHIFT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WardDrawer(
    state: WardUiState,
    currentDoctorName: String?,
    isAdmin: Boolean,
    online: Boolean,
    viewMode: WardViewMode,
    allPatientsSelected: Boolean,
    filtersSelected: Boolean,
    onAction: (WardDrawerAction) -> Unit
) {
    var wardMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var appMenuExpanded by rememberSaveable { mutableStateOf(false) }

    ModalDrawerSheet(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Surface(
            onClick = {
                onAction(WardDrawerAction.DATA_MANAGEMENT)
            },
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Column(Modifier.padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(state.hospitalName.ifBlank { stringResource(R.string.app_name) },
                            style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (state.hospitalName.isNotBlank()) Text(stringResource(R.string.app_name),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(currentDoctorName ?: "مستخدم غير محدد",
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("${if (isAdmin) "مدير" else "عضو الفريق"} · ${uiNumber(state.patients.size)} مريض",
                            style = MaterialTheme.typography.labelSmall)
                        Text("${uiNumber(state.shift?.date ?: "—")}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PatientSyncNotice(status = state.syncStatus, freshnessLabel = state.syncStatus.arabicLabel,
                    online = online, compact = true)
            }
        }

        CompactDrawerItem(
                icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
                label = { Text("جميع المرضى") },
                selected = allPatientsSelected,
                onClick = { onAction(WardDrawerAction.ALL_PATIENTS) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium)
            )
        CompactDrawerItem(
                icon = { Icon(UiAction.SEND_REPORT.icon, contentDescription = null) },
                label = { Text(UiAction.SEND_REPORT.label) },
                selected = false,
                onClick = { onAction(WardDrawerAction.REPORT) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium),
                enabled = state.shift != null
            )
        if (isAdmin) CompactDrawerItem(
                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                label = { Text("مركز النشاط والسجل") },
                selected = false,
                onClick = { onAction(WardDrawerAction.ACTIVITY) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium)
            )
        Divider()
        DrawerSubmenuHeader(
            label = "المناوبة والمرضى",
            expanded = wardMenuExpanded,
            onClick = {
                wardMenuExpanded = !wardMenuExpanded
                if (wardMenuExpanded) {
                    appMenuExpanded = false
                }
            }
        )
        if (wardMenuExpanded) {

            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                label = { Text("لوحة تسليم المناوبة") },
                selected = viewMode == WardViewMode.DASHBOARD,
                onClick = { onAction(WardDrawerAction.DASHBOARD) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium)
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                label = { Text("مرضاي") },
                selected = viewMode == WardViewMode.MINE,
                onClick = { onAction(WardDrawerAction.MY_PATIENTS) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium)
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
                label = { Text("خيارات العرض") },
                selected = filtersSelected,
                onClick = { onAction(WardDrawerAction.FILTERS) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium)
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                label = { Text("أطباء المناوبة") },
                selected = false,
                onClick = { if (!state.isReadOnly) onAction(WardDrawerAction.SHIFT_DOCTORS) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium),
                enabled = !state.isReadOnly
            )
        }

        if (wardMenuExpanded) {
            CompactDrawerItem(icon = { Icon(Icons.Filled.History, contentDescription = null) },
                label = { Text("نسخة نشر سابقة") }, selected = false, enabled = !state.syncing && !state.saving,
                onClick = { onAction(WardDrawerAction.PREVIOUS) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium))
            if (!state.isReadOnly) CompactDrawerItem(
                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                label = { Text("بدء مناوبة اليوم من السابقة") }, selected = false,
                enabled = !state.syncing && !state.saving && state.patients.isEmpty(),
                onClick = { onAction(WardDrawerAction.START_SHIFT) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium))
            CompactDrawerItem(icon = { Icon(Icons.Filled.Recycling, contentDescription = null) },
                label = { Text("سلة المحذوفات") }, selected = false,
                onClick = { onAction(WardDrawerAction.RECYCLE_BIN) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium))
        }
        Divider(modifier = Modifier.padding(vertical = UiSpacing.tiny))
        CompactDrawerItem(
            icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
            label = { Text("إدارة البيانات") }, selected = false,
            onClick = { onAction(WardDrawerAction.DATA_MANAGEMENT) },
            modifier = Modifier.padding(horizontal = UiSpacing.medium))

        Divider(modifier = Modifier.padding(vertical = UiSpacing.tiny))
        DrawerSubmenuHeader(
            label = "الإعدادات والمشروع",
            expanded = appMenuExpanded,
            onClick = {
                appMenuExpanded = !appMenuExpanded
                if (appMenuExpanded) wardMenuExpanded = false
            }
        )
        if (appMenuExpanded) {
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                label = { Text("الإعدادات") },
                selected = false,
                onClick = { onAction(WardDrawerAction.SETTINGS) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium)
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                label = { Text("إدارة المشروع") },
                selected = false,
                onClick = { onAction(WardDrawerAction.PROJECT) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium)
            )
            if (isAdmin) CompactDrawerItem(
                icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
                label = { Text("لوحة المدير") },
                selected = false,
                onClick = { onAction(WardDrawerAction.ADMIN) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium)
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                label = { Text("حول التطبيق") },
                selected = false,
                onClick = { onAction(WardDrawerAction.ABOUT) },
                modifier = Modifier.padding(horizontal = UiSpacing.medium)
            )
        }
    }
}
