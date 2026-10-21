package com.hos.rushdpatients.ui.ward

import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

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
    val uiText = rememberUiStrings()
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
        if (showSearch || query.isNotBlank()) {
            WardPatientSearchField(query, onQueryChange, requestFocus, onFocusRequested)
        }

        Text(uiText(R.string.ui_ward_patient_list_controls_001, uiNumber(visibleCount), uiNumber(totalCount)),
            modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold)
        if (activeFilters.isNotEmpty()) Text(uiText(R.string.ui_ward_patient_list_controls_002, activeFilters.distinct().joinToString(" · ")),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        groupingLabel?.let { Text(uiText(R.string.ui_ward_patient_list_controls_003, it), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        FlowRow(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppTextButton(onClick = onOptions) { Text(uiText(R.string.ui_ward_patient_list_controls_004)) }
            if (activeFilters.isNotEmpty()) AppTextButton(onClick = onReset) { Text(uiText(R.string.ui_ward_patient_list_controls_005)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
            listOf(DashboardFilter.ALL to uiText(R.string.ui_all), DashboardFilter.MINE to uiText(R.string.ui_handover_dashboard_011),
                DashboardFilter.URGENT to uiText(R.string.ui_ward_patient_list_controls_007), DashboardFilter.TASK_OVERDUE to uiText(R.string.ui_ward_patient_list_controls_008)
            ).forEach { (preset, label) ->
                CompactFilterChip(selectedPreset == preset, { onPreset(preset) }, label)
            }
        }
    }
}

/** One wrapping summary; grouping remains a display choice, not a patient filter. */
internal fun wardPatientFilterSummary(uiText: UiStrings,
    visibleCount: Int, totalCount: Int, activeFilters: List<String>, groupingLabel: String? = null
): String = buildList {
    add(uiText(R.string.ui_ward_patient_list_controls_001, uiNumber(visibleCount), uiNumber(totalCount)))
    addAll(activeFilters.filter(String::isNotBlank).distinct())
    groupingLabel?.takeIf(String::isNotBlank)?.let { add(uiText(R.string.ui_ward_patient_list_controls_003, it)) }
}.joinToString(" · ")

internal enum class WardViewOption(@StringRes private val labelRes: Int) {
    FILTERS(R.string.ui_ward_patient_list_controls_009), GROUPING(R.string.ui_ward_patient_list_controls_010), CARDS(R.string.ui_ward_patient_list_controls_011), SORT(R.string.ui_ward_patient_list_controls_012);

    fun label(uiText: UiStrings): String = uiText(labelRes)
}
