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
            AppDivider()
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
