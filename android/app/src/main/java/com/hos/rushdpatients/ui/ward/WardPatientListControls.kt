package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing

/** View-only list controls. Saved sorting remains an explicit, separate action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WardPatientListControls(
    visibleCount: Int,
    totalCount: Int,
    groupingLabel: String?,
    activeFilters: List<Pair<String, () -> Unit>>,
    selectedPreset: DashboardFilter?,
    onPreset: (DashboardFilter) -> Unit,
    onOptions: () -> Unit,
    onReset: () -> Unit,
    showSearch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        FlowRow(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            Text("${uiNumber(visibleCount)} من ${uiNumber(totalCount)} مريض",
                style = MaterialTheme.typography.titleSmall)
            groupingLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            AppTextButton(onClick = onOptions) {
                Text("التصفية والعرض والترتيب" +
                    if (activeFilters.isEmpty()) "" else " (${uiNumber(activeFilters.size)})")
            }
        }
        if (showSearch || query.isNotBlank()) {
            WardPatientSearchField(query, onQueryChange, requestFocus, onFocusRequested)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            listOf(DashboardFilter.ALL to "الكل", DashboardFilter.MINE to "مرضاي",
                DashboardFilter.URGENT to "عاجل", DashboardFilter.TASK_OVERDUE to "مهام متأخرة"
            ).forEach { (preset, label) ->
                CompactFilterChip(selectedPreset == preset, { onPreset(preset) }, label)
            }
        }
        if (activeFilters.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                activeFilters.forEach { (label, remove) -> ActiveFilterChip(label, remove) }
                AppTextButton(onClick = onReset) { Text("مسح التصفية") }
            }
        }
    }
}
