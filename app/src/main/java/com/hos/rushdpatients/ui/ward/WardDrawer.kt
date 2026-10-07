package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.ui.theme.LocalClinicalColors

internal enum class WardDrawerAction {
    ALL_PATIENTS, REPORT, ACTIVITY, SETTINGS, DASHBOARD, MY_PATIENTS, FILTERS,
    SORT, GROUP, SHIFT_DOCTORS, SYNC_DETAILS, SYNC, PREVIOUS, DISMISS_SYNC_HINT,
    EXPORT_CSV, RECYCLE_BIN, ADMIN, IMPORT, ABOUT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WardDrawer(
    state: WardUiState,
    currentDoctorName: String?,
    isAdmin: Boolean,
    online: Boolean,
    freshnessLabel: String,
    viewMode: WardViewMode,
    allPatientsSelected: Boolean,
    filtersSelected: Boolean,
    onAction: (WardDrawerAction) -> Unit
) {
    val clinicalColors = LocalClinicalColors.current
    var wardMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var dataMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var appMenuExpanded by rememberSaveable { mutableStateOf(false) }

    ModalDrawerSheet(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Surface(
            onClick = {
                dataMenuExpanded = true
                wardMenuExpanded = false
                appMenuExpanded = false
            },
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
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
                        Text("${if (isAdmin) "مدير" else "عضو الفريق"} · ${state.patients.size} مريض",
                            style = MaterialTheme.typography.labelSmall)
                        Text("${state.shift?.date ?: "—"}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(buildString {
                    append(if (online) state.syncStatus.arabicLabel else "غير متصل · بيانات محلية")
                    if (state.mergeConflicts.isNotEmpty()) append(" · ${state.mergeConflicts.size} تعارض")
                }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        CompactDrawerItem(
                icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
                label = { Text("جميع المرضى") },
                selected = allPatientsSelected,
                onClick = { onAction(WardDrawerAction.ALL_PATIENTS) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        CompactDrawerItem(
                icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                label = { Text("معاينة وإرسال التقرير") },
                selected = false,
                onClick = { onAction(WardDrawerAction.REPORT) },
                modifier = Modifier.padding(horizontal = 12.dp),
                enabled = state.shift != null
            )
        if (isAdmin) CompactDrawerItem(
                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                label = { Text("مركز النشاط والسجل") },
                selected = false,
                onClick = { onAction(WardDrawerAction.ACTIVITY) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        CompactDrawerItem(
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                label = { Text("الإعدادات") },
                selected = false,
                onClick = { onAction(WardDrawerAction.SETTINGS) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        Divider()
        DrawerSubmenuHeader(
            label = "المناوبة والمرضى",
            expanded = wardMenuExpanded,
            onClick = {
                wardMenuExpanded = !wardMenuExpanded
                if (wardMenuExpanded) {
                    dataMenuExpanded = false
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
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                label = { Text("مرضاي") },
                selected = viewMode == WardViewMode.MINE,
                onClick = { onAction(WardDrawerAction.MY_PATIENTS) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
                label = { Text("بحث وتصفية وترتيب") },
                selected = filtersSelected,
                onClick = { onAction(WardDrawerAction.FILTERS) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Sort, contentDescription = null) },
                label = { Text("ترتيب المرضى") },
                selected = false,
                onClick = { if (!state.isReadOnly) onAction(WardDrawerAction.SORT) },
                modifier = Modifier.padding(horizontal = 12.dp),
                enabled = !state.isReadOnly
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
                label = { Text("تجميع المرضى") },
                selected = state.groupByMode != GroupByMode.NONE,
                onClick = { onAction(WardDrawerAction.GROUP) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                label = { Text("أطباء المناوبة") },
                selected = false,
                onClick = { if (!state.isReadOnly) onAction(WardDrawerAction.SHIFT_DOCTORS) },
                modifier = Modifier.padding(horizontal = 12.dp),
                enabled = !state.isReadOnly
            )
        }

        Divider(modifier = Modifier.padding(vertical = 4.dp))
        DrawerSubmenuHeader(
            label = "البيانات والمزامنة",
            expanded = dataMenuExpanded,
            onClick = {
                dataMenuExpanded = !dataMenuExpanded
                if (dataMenuExpanded) {
                    wardMenuExpanded = false
                    appMenuExpanded = false
                }
            }
        )
        if (dataMenuExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("حالة البيانات", style = MaterialTheme.typography.titleSmall)
                        if (state.syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    val syncColors = when (state.syncStatus) {
                        PatientSyncStatus.LOCAL -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                        PatientSyncStatus.PENDING -> clinicalColors.pendingContainer to clinicalColors.onPendingContainer
                        PatientSyncStatus.BACKED_UP -> clinicalColors.successContainer to clinicalColors.onSuccessContainer
                        PatientSyncStatus.SYNCING -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
                        PatientSyncStatus.CONFLICT -> clinicalColors.urgentContainer to clinicalColors.onUrgentContainer
                    }
                    AssistChip(
                        onClick = { onAction(WardDrawerAction.SYNC_DETAILS) },
                        label = { Text(freshnessLabel, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (state.syncStatus) {
                                    PatientSyncStatus.LOCAL -> Icons.Filled.Save
                                    PatientSyncStatus.PENDING -> Icons.Filled.CloudUpload
                                    PatientSyncStatus.BACKED_UP -> Icons.Filled.CloudDone
                                    PatientSyncStatus.SYNCING -> Icons.Filled.Sync
                                    PatientSyncStatus.CONFLICT -> Icons.Filled.Warning
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = syncColors.first,
                            labelColor = syncColors.second,
                            leadingIconContentColor = syncColors.second
                        ),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    if (!online) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "غير متصل · تُعرض البيانات المحلية",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    state.lastOperation?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.mergeConflicts.isNotEmpty()) {
                        Text(
                            "${state.mergeConflicts.size} تعارض يحتاج إجراء",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(
                            onClick = { onAction(WardDrawerAction.SYNC) },
                            enabled = !state.syncing
                        ) { Text("مزامنة", style = MaterialTheme.typography.labelSmall) }
                        TextButton(
                            onClick = { onAction(WardDrawerAction.PREVIOUS) },
                            enabled = !state.syncing
                        ) { Text("نسخة سابقة", style = MaterialTheme.typography.labelSmall) }
                    }
                    if (state.showSyncHint) {
                        Text(
                            "يمكن اختيار واحدة من آخر ثلاث مناوبات عند جلب نسخة سابقة.",
                            style = MaterialTheme.typography.labelSmall
                        )
                        TextButton(onClick = { onAction(WardDrawerAction.DISMISS_SYNC_HINT) }) {
                            Text("فهمت", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            CompactDrawerItem(
                icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                label = { Text("حفظ بيانات المرضى CSV") },
                selected = false,
                onClick = { onAction(WardDrawerAction.EXPORT_CSV) },
                modifier = Modifier.padding(horizontal = 12.dp),
                enabled = !state.exportingCsv
            )
            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Recycling, contentDescription = null) },
                label = { Text("سلة المحذوفات") },
                selected = false,
                onClick = { onAction(WardDrawerAction.RECYCLE_BIN) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Divider(modifier = Modifier.padding(vertical = 4.dp))
        DrawerSubmenuHeader(
            label = "الإدارة وحول التطبيق",
            expanded = appMenuExpanded,
            onClick = {
                appMenuExpanded = !appMenuExpanded
                if (appMenuExpanded) {
                    wardMenuExpanded = false
                    dataMenuExpanded = false
                }
            }
        )
        if (appMenuExpanded) {
            if (isAdmin) {
                CompactDrawerItem(
                    icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
                    label = { Text("لوحة المدير") },
                    selected = false,
                    onClick = { onAction(WardDrawerAction.ADMIN) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                CompactDrawerItem(
                    icon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                    label = { Text("استيراد بيانات من ملف") },
                    selected = false,
                    onClick = { onAction(WardDrawerAction.IMPORT) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            CompactDrawerItem(
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                label = { Text("حول التطبيق") },
                selected = false,
                onClick = { onAction(WardDrawerAction.ABOUT) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
