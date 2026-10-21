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
import androidx.compose.ui.text.font.FontWeight
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
    activeFilters: List<String>,
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
        if (showSearch || query.isNotBlank()) {
            WardPatientSearchField(query, onQueryChange, requestFocus, onFocusRequested)
        }

        Text("${uiNumber(visibleCount)} من ${uiNumber(totalCount)} مريض",
            modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold)
        if (activeFilters.isNotEmpty()) Text("التصفية: ${activeFilters.distinct().joinToString(" · ")}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        groupingLabel?.let { Text("تجميع: $it", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        FlowRow(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppTextButton(onClick = onOptions) { Text("خيارات العرض") }
            if (activeFilters.isNotEmpty()) AppTextButton(onClick = onReset) { Text("مسح التصفية") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            listOf(DashboardFilter.ALL to "الكل", DashboardFilter.MINE to "مرضاي",
                DashboardFilter.URGENT to "عاجل", DashboardFilter.TASK_OVERDUE to "مهام متأخرة"
            ).forEach { (preset, label) ->
                CompactFilterChip(selectedPreset == preset, { onPreset(preset) }, label)
            }
        }
    }
}

/** One wrapping summary; grouping remains a display choice, not a patient filter. */
internal fun wardPatientFilterSummary(
    visibleCount: Int, totalCount: Int, activeFilters: List<String>, groupingLabel: String? = null
): String = buildList {
    add("${uiNumber(visibleCount)} من ${uiNumber(totalCount)} مريض")
    addAll(activeFilters.filter(String::isNotBlank).distinct())
    groupingLabel?.takeIf(String::isNotBlank)?.let { add("تجميع: $it") }
}.joinToString(" · ")

internal enum class WardViewOption(val label: String) {
    FILTERS("التصفية"), GROUPING("التجميع"), CARDS("البطاقات"), SORT("الترتيب")
}
