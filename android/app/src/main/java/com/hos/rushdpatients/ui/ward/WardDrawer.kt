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
    var dataMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var appMenuExpanded by rememberSaveable { mutableStateOf(false) }

    ModalDrawerSheet(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Surface(
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

        Text("العمل اليومي", Modifier.padding(horizontal = UiSpacing.medium, vertical = UiSpacing.tiny),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WardDrawerRow("جميع المرضى", Icons.Filled.ViewAgenda, allPatientsSelected) { onAction(WardDrawerAction.ALL_PATIENTS) }
        WardDrawerRow("مرضاي", Icons.Filled.Person, viewMode == WardViewMode.MINE) { onAction(WardDrawerAction.MY_PATIENTS) }
        WardDrawerRow("لوحة تسليم المناوبة", Icons.Filled.Dashboard, viewMode == WardViewMode.DASHBOARD) { onAction(WardDrawerAction.DASHBOARD) }
        WardDrawerRow("خيارات العرض والتصفية", Icons.Filled.FilterList, filtersSelected) { onAction(WardDrawerAction.FILTERS) }
        WardDrawerRow("أطباء المناوبة", Icons.Filled.Groups, enabled = !state.isReadOnly && state.shift != null) { onAction(WardDrawerAction.SHIFT_DOCTORS) }
        WardDrawerRow(UiAction.SEND_REPORT.label, UiAction.SEND_REPORT.icon, enabled = state.shift != null) { onAction(WardDrawerAction.REPORT) }

        Divider(Modifier.padding(vertical = UiSpacing.tiny))
        DrawerSubmenuHeader(label = "البيانات والاستعادة", expanded = dataMenuExpanded,
            onClick = { dataMenuExpanded = !dataMenuExpanded })
        if (dataMenuExpanded) {
            WardDrawerRow("الجلب والنشر والنسخ الاحتياطي", Icons.Filled.FileDownload) { onAction(WardDrawerAction.DATA_MANAGEMENT) }
            WardDrawerRow("بدء مناوبة اليوم من السابقة", Icons.Filled.History,
                enabled = !state.isReadOnly && !state.syncing && !state.saving && state.patients.isEmpty()) { onAction(WardDrawerAction.START_SHIFT) }
            WardDrawerRow("نسخة نشر سابقة", Icons.Filled.History, enabled = !state.syncing && !state.saving) { onAction(WardDrawerAction.PREVIOUS) }
            WardDrawerRow("سلة المحذوفات", Icons.Filled.Recycling) { onAction(WardDrawerAction.RECYCLE_BIN) }
        }
        Divider(Modifier.padding(vertical = UiSpacing.tiny))
        DrawerSubmenuHeader(label = "التفضيلات والمشروع", expanded = appMenuExpanded,
            onClick = { appMenuExpanded = !appMenuExpanded })
        if (appMenuExpanded) {
            WardDrawerRow("المظهر وتفضيلات الجهاز", Icons.Filled.Settings) { onAction(WardDrawerAction.SETTINGS) }
            WardDrawerRow("اتصال المشروع", Icons.Filled.Groups) { onAction(WardDrawerAction.PROJECT) }
            if (isAdmin) {
                WardDrawerRow("إدارة الأطباء والمشروع", Icons.Filled.AdminPanelSettings) { onAction(WardDrawerAction.ADMIN) }
                WardDrawerRow("مركز النشاط والسجل", Icons.Filled.History) { onAction(WardDrawerAction.ACTIVITY) }
            }
            WardDrawerRow("حول التطبيق", Icons.Filled.Info) { onAction(WardDrawerAction.ABOUT) }
        }
    }
}

@Composable
private fun WardDrawerRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    CompactDrawerItem(icon = { Icon(icon, contentDescription = null) }, label = { Text(label) },
        selected = selected, enabled = enabled, onClick = onClick,
        modifier = Modifier.padding(horizontal = UiSpacing.medium))
}
