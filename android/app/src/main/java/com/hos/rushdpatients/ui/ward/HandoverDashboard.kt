package com.hos.rushdpatients.ui.ward

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
    conflictCount: Int,
    nowEpochMillis: Long,
    onFilter: (DashboardFilter) -> Unit,
    onSync: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier,
    bottomClearance: Dp = UiSpacing.screen
) {
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
        Text("لوحة تسليم المناوبة", style = MaterialTheme.typography.headlineSmall)
        Text(
            "ملخص سريع قبل مراجعة المرضى أو نشر التقرير",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppTextButton(onClick = onSync) { Text("مزامنة") }
            if (isAdmin) AppTextButton(onClick = onActivity) { Text("مركز النشاط") }
            AppTextButton(onClick = { onFilter(DashboardFilter.ALL) }) { Text("قائمة المرضى") }
        }
        AppMetricRow("عاجل أو أولوية", urgent, kind = NoticeKind.ERROR) { onFilter(DashboardFilter.URGENT) }
        AppMetricRow("المهام المتأخرة (ضمن المعلقة)", taskCounts.overdue, kind = NoticeKind.ERROR) { onFilter(DashboardFilter.TASK_OVERDUE) }
        Text("ملخص المرضى والمهام", style = MaterialTheme.typography.titleSmall)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(UiSpacing.tiny)) {
                AppMetricRow("إجمالي المرضى", patients.size, compact = true) { onFilter(DashboardFilter.ALL) }
                AppMetricRow("مرضاي", mine, compact = true) { onFilter(DashboardFilter.MINE) }
                AppMetricRow("المهام المعلقة", taskCounts.pending, compact = true) { onFilter(DashboardFilter.TASK_PENDING) }
                AppMetricRow("مرضى لديهم شارات", warnings, compact = true) { onFilter(DashboardFilter.WARNINGS) }
                AppMetricRow("بحاجة إلى تعيين طبيب", unassigned, compact = true) { onFilter(DashboardFilter.UNASSIGNED) }
                AppMetricRow("مهام معلقة غير معيّنة", taskCounts.unassigned, compact = true) { onFilter(DashboardFilter.TASK_UNASSIGNED) }
                AppMetricRow("دخول اليوم", newAdmissions, compact = true) { onFilter(DashboardFilter.NEW_ADMISSIONS) }
            }
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Text("حالة المناوبة", style = MaterialTheme.typography.titleMedium)
                PatientSyncNotice(syncStatus, freshnessLabel, online, conflictCount)
                Text("استخدم معاينة التقرير لمراجعة تغييرات المناوبة قبل النشر")
            }
        }
    }
}
