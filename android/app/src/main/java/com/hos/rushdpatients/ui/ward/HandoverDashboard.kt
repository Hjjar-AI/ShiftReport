package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.ui.components.AppMetric
import com.hos.rushdpatients.ui.components.AppMetricGrid
import com.hos.rushdpatients.ui.theme.Tone
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextButton
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
import androidx.compose.runtime.remember
import com.hos.rushdpatients.domain.patient.WardSummary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    showSynchronization: Boolean = true,
    reportStatus: String? = null, onReportStatus: () -> Unit = onSync
) {
    val uiText = rememberUiStrings()
    val summary = remember(patients, currentDoctorId, nowEpochMillis) {
        WardSummary.from(patients, currentDoctorId, nowEpochMillis)
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
        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
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
            if (isAdmin) AppTextButton(onClick = onActivity) { Text(uiText(R.string.ui_ward_activity_screen_003)) }
            AppTextButton(onClick = { onFilter(DashboardFilter.ALL) }) { Text(uiText(R.string.ui_handover_dashboard_004)) }
        }
        Text(uiText(R.string.ui_handover_dashboard_005), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        val attention = buildList {
            if (urgent > 0) add(AppMetric(uiText(R.string.ui_handover_dashboard_006), urgent, Tone.URGENT) { onFilter(DashboardFilter.URGENT) })
        }
        AppMetricGrid(attention)
        if (attention.isEmpty()) com.hos.rushdpatients.ui.components.AppNotice(
            uiText(R.string.ui_handover_dashboard_008), kind = NoticeKind.SUCCESS)
        Text(uiText(R.string.ui_handover_dashboard_009), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        AppMetricGrid(listOf(
            AppMetric(uiText(R.string.ui_handover_dashboard_010), patients.size) { onFilter(DashboardFilter.ALL) },
            AppMetric(uiText(R.string.ui_handover_dashboard_011), mine) { onFilter(DashboardFilter.MINE) },
            AppMetric(uiText(R.string.ui_handover_dashboard_012), taskCounts.pending, Tone.PENDING) { onFilter(DashboardFilter.TASK_PENDING) },
            AppMetric(uiText(R.string.ui_handover_dashboard_013), warnings) { onFilter(DashboardFilter.WARNINGS) },
            AppMetric(uiText(R.string.ui_handover_dashboard_014), unassigned) { onFilter(DashboardFilter.UNASSIGNED) },
            AppMetric(uiText(R.string.ui_handover_dashboard_016), newAdmissions) { onFilter(DashboardFilter.NEW_ADMISSIONS) }
        ))
        if (showSynchronization) Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Text(uiText(R.string.ui_handover_dashboard_017), style = MaterialTheme.typography.titleSmall)
                PatientSyncNotice(syncStatus, freshnessLabel, online, onDetails = onSync)
                Text(uiText(R.string.ui_handover_dashboard_018))
            }
        }
    }
}
