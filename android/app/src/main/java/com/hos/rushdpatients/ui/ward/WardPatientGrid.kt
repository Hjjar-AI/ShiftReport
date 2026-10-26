package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiLayout
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import com.hos.rushdpatients.ui.theme.LocalUiStyle
import com.hos.rushdpatients.ui.components.AppIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import com.hos.rushdpatients.ui.components.AppTextButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.ui.components.EmptyState

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WardPatientGrid(
    state: WardUiState, visiblePatients: List<Patient>, dualPane: Boolean,
    patientGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    scrollBottomClearance: androidx.compose.ui.unit.Dp, pinnedPatientIds: List<String>,
    collapsedGroups: List<String>, onToggleGroup: (String) -> Unit, onResetFilters: () -> Unit,
    controlsContent: @Composable () -> Unit, headerContent: @Composable () -> Unit, patientCard: @Composable (Patient, Modifier) -> Unit
) {
    val uiText = rememberUiStrings()
    val placementMillis = LocalUiStyle.current.placementMillis
    val placementSpec = tween<androidx.compose.ui.unit.IntOffset>(placementMillis)
    // Fling movement already follows the finger/scroll physics. Reserve placement
    // animation for idle reorder/filter/expansion changes, without a second animator.
    val animatePlacement = placementMillis > 0 && !patientGridState.isScrollInProgress
    val visibleIds = remember(visiblePatients) { visiblePatients.mapTo(HashSet()) { it.id } }
    val pinnedIds = remember(pinnedPatientIds) { pinnedPatientIds.toHashSet() }
    val collapsedGroupIds = remember(collapsedGroups) { collapsedGroups.toHashSet() }
    val visibleGroups = remember(state.groupedPatients, visibleIds, pinnedIds) {
        state.groupedPatients.map { group ->
            group to group.patients.filter { it.id in visibleIds }.sortedByDescending { it.id in pinnedIds }
        }.filter { it.second.isNotEmpty() }
    }
    val scope = rememberCoroutineScope()
    val showBackToTop by remember(patientGridState) {
        derivedStateOf { patientGridState.firstVisibleItemIndex >= 8 }
    }
    Column(Modifier.fillMaxSize()) {
        controlsContent()
        if (showBackToTop) Row(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.screenGutter),
            horizontalArrangement = Arrangement.End) {
            AppTextButton(onClick = { scope.launch { patientGridState.scrollToItem(0) } }) {
                Text(uiText(R.string.ui_back_to_top))
            }
        }
        LazyVerticalGrid(
            columns = if (dualPane) GridCells.Fixed(1) else GridCells.Adaptive(minSize = UiLayout.patientGridMinWidth),
            state = patientGridState,
            contentPadding = PaddingValues(bottom = scrollBottomClearance),
            modifier = Modifier
                .weight(1f).fillMaxSize()
                .padding(horizontal = UiSpacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(com.hos.rushdpatients.ui.theme.uiListGap()),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
        ) {
            item(key = "ward-header", contentType = "ward-header", span = { GridItemSpan(maxLineSpan) }) {
                headerContent()
            }

            if (visiblePatients.isEmpty()) {
                item(key = "filtered-empty", contentType = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = uiText(R.string.ui_ward_patient_grid_001), icon = AppIcons.Search,
                        subtitle = uiText(R.string.ui_ward_patient_grid_002),
                        actionLabel = uiText(R.string.ui_ward_activity_screen_008), onAction = onResetFilters
                    )
                }
            } else if (state.groupByMode != GroupByMode.NONE &&
                state.groupedPatients.isNotEmpty()
            ) {
                visibleGroups.forEach { (group, groupPatients) ->
                    item(
                        key = "grp-${group.key ?: "none"}", contentType = "group",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        GroupHeader(
                            name = group.name,
                            count = groupPatients.size,
                            collapsed = (group.key ?: "none") in collapsedGroupIds,
                            onToggle = {
                                onToggleGroup(group.key ?: "none")
                            }
                        )
                    }
                    if ((group.key ?: "none") !in collapsedGroupIds) gridItems(groupPatients, key = { it.id }, contentType = { "patient" }) { patient ->
                        patientCard(patient, if (animatePlacement) Modifier.animateItemPlacement(placementSpec) else Modifier)
                    }
                }
            } else {
                gridItems(visiblePatients, key = { it.id }, contentType = { "patient" }) { patient ->
                    patientCard(patient, if (animatePlacement) Modifier.animateItemPlacement(placementSpec) else Modifier)
                }
            }
        }
    }
}
