package com.hos.rushdpatients.ui.ward

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.patient.WardSummary
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.demo.DemoData
import com.hos.rushdpatients.ui.theme.*

/** Production components with isolated dummy data; no ViewModel, storage, network or screenshot suite. */
@Preview(name = "Pinned filters — 320dp RTL", locale = "ar", widthDp = 320, heightDp = 640)
@Preview(name = "Pinned filters — 320dp dark 200%", locale = "ar", widthDp = 320, heightDp = 720,
    fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CompactWardPreview() = SelectedWardPreview(UiPreferences(reducedMotion = true))

@Preview(name = "Quiet — RTL dark 150%", locale = "ar", widthDp = 360, heightDp = 720,
    fontScale = 1.5f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QuietWardPreview() = SelectedWardPreview(UiPreferences(profile = "quiet", reducedMotion = true))

@Preview(name = "Clear — RTL AMOLED 200%", locale = "ar", widthDp = 320, heightDp = 720, fontScale = 2f)
@Composable
private fun ClearWardPreview() = SelectedWardPreview(UiPreferences(profile = "clear", reducedMotion = true), AppAppearance.AMOLED)

@Preview(name = "Relaxed — RTL narrow 200%", locale = "ar", widthDp = 320, heightDp = 720, fontScale = 2f)
@Composable
private fun RelaxedWardPreview() = SelectedWardPreview(UiPreferences(profile = "relaxed", reducedMotion = true))

@Preview(name = "Search — RTL narrow dark", locale = "ar", widthDp = 320, heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchWardPreview() = SelectedWardPreview(UiPreferences(reducedMotion = true), search = true)

@Composable
private fun SelectedWardPreview(preferences: UiPreferences, appearance: AppAppearance = AppAppearance.SYSTEM,
    search: Boolean = false) {
    val doctors = remember { DemoData.doctors() }
    val patients = remember { DemoData.patients(doctors).take(4).mapIndexed { index, patient ->
        if (index == 0) patient.copy(name = "شاي بالنعناع والزنجبيل والقرفة — اسم تجريبي طويل") else patient
    } }
    val names = remember { doctors.associate { it.id to it.fullName } }
    RushdPatientsTheme(appearance = appearance, uiPreferences = preferences) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalWardDayLabel provides com.hos.rushdpatients.util.ShiftDate.current()) {
            val uiText = rememberUiStrings()
            val summary = WardSummary.from(patients, doctors.first().id)
            val visible = if (search) patients.filter { it.name.contains("شاي") } else patients
            val counts = mapOf(DashboardFilter.ALL to patients.size, DashboardFilter.MINE to summary.mine,
                DashboardFilter.URGENT to summary.urgent, DashboardFilter.TASK_PENDING to summary.tasks.pending)
            Surface(Modifier.fillMaxSize()) {
                Column {
                    // A persistent important notice precedes the actual pinned controls/grid.
                    AppNotice(uiText(R.string.ui_ward_workflow_strip_005), kind = NoticeKind.WARNING, compact = true)
                    Box(Modifier.weight(1f)) {
                        WardPatientGrid(
                            state = WardUiState(loading = false, patients = patients, groupByMode = GroupByMode.NONE),
                            visiblePatients = visible, dualPane = false, patientGridState = rememberLazyGridState(),
                            scrollBottomClearance = UiSpacing.screen, pinnedPatientIds = emptyList(), collapsedGroups = emptyList(),
                            onToggleGroup = {}, onResetFilters = {},
                            controlsContent = {
                                Column(Modifier.padding(horizontal = UiSpacing.screenGutter)) {
                                    WardPatientListControls(visible.size, patients.size, counts, null,
                                        activeFilters = if (search) listOf("بحث: شاي") else emptyList(),
                                        selectedPreset = if (search) null else DashboardFilter.ALL,
                                        onPreset = {}, onOptions = {}, onReset = {},
                                        showSearch = search, query = if (search) "شاي" else "", onQueryChange = {},
                                        requestFocus = false, onFocusRequested = {})
                                }
                            }, headerContent = {}, patientCard = { patient, modifier ->
                                PatientCard(PatientCardState(patient = patient, doctorNames = names),
                                    PatientCardActions.None, modifier)
                            })
                    }
                    AppTabBar(listOf(AppTabItem(uiText(R.string.ui_all), AppIcons.ViewAgenda),
                        AppTabItem(uiText(R.string.ui_ward_navigation_chrome_002), AppIcons.Dashboard)), 0, {})
                }
            }
        }
    }
}
