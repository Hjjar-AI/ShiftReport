package com.hos.rushdpatients.ui.ward

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
import androidx.compose.runtime.key
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
                    title = "لا توجد نتائج مطابقة",
                    subtitle = "لا يطابق أي مريض البحث والتصفية الحالية",
                    actionLabel = "مسح البحث والتصفية", onAction = onResetFilters
                )
            }
        } else if (state.groupByMode != GroupByMode.NONE &&
            state.groupedPatients.isNotEmpty()
        ) {
            state.groupedPatients.forEach { group ->
                val groupPatients = group.patients
                    .filter { it in visiblePatients }
                    .sortedByDescending { it.id in pinnedPatientIds }
                if (groupPatients.isEmpty()) return@forEach
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
