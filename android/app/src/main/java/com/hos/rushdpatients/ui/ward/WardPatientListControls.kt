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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.hos.rushdpatients.ui.theme.UiSpace
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
    presetCounts: Map<DashboardFilter, Int>,
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

        val description = listOfNotNull(
            uiText(R.string.ui_ward_patient_list_controls_004),
            uiText(R.string.ui_ward_patient_list_controls_001, uiNumber(visibleCount), uiNumber(totalCount)),
            groupingLabel, activeFilters.takeIf { it.isNotEmpty() }?.distinct()?.joinToString(" · ")
        ).joinToString(" · ")
        val chipScroll = rememberScrollState()
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val surface = MaterialTheme.colorScheme.surface
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
            Box(Modifier.weight(1f).drawWithContent {
                drawContent()
                val edge = UiSpace.l.toPx().coerceAtMost(size.width / 2)
                val hiddenLeft = if (rtl) chipScroll.canScrollForward else chipScroll.canScrollBackward
                val hiddenRight = if (rtl) chipScroll.canScrollBackward else chipScroll.canScrollForward
                if (hiddenLeft) drawRect(Brush.horizontalGradient(listOf(surface, Color.Transparent), 0f, edge),
                    size = Size(edge, size.height))
                if (hiddenRight) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, surface), size.width - edge, size.width),
                    topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height))
            }) {
                Row(Modifier.horizontalScroll(chipScroll),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.controlGap)) {
                    listOf(DashboardFilter.ALL to uiText(R.string.ui_all), DashboardFilter.MINE to uiText(R.string.ui_handover_dashboard_011),
                        DashboardFilter.URGENT to uiText(R.string.ui_ward_patient_list_controls_007),
                        DashboardFilter.UNASSIGNED to uiText(R.string.ui_ward_screen_010), DashboardFilter.TASK_PENDING to uiText(R.string.ui_ward_screen_011)
                    ).forEach { (preset, label) -> AppFilterChip(selectedPreset == preset, { onPreset(preset) },
                        "$label ${uiNumber(presetCounts[preset] ?: 0)}") }
                }
            }
            BadgedBox(badge = { if (activeFilters.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { Text(uiNumber(activeFilters.size)) } }) {
                AppIconButton(label = description,
                    icon = AppIcons.FilterList, onClick = onOptions)
            }
            AppIconButton(label = uiText(R.string.ui_ward_patient_list_controls_005),
                icon = AppIcons.RestartAlt, enabled = activeFilters.isNotEmpty(), onClick = onReset)
        }

    }
}

internal enum class WardViewOption(@StringRes private val labelRes: Int) {
    FILTERS(R.string.ui_ward_patient_list_controls_009), GROUPING(R.string.ui_ward_patient_list_controls_010), CARDS(R.string.ui_ward_patient_list_controls_011), SORT(R.string.ui_ward_patient_list_controls_012);

    fun label(uiText: UiStrings): String = uiText(labelRes)
}
