package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.UiStrings
import androidx.annotation.StringRes


import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.hos.rushdpatients.ui.components.AppIconButton
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.theme.UiSpacing

/** View-only list controls. Saved sorting remains an explicit, separate action. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

        if (activeFilters.isNotEmpty() || groupingLabel != null) Text(
            listOfNotNull(
                if (activeFilters.isNotEmpty()) uiText(R.string.ui_ward_patient_list_controls_001, uiNumber(visibleCount), uiNumber(totalCount)) else null,
                groupingLabel
            ).joinToString(" · "), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
                listOf(DashboardFilter.ALL to uiText(R.string.ui_all), DashboardFilter.MINE to uiText(R.string.ui_handover_dashboard_011),
                    DashboardFilter.URGENT to uiText(R.string.ui_ward_patient_list_controls_007), DashboardFilter.TASK_OVERDUE to uiText(R.string.ui_ward_patient_list_controls_008)
                ).forEach { (preset, label) -> AppFilterChip(selectedPreset == preset, { onPreset(preset) }, label) }
            }
            BadgedBox(badge = { if (activeFilters.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { Text(uiNumber(activeFilters.size)) } }) {
                AppIconButton(label = uiText(R.string.ui_ward_patient_list_controls_004) +
                    if (activeFilters.isEmpty()) "" else " · ${uiNumber(activeFilters.size)}",
                    icon = AppIcons.FilterList, onClick = onOptions)
            }
            if (activeFilters.isNotEmpty()) AppIconButton(label = uiText(R.string.ui_ward_patient_list_controls_005),
                icon = AppIcons.RestartAlt, onClick = onReset)
        }
        if (activeFilters.isNotEmpty() && selectedPreset == null) Text(
            uiText(R.string.ui_ward_patient_list_controls_002, activeFilters.distinct().joinToString(" · ")),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    }
}

internal enum class WardViewOption(@StringRes private val labelRes: Int) {
    FILTERS(R.string.ui_ward_patient_list_controls_009), GROUPING(R.string.ui_ward_patient_list_controls_010), CARDS(R.string.ui_ward_patient_list_controls_011), SORT(R.string.ui_ward_patient_list_controls_012);

    fun label(uiText: UiStrings): String = uiText(labelRes)
}
