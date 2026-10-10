package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.domain.task.PatientTasks
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
        DashboardMetric("عاجل أو أولوية", urgent, MaterialTheme.colorScheme.errorContainer) { onFilter(DashboardFilter.URGENT) }
        DashboardMetric("المهام المتأخرة (ضمن المعلقة)", taskCounts.overdue, MaterialTheme.colorScheme.errorContainer) { onFilter(DashboardFilter.TASK_OVERDUE) }
        Text("ملخص المرضى والمهام", style = MaterialTheme.typography.titleSmall)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(UiSpacing.tiny)) {
                DashboardMetric("إجمالي المرضى", patients.size, MaterialTheme.colorScheme.surfaceVariant, compact = true) { onFilter(DashboardFilter.ALL) }
                DashboardMetric("مرضاي", mine, MaterialTheme.colorScheme.surfaceVariant, compact = true) { onFilter(DashboardFilter.MINE) }
                DashboardMetric("المهام المعلقة", taskCounts.pending, MaterialTheme.colorScheme.surfaceVariant, compact = true) { onFilter(DashboardFilter.TASK_PENDING) }
                DashboardMetric("شارات فعالة", warnings, MaterialTheme.colorScheme.surfaceVariant, compact = true) { onFilter(DashboardFilter.WARNINGS) }
                DashboardMetric("بحاجة إلى تعيين طبيب", unassigned, MaterialTheme.colorScheme.surfaceVariant, compact = true) { onFilter(DashboardFilter.UNASSIGNED) }
                DashboardMetric("مهام معلقة غير معيّنة", taskCounts.unassigned, MaterialTheme.colorScheme.surfaceVariant, compact = true) { onFilter(DashboardFilter.TASK_UNASSIGNED) }
                DashboardMetric("دخول اليوم", newAdmissions, MaterialTheme.colorScheme.surfaceVariant, compact = true) { onFilter(DashboardFilter.NEW_ADMISSIONS) }
            }
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(UiPadding.content), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                Text("حالة المناوبة", style = MaterialTheme.typography.titleMedium)
                Text("حالة البيانات: ${syncStatus.arabicLabel}")
                Text("استخدم معاينة التقرير لمراجعة تغييرات المناوبة قبل النشر")
            }
        }
    }
}

@Composable
private fun DashboardMetric(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = UiSpacing.touchTarget)
            .clickable(role = Role.Button, onClickLabel = "عرض $label", onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = color
    ) {
        Row(
            modifier = Modifier.padding(UiPadding.content),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), style = if (compact)
                MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium)
            Text(count.toString(), style = if (compact)
                MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
        }
    }
}
