package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.components.AppMetricRow
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.ui.theme.UiSpacing

internal enum class DashboardFilter { ALL, MINE, URGENT, WARNINGS, UNASSIGNED, NEW_ADMISSIONS, TASK_PENDING, TASK_OVERDUE, TASK_UNASSIGNED }

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HandoverDashboard(
    isAdmin: Boolean,
    patients: List<Patient>,
    currentDoctorId: String,
    syncStatus: PatientSyncStatus,
    online: Boolean,
    freshnessLabel: String,
    nowEpochMillis: Long,
    onFilter: (DashboardFilter) -> Unit,
    onSync: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
    bottomClearance: Dp = UiSpacing.screen,
    showSynchronization: Boolean = true
) {
    val uiText = rememberUiStrings()
    val mine = patients.count {
        it.responsibleResidentId == currentDoctorId || it.responsibleSpecialistId == currentDoctorId ||
            it.tasks.any { task -> !task.done && task.ownerDoctorId == currentDoctorId }
    }
    val taskCounts = PatientTasks.counts(patients.flatMap { it.tasks }, nowEpochMillis)
    val urgent = patients.count {
        it.isPriority || it.badges.any { badge ->
            badge.priority == com.hos.rushdpatients.data.model.PatientBadgePriority.HIGH
        }
    }
    val warnings = patients.count { it.badges.isNotEmpty() }
    val unassigned = patients.count {
        it.responsibleResidentId == null || it.responsibleSpecialistId == null
    }
    val newAdmissions = patients.count { it.admittanceDays == 0 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
    ) {
        Text(
            if (showSynchronization) uiText(R.string.ui_handover_dashboard_001)
            else uiText(R.string.ui_handover_dashboard_002),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            if (showSynchronization) AppTextButton(onClick = onSync) { Text(uiText(R.string.ui_handover_dashboard_003)) }
            if (isAdmin) AppTextButton(onClick = onActivity) { Text(uiText(R.string.ui_ward_activity_screen_003)) }
            AppTextButton(onClick = { onFilter(DashboardFilter.ALL) }) { Text(uiText(R.string.ui_handover_dashboard_004)) }
        }
        Text(uiText(R.string.ui_handover_dashboard_005), style = MaterialTheme.typography.titleMedium)
        if (urgent > 0) AppMetricRow(uiText(R.string.ui_handover_dashboard_006), urgent, kind = NoticeKind.ERROR) { onFilter(DashboardFilter.URGENT) }
        if (taskCounts.overdue > 0) AppMetricRow(uiText(R.string.ui_handover_dashboard_007), taskCounts.overdue, kind = NoticeKind.ERROR) { onFilter(DashboardFilter.TASK_OVERDUE) }
        if (urgent == 0 && taskCounts.overdue == 0) Text(uiText(R.string.ui_handover_dashboard_008),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(uiText(R.string.ui_handover_dashboard_009), style = MaterialTheme.typography.titleMedium)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(UiSpacing.tiny)) {
                AppMetricRow(uiText(R.string.ui_handover_dashboard_010), patients.size, compact = true) { onFilter(DashboardFilter.ALL) }
                AppMetricRow(uiText(R.string.ui_handover_dashboard_011), mine, compact = true) { onFilter(DashboardFilter.MINE) }
                AppMetricRow(uiText(R.string.ui_handover_dashboard_012), taskCounts.pending, compact = true) { onFilter(DashboardFilter.TASK_PENDING) }
                AppMetricRow(uiText(R.string.ui_handover_dashboard_013), warnings, compact = true) { onFilter(DashboardFilter.WARNINGS) }
                AppMetricRow(uiText(R.string.ui_handover_dashboard_014), unassigned, compact = true) { onFilter(DashboardFilter.UNASSIGNED) }
                AppMetricRow(uiText(R.string.ui_handover_dashboard_015), taskCounts.unassigned, compact = true) { onFilter(DashboardFilter.TASK_UNASSIGNED) }
                AppMetricRow(uiText(R.string.ui_handover_dashboard_016), newAdmissions, compact = true) { onFilter(DashboardFilter.NEW_ADMISSIONS) }
            }
        }
        if (showSynchronization) Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Text(uiText(R.string.ui_handover_dashboard_017), style = MaterialTheme.typography.titleSmall)
                PatientSyncNotice(syncStatus, freshnessLabel, online)
                Text(uiText(R.string.ui_handover_dashboard_018))
            }
        }
    }
}
