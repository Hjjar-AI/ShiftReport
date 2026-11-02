package com.hos.rushdpatients.ui.ward

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.components.rememberUiStrings
import com.hos.rushdpatients.ui.components.uiLabel
import com.hos.rushdpatients.ui.theme.UiSpacing

/** List presentation only; the workspace retains filters, scroll/expansion state and patient actions. */
@Composable
internal fun WardPatientListSection(
    state: WardUiState, visiblePatients: List<Patient>, dualPane: Boolean,
    gridState: LazyGridState, bottomClearance: Dp, collapsedGroups: List<String>,
    filters: WardFilters, presetCounts: Map<DashboardFilter, Int>,
    showSearch: Boolean, requestSearchFocus: Boolean,
    onToggleGroup: (String) -> Unit, onResetFilters: () -> Unit,
    onPreset: (DashboardFilter) -> Unit, onOptions: () -> Unit,
    onQueryChange: (String) -> Unit, onFocusRequested: () -> Unit,
    patientCard: @Composable (Patient, Modifier) -> Unit
) {
    val uiText = rememberUiStrings()
    WardPatientGrid(state, visiblePatients, dualPane, gridState,
        bottomClearance, emptyList(), collapsedGroups,
        onToggleGroup = onToggleGroup, onResetFilters = onResetFilters,
        controlsContent = {
            if (state.isReadOnly) AppNotice(uiText(R.string.ui_ward_screen_013), icon = AppIcons.Lock, compact = true)
            Surface {
                Column(Modifier.padding(horizontal = UiSpacing.screenGutter)) {
                    WardPatientListControls(
                        visibleCount = visiblePatients.size, totalCount = state.patients.size,
                        presetCounts = presetCounts,
                        groupingLabel = state.groupByMode.takeUnless { it == GroupByMode.NONE }?.uiLabel(uiText),
                        activeFilters = filters.labels(uiText), selectedPreset = filters.selectedPreset,
                        onPreset = onPreset, onOptions = onOptions, onReset = onResetFilters,
                        showSearch = showSearch, query = filters.searchQuery, onQueryChange = onQueryChange,
                        requestFocus = requestSearchFocus, onFocusRequested = onFocusRequested)
                }
            }
        }, headerContent = {}, patientCard = patientCard)
}
