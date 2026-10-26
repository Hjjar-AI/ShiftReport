package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.AppAlertDialog
import com.hos.rushdpatients.ui.components.AppFilterChip
import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.domain.sort.GroupByMode

internal data class WardFilterOptions(
    val searchQuery: String = "", val viewMode: WardViewMode = WardViewMode.ALL, val priorityOnly: Boolean = false,
    val warningsOnly: Boolean = false, val unassignedOnly: Boolean = false, val urgentOnly: Boolean = false,
    val newAdmissionsOnly: Boolean = false, val taskFilter: WardTaskFilter? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WardViewOptionsDialog(
    state: WardUiState, viewModel: WardController, filters: WardFilterOptions,
    resultSummary: String, activeFilterLabels: List<String>,
    onFiltersChanged: ((WardFilterOptions) -> WardFilterOptions) -> Unit, selectedViewOption: WardViewOption,
    viewOptionsScroll: androidx.compose.foundation.ScrollState, showSort: Boolean,
    onSortExpandedChange: (Boolean) -> Unit, onSelectOption: (WardViewOption) -> Unit,
    onShowTasks: () -> Unit, onSetAllExpanded: (Boolean) -> Unit,
    onResetFilters: () -> Unit, onClose: () -> Unit
) {
    val uiText = rememberUiStrings()
    AppAlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = {
            Column {
                Text(uiText(R.string.ui_ward_patient_list_controls_004))
                AppSectionIndex(sections = WardViewOption.entries.toList(), selected = selectedViewOption,
                    label = { it.label(uiText) }, enabled = !state.saving, onSelect = onSelectOption)
            }
        },
        text = {
            Column(Modifier.appDialogBody(UiDialog.reviewBodyMaxHeight)
                .verticalScroll(viewOptionsScroll), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(resultSummary, style = MaterialTheme.typography.labelMedium)
                if (activeFilterLabels.isNotEmpty()) Text(
                    uiText(R.string.ui_ward_patient_list_controls_002, activeFilterLabels.distinct().joinToString(" · ")),
                    style = MaterialTheme.typography.bodySmall)
                Text(uiText(R.string.ui_ward_view_options_dialog_001), style = MaterialTheme.typography.bodySmall)
                if (selectedViewOption == WardViewOption.FILTERS) {
                    AppSearchField(query = filters.searchQuery, onQueryChange = { query -> onFiltersChanged { it.copy(searchQuery = query) } }, label = uiText(R.string.ui_search))
                    Text(uiText(R.string.ui_ward_view_options_dialog_003), style = MaterialTheme.typography.titleSmall)
                    AppOutlinedButton(onClick = onShowTasks,
                        enabled = state.patients.isNotEmpty() && !state.loading && !state.saving && !state.syncing,
                        modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_ward_view_options_dialog_004)) }
                    AppFilterChip(filters.viewMode == WardViewMode.MINE,
                        { onFiltersChanged { it.copy(viewMode = if (it.viewMode == WardViewMode.MINE) WardViewMode.ALL else WardViewMode.MINE) } },
                        uiText(R.string.ui_ward_view_options_dialog_005))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        AppFilterChip(filters.priorityOnly, { onFiltersChanged { it.copy(priorityOnly = !it.priorityOnly) } }, uiText(R.string.ui_ward_view_options_dialog_006))
                        AppFilterChip(filters.warningsOnly, { onFiltersChanged { it.copy(warningsOnly = !it.warningsOnly) } }, uiText(R.string.ui_ward_screen_009))
                        AppFilterChip(filters.unassignedOnly, { onFiltersChanged { it.copy(unassignedOnly = !it.unassignedOnly) } }, uiText(R.string.ui_ward_screen_010))
                        AppFilterChip(filters.urgentOnly, { onFiltersChanged { it.copy(urgentOnly = !it.urgentOnly) } }, uiText(R.string.ui_ward_patient_list_controls_007))
                        AppFilterChip(filters.newAdmissionsOnly, { onFiltersChanged { it.copy(newAdmissionsOnly = !it.newAdmissionsOnly) } }, uiText(R.string.ui_handover_dashboard_016))
                        listOf(WardTaskFilter.PENDING to uiText(R.string.ui_ward_screen_011)).forEach { (value, label) ->
                            AppFilterChip(selected = filters.taskFilter == value,
                                onClick = { onFiltersChanged { it.copy(taskFilter = if (it.taskFilter == value) null else value) } }, label = label)
                        }
                    }
                }
                if (selectedViewOption == WardViewOption.GROUPING) {
                    Text(uiText(R.string.ui_ward_view_options_dialog_007), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        com.hos.rushdpatients.domain.sort.GroupByMode.entries.forEach { mode ->
                            AppFilterChip(state.groupByMode == mode,
                                { viewModel.setGroupByMode(mode) }, mode.uiLabel(uiText))
                        }
                    }
                }
                if (selectedViewOption == WardViewOption.CARDS) {
                    Text(uiText(R.string.ui_ward_view_options_dialog_008), style = MaterialTheme.typography.titleSmall)
                    AppToggleRow(title = uiText(R.string.ui_ward_view_options_dialog_009), checked = state.patientDetailsExpanded,
                        onCheckedChange = onSetAllExpanded)
                    AppToggleRow(title = uiText(R.string.ui_ward_view_options_dialog_010), checked = state.twoColumn,
                        onCheckedChange = viewModel::setTwoColumn)
                    AppToggleRow(title = uiText(R.string.ui_ward_view_options_dialog_011), checked = state.compactCards,
                        onCheckedChange = viewModel::setCompactCards)
                }
                if (selectedViewOption == WardViewOption.SORT) {
                    Text(uiText(R.string.ui_ward_view_options_dialog_012), style = MaterialTheme.typography.titleSmall)
                    Text(sortReviewLabel(uiText, state.sortSpec), style = MaterialTheme.typography.bodySmall)
                    if (!state.isReadOnly) {
                        AppTextButton(onClick = { onSortExpandedChange(!showSort) }, enabled = !state.saving) {
                            Text(if (showSort) uiText(R.string.ui_ward_view_options_dialog_013) else uiText(R.string.ui_ward_view_options_dialog_014))
                        }
                        if (showSort) PatientSortOptions(
                            initial = state.sortSpec,
                            saving = state.saving,
                            onApply = { spec ->
                                viewModel.applySort(spec) { onSortExpandedChange(false) }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { AppTextButton(onClick = onClose,
            enabled = !state.saving) { Text(uiText(R.string.ui_done)) } },
        dismissButton = {
            AppTextButton(onClick = onResetFilters) { Text(uiText(R.string.ui_ward_view_options_dialog_016)) }
        }
    )
}
