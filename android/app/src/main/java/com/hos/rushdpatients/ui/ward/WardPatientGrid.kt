package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiPatientCard
import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.ui.components.EmptyState

@Composable
internal fun WardPatientGrid(
    state: WardUiState, visiblePatients: List<Patient>, dualPane: Boolean,
    patientGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    scrollBottomClearance: androidx.compose.ui.unit.Dp, pinnedPatientIds: List<String>,
    collapsedGroups: List<String>, onToggleGroup: (String) -> Unit, onResetFilters: () -> Unit,
    headerContent: @Composable () -> Unit, patientCard: @Composable (Patient) -> Unit
) {
    val uiText = rememberUiStrings()
    val visibleIds = remember(visiblePatients) { visiblePatients.mapTo(HashSet()) { it.id } }
    val pinnedIds = remember(pinnedPatientIds) { pinnedPatientIds.toHashSet() }
    val visibleGroups = remember(state.groupedPatients, visibleIds, pinnedIds) {
        state.groupedPatients.map { group ->
            group to group.patients.filter { it.id in visibleIds }.sortedByDescending { it.id in pinnedIds }
        }.filter { it.second.isNotEmpty() }
    }
    LazyVerticalGrid(
        columns = if (dualPane) GridCells.Fixed(1) else GridCells.Adaptive(minSize = UiLayout.patientGridMinWidth),
        state = patientGridState,
        contentPadding = PaddingValues(bottom = scrollBottomClearance),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UiSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(UiPatientCard.listGap),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            headerContent()
        }

        if (visiblePatients.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    title = uiText(R.string.ui_ward_patient_grid_001),
                    subtitle = uiText(R.string.ui_ward_patient_grid_002),
                    actionLabel = uiText(R.string.ui_ward_activity_screen_008), onAction = onResetFilters
                )
            }
        } else if (state.groupByMode != GroupByMode.NONE &&
            state.groupedPatients.isNotEmpty()
        ) {
            visibleGroups.forEach { (group, groupPatients) ->
                item(
                    key = "grp-${group.key ?: "none"}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    GroupHeader(
                        name = group.name,
                        count = groupPatients.size,
                        collapsed = (group.key ?: "none") in collapsedGroups,
                        onToggle = {
                            onToggleGroup(group.key ?: "none")
                        }
                    )
                }
                if ((group.key ?: "none") !in collapsedGroups) gridItems(groupPatients, key = { it.id }) { patient ->
                    patientCard(patient)
                }
            }
        } else {
            gridItems(visiblePatients, key = { it.id }) { patient ->
                patientCard(patient)
            }
        }
    }
}
