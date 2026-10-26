package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.components.AppSection
import com.hos.rushdpatients.ui.components.AppMetric
import com.hos.rushdpatients.ui.components.AppMetricGrid
import com.hos.rushdpatients.ui.theme.Tone
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import com.hos.rushdpatients.domain.patient.WardSummary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.ui.theme.UiSpacing

internal enum class DashboardFilter { ALL, MINE, URGENT, WARNINGS, UNASSIGNED, NEW_ADMISSIONS, TASK_PENDING }

@Composable
@OptIn(ExperimentalLayoutApi::class)
@Suppress("UNUSED_PARAMETER")
internal fun HandoverDashboard(
    isAdmin: Boolean,
    patients: List<Patient>,
    currentDoctorId: String,
    syncStatus: PatientSyncStatus,
    online: Boolean,
    freshnessLabel: String,
    dayLabel: java.time.LocalDate = com.hos.rushdpatients.util.ShiftDate.current(),
    onFilter: (DashboardFilter) -> Unit,
    onSync: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
    bottomClearance: Dp = UiSpacing.screen,
    showSynchronization: Boolean = true,
    pendingCount: Int? = null,
    reportStatus: String? = null, onReportStatus: () -> Unit = onSync
) {
    val uiText = rememberUiStrings()
    val summary = remember(patients, currentDoctorId, dayLabel) {
        WardSummary.from(patients, currentDoctorId, dayLabel)
    }
    val mine = summary.mine
    val taskCounts = summary.tasks
    val urgent = summary.urgent
    val warnings = summary.warnings
    val unassigned = summary.unassigned
    val newAdmissions = summary.newAdmissions

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.section)
    ) {
        reportStatus?.let { status ->
            AppTextButton(onClick = onReportStatus) { Text(status, style = MaterialTheme.typography.titleSmall) }
        }
        Text(
            if (showSynchronization) uiText(R.string.ui_handover_dashboard_001)
            else uiText(R.string.ui_handover_dashboard_002),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            if (showSynchronization) AppTextButton(onClick = onSync) { Text(uiText(R.string.ui_ward_drawer_013)) }
            AppTextButton(onClick = { onFilter(DashboardFilter.ALL) }) { Text(uiText(R.string.ui_handover_dashboard_004)) }
        }
        val attention = buildList {
            if (mine > 0) add(AppMetric(uiText(R.string.ui_handover_dashboard_011), mine) { onFilter(DashboardFilter.MINE) })
            if (unassigned > 0) add(AppMetric(uiText(R.string.ui_handover_dashboard_014), unassigned, Tone.WARNING) { onFilter(DashboardFilter.UNASSIGNED) })
            if (warnings > 0) add(AppMetric(uiText(R.string.ui_handover_dashboard_013), warnings, Tone.WARNING) { onFilter(DashboardFilter.WARNINGS) })
            if (taskCounts.pending > 0) add(AppMetric(uiText(R.string.ui_handover_dashboard_012), taskCounts.pending, Tone.PENDING) { onFilter(DashboardFilter.TASK_PENDING) })
            if (urgent > 0) add(AppMetric(uiText(R.string.ui_handover_dashboard_006), urgent, Tone.URGENT) { onFilter(DashboardFilter.URGENT) })
        }
        AppSection(title = uiText(R.string.ui_handover_dashboard_005)) {
            AppMetricGrid(attention)
            if (urgent == 0 && unassigned == 0 && warnings == 0 && taskCounts.pending == 0) {
                com.hos.rushdpatients.ui.components.AppNotice(
                    uiText(R.string.ui_handover_dashboard_008), kind = NoticeKind.SUCCESS)
            }
        }
        AppSection(title = uiText(R.string.ui_handover_dashboard_009)) {
            AppMetricGrid(listOf(
                AppMetric(uiText(R.string.ui_handover_dashboard_010), patients.size) { onFilter(DashboardFilter.ALL) },
                AppMetric(uiText(R.string.ui_handover_dashboard_016), newAdmissions) { onFilter(DashboardFilter.NEW_ADMISSIONS) }
            ))
        }
        if (showSynchronization) AppSection(title = uiText(R.string.ui_handover_dashboard_017)) {
            PatientSyncNotice(syncStatus, freshnessLabel, online, onDetails = onSync)
            if (pendingCount != null && pendingCount > 0) {
                Text(uiText(R.string.dashboard_pending_changes,
                    com.hos.rushdpatients.ui.components.uiNumber(pendingCount)),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text(uiText(R.string.ui_handover_dashboard_018), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
