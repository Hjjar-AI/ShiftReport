package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.data.model.Patient

internal enum class DashboardFilter { ALL, MINE, URGENT, WARNINGS, UNASSIGNED, NEW_ADMISSIONS, TASK_PENDING, TASK_OVERDUE, TASK_UNASSIGNED }

@Composable
internal fun HandoverDashboard(
    isAdmin: Boolean,
    patients: List<Patient>,
    currentDoctorId: String,
    syncStatus: PatientSyncStatus,
    nowEpochMillis: Long,
    onFilter: (DashboardFilter) -> Unit,
    onSync: () -> Unit,
    onActivity: () -> Unit,
    modifier: Modifier = Modifier
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("لوحة تسليم المناوبة", style = MaterialTheme.typography.headlineSmall)
        Text(
            "ملخص سريع قبل مراجعة المرضى أو نشر التقرير",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSync) { Text("مزامنة") }
            if (isAdmin) TextButton(onClick = onActivity) { Text("مركز النشاط") }
            TextButton(onClick = { onFilter(DashboardFilter.ALL) }) { Text("قائمة المرضى") }
        }
        DashboardMetric("إجمالي المرضى", patients.size, MaterialTheme.colorScheme.primaryContainer) { onFilter(DashboardFilter.ALL) }
        DashboardMetric("مرضاي", mine, MaterialTheme.colorScheme.secondaryContainer) { onFilter(DashboardFilter.MINE) }
        DashboardMetric("عاجل أو أولوية", urgent, MaterialTheme.colorScheme.errorContainer) { onFilter(DashboardFilter.URGENT) }
        DashboardMetric("شارات فعالة", warnings, MaterialTheme.colorScheme.tertiaryContainer) { onFilter(DashboardFilter.WARNINGS) }
        DashboardMetric("بحاجة إلى تعيين طبيب", unassigned, MaterialTheme.colorScheme.surfaceVariant) { onFilter(DashboardFilter.UNASSIGNED) }
        DashboardMetric("المهام المعلقة", taskCounts.pending, MaterialTheme.colorScheme.secondaryContainer) { onFilter(DashboardFilter.TASK_PENDING) }
        DashboardMetric("المهام المتأخرة (ضمن المعلقة)", taskCounts.overdue, MaterialTheme.colorScheme.errorContainer) { onFilter(DashboardFilter.TASK_OVERDUE) }
        DashboardMetric("مهام معلقة غير معيّنة", taskCounts.unassigned, MaterialTheme.colorScheme.surfaceVariant) { onFilter(DashboardFilter.TASK_UNASSIGNED) }
        DashboardMetric("دخول اليوم", newAdmissions, MaterialTheme.colorScheme.primaryContainer) { onFilter(DashboardFilter.NEW_ADMISSIONS) }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("حالة المناوبة", style = MaterialTheme.typography.titleMedium)
                Text("حالة البيانات: ${syncStatus.arabicLabel}")
                Text("استخدم معاينة التقرير لمراجعة تغييرات المناوبة قبل النشر")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardMetric(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
