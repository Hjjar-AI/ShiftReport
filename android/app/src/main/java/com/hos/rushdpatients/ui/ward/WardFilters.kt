package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.UiStrings

/** One saveable owner for view-only filters; stored patient sorting remains separate. */
@Stable
internal class WardFilters(initial: WardFilterOptions = WardFilterOptions()) {
    var searchQuery by mutableStateOf(initial.searchQuery)
    var viewMode by mutableStateOf(initial.viewMode)
    var priorityOnly by mutableStateOf(initial.priorityOnly)
    var warningsOnly by mutableStateOf(initial.warningsOnly)
    var unassignedOnly by mutableStateOf(initial.unassignedOnly)
    var urgentOnly by mutableStateOf(initial.urgentOnly)
    var newAdmissionsOnly by mutableStateOf(initial.newAdmissionsOnly)
    var taskFilter by mutableStateOf(initial.taskFilter)

    fun options() = WardFilterOptions(searchQuery, viewMode, priorityOnly, warningsOnly,
        unassignedOnly, urgentOnly, newAdmissionsOnly, taskFilter)

    fun update(value: WardFilterOptions) {
        searchQuery = value.searchQuery; viewMode = value.viewMode
        priorityOnly = value.priorityOnly; warningsOnly = value.warningsOnly
        unassignedOnly = value.unassignedOnly; urgentOnly = value.urgentOnly
        newAdmissionsOnly = value.newAdmissionsOnly; taskFilter = value.taskFilter
    }

    fun applyPreset(filter: DashboardFilter) {
        update(WardFilterOptions())
        when (filter) {
            DashboardFilter.ALL -> Unit
            DashboardFilter.MINE -> viewMode = WardViewMode.MINE
            DashboardFilter.URGENT -> urgentOnly = true
            DashboardFilter.WARNINGS -> warningsOnly = true
            DashboardFilter.UNASSIGNED -> unassignedOnly = true
            DashboardFilter.NEW_ADMISSIONS -> newAdmissionsOnly = true
            DashboardFilter.TASK_PENDING -> taskFilter = WardTaskFilter.PENDING
        }
    }

    private val filterCount: Int get() = listOf(viewMode == WardViewMode.MINE, searchQuery.isNotBlank(),
        priorityOnly, warningsOnly, unassignedOnly, urgentOnly, newAdmissionsOnly, taskFilter != null).count { it }
    val filtersSelected: Boolean get() = filterCount > 0
    val allPatientsSelected: Boolean get() = viewMode == WardViewMode.ALL && !filtersSelected
    val selectedPreset: DashboardFilter? get() = when {
        filterCount == 0 -> DashboardFilter.ALL
        filterCount != 1 -> null
        viewMode == WardViewMode.MINE -> DashboardFilter.MINE
        urgentOnly -> DashboardFilter.URGENT
        taskFilter == WardTaskFilter.PENDING -> DashboardFilter.TASK_PENDING
        warningsOnly -> DashboardFilter.WARNINGS
        unassignedOnly -> DashboardFilter.UNASSIGNED
        newAdmissionsOnly -> DashboardFilter.NEW_ADMISSIONS
        else -> null
    }

    fun labels(uiText: UiStrings): List<String> = buildList {
        if (viewMode == WardViewMode.MINE) add(uiText(R.string.ui_handover_dashboard_011))
        if (searchQuery.isNotBlank()) add(uiText(R.string.ui_ward_screen_008, searchQuery))
        if (priorityOnly) add(uiText(R.string.ui_patient_identity_002))
        if (warningsOnly) add(uiText(R.string.ui_ward_screen_009))
        if (unassignedOnly) add(uiText(R.string.ui_ward_screen_010))
        if (urgentOnly) add(uiText(R.string.ui_ward_patient_list_controls_007))
        if (newAdmissionsOnly) add(uiText(R.string.ui_handover_dashboard_016))
        when (taskFilter) {
            WardTaskFilter.PENDING -> add(uiText(R.string.ui_ward_screen_011))
            null -> Unit
        }
    }

    companion object {
        val Saver = listSaver<WardFilters, Any>(save = {
            listOf(it.searchQuery, it.viewMode.name, it.priorityOnly, it.warningsOnly,
                it.unassignedOnly, it.urgentOnly, it.newAdmissionsOnly, it.taskFilter?.name.orEmpty())
        }, restore = {
            WardFilters(WardFilterOptions(it[0] as String, WardViewMode.valueOf(it[1] as String),
                it[2] as Boolean, it[3] as Boolean, it[4] as Boolean, it[5] as Boolean,
                it[6] as Boolean, (it[7] as String).takeIf(String::isNotEmpty)?.let { value -> WardTaskFilter.fromSaved(value) }))
        })
    }
}
