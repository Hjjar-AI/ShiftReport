package com.hos.rushdpatients.ui.demo

import androidx.annotation.StringRes

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.ward.DashboardFilter
import com.hos.rushdpatients.ui.ward.PatientSortOptions
import com.hos.rushdpatients.ui.ward.WardViewOption
import com.hos.rushdpatients.ui.ward.sortReviewLabel

internal enum class DemoDestination(@StringRes private val titleRes: Int) {
    PATIENTS(R.string.ui_patients), DASHBOARD(R.string.ui_ward_drawer_010),
    TEAM(R.string.ui_shift_doctor_picker_001), REPORT(R.string.ui_demo_navigation_009), SETTINGS(R.string.ui_settings);

    fun title(uiText: UiStrings): String = uiText(titleRes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DemoDrawer(
    state: DemoWardState, selected: DemoDestination,
    onNavigate: (DemoDestination) -> Unit,
    onRecycle: () -> Unit, onReset: () -> Unit, onExit: () -> Unit
) {
    val uiText = rememberUiStrings()
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            AppSection(uiText(R.string.ui_demo_navigation_001)) {
                Text(state.actor.fullName, style = MaterialTheme.typography.titleMedium)
                Text(uiText(R.string.ui_demo_navigation_002, state.date), style = MaterialTheme.typography.bodySmall)
            }
            Text(uiText(R.string.ui_demo_navigation_003),
                style = MaterialTheme.typography.bodySmall)
            DemoDestination.entries.forEach { destination ->
                NavigationDrawerItem(selected = selected == destination,
                    label = { Text(destination.title(uiText)) },
                    icon = { Icon(when (destination) {
                        DemoDestination.PATIENTS -> Icons.Filled.ViewAgenda
                        DemoDestination.DASHBOARD -> Icons.Filled.Dashboard
                        DemoDestination.TEAM -> Icons.Filled.Groups
                        DemoDestination.REPORT -> Icons.Filled.Description
                        DemoDestination.SETTINGS -> Icons.Filled.Settings
                    }, contentDescription = null) }, onClick = { onNavigate(destination) })
            }
            Divider()
            AppTextButton(onClick = onRecycle, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(R.string.ui_demo_navigation_004, uiNumber(state.deleted.size)))
            }
            AppTextButton(onClick = onReset, enabled = !state.exporting, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(R.string.ui_demo_navigation_005))
            }
            AppOutlinedButton(onClick = onExit, enabled = !state.exporting, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(R.string.ui_demo_navigation_006))
            }
        }
    }
}

internal fun DashboardFilter.demoLabel(uiText: UiStrings): String = when (this) {
    DashboardFilter.ALL -> uiText(R.string.ui_all)
    DashboardFilter.MINE -> uiText(R.string.ui_ward_view_options_dialog_005)
    DashboardFilter.URGENT -> uiText(R.string.ui_handover_dashboard_006)
    DashboardFilter.WARNINGS -> uiText(R.string.ui_ward_screen_009)
    DashboardFilter.UNASSIGNED -> uiText(R.string.ui_handover_dashboard_014)
    DashboardFilter.NEW_ADMISSIONS -> uiText(R.string.ui_handover_dashboard_016)
    DashboardFilter.TASK_PENDING -> uiText(R.string.ui_ward_screen_011)
    DashboardFilter.TASK_OVERDUE -> uiText(R.string.ui_ward_patient_list_controls_008)
    DashboardFilter.TASK_UNASSIGNED -> uiText(R.string.ui_ward_screen_012)
}

/** A reduced version of the ward's options, with the same fields and saved-sort editor. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DemoViewOptions(
    filter: DashboardFilter, onFilter: (DashboardFilter) -> Unit,
    expanded: Boolean, onExpanded: (Boolean) -> Unit,
    compact: Boolean, onCompact: (Boolean) -> Unit,
    twoColumn: Boolean, onTwoColumn: (Boolean) -> Unit,
    sort: SortSpec, onSort: (SortSpec) -> Unit,
    onReset: () -> Unit, onDismiss: () -> Unit
) {
    val uiText = rememberUiStrings()
    var selected by rememberSaveable { mutableStateOf(WardViewOption.FILTERS) }
    val sections = listOf(WardViewOption.FILTERS, WardViewOption.CARDS, WardViewOption.SORT)
    AlertDialog(onDismissRequest = onDismiss, title = {
        Column {
            Text(uiText(R.string.ui_ward_patient_list_controls_004))
            AppSectionIndex(sections, selected, label = { it.label(uiText) }, onSelect = { selected = it })
        }
    }, text = {
        Column(Modifier.appDialogBody().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            when (selected) {
                WardViewOption.FILTERS -> {
                    Text(uiText(R.string.ui_demo_navigation_007), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        DashboardFilter.entries.forEach { value ->
                            FilterChip(selected = filter == value, onClick = { onFilter(value) },
                                label = { Text(value.demoLabel(uiText)) })
                        }
                    }
                }
                WardViewOption.CARDS -> {
                    AppToggleRow(uiText(R.string.ui_ward_view_options_dialog_009), checked = expanded, onCheckedChange = onExpanded)
                    AppToggleRow(uiText(R.string.ui_ward_view_options_dialog_010), checked = twoColumn, onCheckedChange = onTwoColumn)
                    AppToggleRow(uiText(R.string.ui_ward_view_options_dialog_011), checked = compact, onCheckedChange = onCompact)
                }
                WardViewOption.SORT -> {
                    Text(uiText(R.string.ui_demo_navigation_008), style = MaterialTheme.typography.bodySmall)
                    Text(sortReviewLabel(uiText, sort), style = MaterialTheme.typography.bodySmall)
                    PatientSortOptions(initial = sort, initialRevision = 0,
                        onApply = { spec, _, _ -> onSort(spec) })
                }
                WardViewOption.GROUPING -> Unit
            }
        }
    }, confirmButton = { AppDialogDismissAction(onClick = onDismiss, text = uiText(R.string.ui_done)) },
        dismissButton = { AppTextButton(onClick = onReset) { Text(uiText(R.string.ui_ward_patient_list_controls_005)) } })
}
