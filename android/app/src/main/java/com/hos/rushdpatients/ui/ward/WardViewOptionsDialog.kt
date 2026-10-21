package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import androidx.compose.foundation.verticalScroll
import com.hos.rushdpatients.ui.components.AppSectionIndex
import com.hos.rushdpatients.ui.components.AppSearchField
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppToggleRow
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.domain.sort.GroupByMode

internal data class WardFilterOptions(
    val searchQuery: String, val viewMode: WardViewMode, val priorityOnly: Boolean,
    val warningsOnly: Boolean, val unassignedOnly: Boolean, val urgentOnly: Boolean,
    val newAdmissionsOnly: Boolean, val taskFilter: DashboardFilter?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WardViewOptionsDialog(
    state: WardUiState, viewModel: WardViewModel, filters: WardFilterOptions,
    onFiltersChanged: ((WardFilterOptions) -> WardFilterOptions) -> Unit, selectedViewOption: WardViewOption,
    viewOptionsScroll: androidx.compose.foundation.ScrollState, showSort: Boolean,
    onSortExpandedChange: (Boolean) -> Unit, onSelectOption: (WardViewOption) -> Unit,
    onShowTasks: () -> Unit, onSetAllExpanded: (Boolean) -> Unit,
    onResetFilters: () -> Unit, onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = {
            Column {
                Text("خيارات العرض")
                AppSectionIndex(sections = WardViewOption.entries.toList(), selected = selectedViewOption,
                    label = { it.label }, enabled = !state.saving, onSelect = onSelectOption)
            }
        },
        text = {
            Column(Modifier.appDialogBody(UiDialog.reviewBodyMaxHeight)
                .verticalScroll(viewOptionsScroll), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text("البحث والتصفية والتجميع للعرض فقط؛ الترتيب يُحفظ ويستخدم في التقارير وCSV. مرضى الأولوية أولاً.", style = MaterialTheme.typography.bodySmall)
                if (selectedViewOption == WardViewOption.FILTERS) {
                    AppSearchField(query = filters.searchQuery, onQueryChange = { query -> onFiltersChanged { it.copy(searchQuery = query) } }, label = "بحث")
                    Text("تصفية · للعرض فقط", style = MaterialTheme.typography.titleSmall)
                    AppOutlinedButton(onClick = onShowTasks,
                        enabled = state.patients.isNotEmpty() && !state.loading && !state.saving && !state.syncing,
                        modifier = Modifier.fillMaxWidth()) { Text("مهام الجناح") }
                    CompactFilterChip(filters.viewMode == WardViewMode.MINE,
                        { onFiltersChanged { it.copy(viewMode = if (it.viewMode == WardViewMode.MINE) WardViewMode.ALL else WardViewMode.MINE) } },
                        "مرضاي ومهامي")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        CompactFilterChip(filters.priorityOnly, { onFiltersChanged { it.copy(priorityOnly = !it.priorityOnly) } }, "أولوية فقط")
                        CompactFilterChip(filters.warningsOnly, { onFiltersChanged { it.copy(warningsOnly = !it.warningsOnly) } }, "مع شارة")
                        CompactFilterChip(filters.unassignedOnly, { onFiltersChanged { it.copy(unassignedOnly = !it.unassignedOnly) } }, "غير معيّن")
                        CompactFilterChip(filters.urgentOnly, { onFiltersChanged { it.copy(urgentOnly = !it.urgentOnly) } }, "عاجل")
                        CompactFilterChip(filters.newAdmissionsOnly, { onFiltersChanged { it.copy(newAdmissionsOnly = !it.newAdmissionsOnly) } }, "دخول اليوم")
                        listOf(DashboardFilter.TASK_PENDING to "مهام معلقة",
                            DashboardFilter.TASK_OVERDUE to "مهام متأخرة",
                            DashboardFilter.TASK_UNASSIGNED to "مهام غير معيّنة").forEach { (value, label) ->
                            CompactFilterChip(selected = filters.taskFilter == value,
                                onClick = { onFiltersChanged { it.copy(taskFilter = if (it.taskFilter == value) null else value) } }, label = label)
                        }
                    }
                }
                if (selectedViewOption == WardViewOption.GROUPING) {
                    Text("تجميع · للعرض فقط", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        com.hos.rushdpatients.domain.sort.GroupByMode.entries.forEach { mode ->
                            CompactFilterChip(state.groupByMode == mode,
                                { viewModel.setGroupByMode(mode) }, mode.arabicLabel)
                        }
                    }
                }
                if (selectedViewOption == WardViewOption.CARDS) {
                    Text("بطاقات المرضى · تفضيلات الجهاز", style = MaterialTheme.typography.titleSmall)
                    AppToggleRow(title = "عرض التفاصيل", checked = state.patientDetailsExpanded,
                        onCheckedChange = onSetAllExpanded)
                    AppToggleRow(title = "بطاقة بعمودين عند توفر المساحة", checked = state.twoColumn,
                        onCheckedChange = viewModel::setTwoColumn)
                    AppToggleRow(title = "بطاقات مضغوطة", checked = state.compactCards,
                        onCheckedChange = viewModel::setCompactCards)
                }
                if (selectedViewOption == WardViewOption.SORT) {
                    Text("ترتيب · يُحفظ مع المناوبة", style = MaterialTheme.typography.titleSmall)
                    Text(sortReviewLabel(state.sortSpec), style = MaterialTheme.typography.bodySmall)
                    if (!state.isReadOnly) {
                        AppTextButton(onClick = { onSortExpandedChange(!showSort) }, enabled = !state.saving) {
                            Text(if (showSort) "طي خيارات الترتيب" else "تعديل ترتيب المرضى")
                        }
                        if (showSort) PatientSortOptions(
                            initial = state.sortSpec,
                            initialRevision = state.shift?.revision ?: 0,
                            saving = state.saving,
                            onApply = { spec, revision, onStale ->
                                viewModel.applySort(spec, revision, onStale) { onSortExpandedChange(false) }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { AppTextButton(onClick = onClose,
            enabled = !state.saving) { Text("تم") } },
        dismissButton = {
            AppTextButton(onClick = onResetFilters) { Text("مسح عوامل التصفية") }
        }
    )
}
