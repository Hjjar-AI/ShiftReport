package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.ui.components.AppSnackbarHost
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.doctors.DoctorsWorkspace
import com.hos.rushdpatients.ui.report.ReportWorkspace
import com.hos.rushdpatients.ui.report.AssignmentsExportWorkspace
import com.hos.rushdpatients.ui.about.IntroAboutScreen
import com.hos.rushdpatients.ui.theme.*
import com.hos.rushdpatients.ui.ward.*

/** The session shell chooses isolated controllers; clinical/report/doctor screens are shared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoWardScreen(onExit: () -> Unit, viewModel: DemoWardViewModel = hiltViewModel()) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ward by viewModel.controller.state.collectAsStateWithLifecycle()
    val report by viewModel.report.state.collectAsStateWithLifecycle()
    val busy = report.isBusy && !report.loading
    val pages = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(DemoDestination.PATIENTS) }
    var appearance by rememberSaveable { mutableStateOf(AppAppearance.LIGHT) }
    var accent by rememberSaveable { mutableStateOf(AppThemePreset.VANILLA) }
    var uiPreferencesSetting by rememberSaveable { mutableStateOf(UiPreferences().encode()) }
    val uiPreferences = UiPreferences.decode(uiPreferencesSetting)
    var font by rememberSaveable { mutableStateOf(AppFontScale.NORMAL) }
    var showExit by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showActor by remember { mutableStateOf(false) }
    var showRecycle by remember { mutableStateOf(false) }
    var reviewPatientIds by remember { mutableStateOf<Set<String>?>(null) }
    val main = tab == DemoDestination.PATIENTS || tab == DemoDestination.DASHBOARD
    val baseDensity = LocalDensity.current
    val density = remember(baseDensity, font) {
        Density(baseDensity.density, (baseDensity.fontScale * font.multiplier).coerceIn(.92f, 2f))
    }
    fun openReport() { viewModel.report.load(); tab = DemoDestination.REPORT }
    val navigation = WardNavigation(
        onOpenReport = { openReport() },
        onOpenAssignments = { viewModel.report.load(); tab = DemoDestination.ASSIGNMENTS },
        onOpenAdmin = { tab = DemoDestination.TEAM },
        onOpenDoctors = { tab = DemoDestination.TEAM },
        onOpenSettings = { tab = DemoDestination.SETTINGS },
        onOpenAbout = { tab = DemoDestination.ABOUT },
        onOpenProject = { viewModel.message(uiText(R.string.ui_demo_local_only)) },
        onOpenDataManagement = { viewModel.controller.bringLatestData() },
        onOpenWorkspace = { viewModel.message(uiText(R.string.ui_demo_local_only)) }
    )
    @Composable
    fun DemoMenu(close: (after: () -> Unit) -> Unit) {
        DemoDrawer(state, ward, busy, onAction = { action -> close {
            navigation.openDrawerAction(action, state.shiftId, isAdmin = true,
                onRecycleBin = { showRecycle = true })
        } }, onUndoFetch = if (viewModel.canUndoFetch) ({ viewModel.undoFetch() }) else null, onActor = { close { showActor = true } },
            onReset = { close { showReset = true } }, onExit = { close { showExit = true } })
    }
    BackHandler(enabled = !busy && viewModel.editor.dialog == null && tab != DemoDestination.REPORT && tab != DemoDestination.ASSIGNMENTS) {
        if (main && tab == DemoDestination.PATIENTS) {
            if (reviewPatientIds != null) { reviewPatientIds = null; openReport() } else showExit = true
        } else tab = DemoDestination.PATIENTS
    }
    LaunchedEffect(state.message, main) {
        if (!main) state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }
    // The label alone refreshes on foreground; patient contents never depend on a ticking task clock.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) viewModel.refreshDayLabel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.refreshDayLabel()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(tab, state.shiftId) {
        // Recreated demo sessions never retain a stale report or clinical state from saved navigation.
        if (tab in setOf(DemoDestination.REPORT, DemoDestination.ASSIGNMENTS) && report.shift?.id != state.shiftId) viewModel.report.load()
    }
    RushdPatientsTheme(preset = accent, appearance = appearance, uiPreferences = uiPreferences) {
        AppSystemBars()
        CompositionLocalProvider(LocalDensity provides density) {
            when {
                main -> pages.SaveableStateProvider("ward") {
                    key(state.shiftId) {
                        WardWorkspace(isAdmin = true, currentDoctorId = state.actorId, navigation = navigation,
                            viewModel = viewModel.controller,
                            initialView = if (tab == DemoDestination.DASHBOARD) WardViewMode.DASHBOARD else WardViewMode.ALL,
                            onViewChanged = { tab = if (it == WardViewMode.DASHBOARD) DemoDestination.DASHBOARD else DemoDestination.PATIENTS },
                            drawerContent = { close -> DemoMenu(close) }, patientFilterIds = reviewPatientIds,
                            onBackToPreview = if (reviewPatientIds == null) null else ({ reviewPatientIds = null; openReport() }))
                    }
                }
                tab == DemoDestination.REPORT -> pages.SaveableStateProvider("report-${state.shiftId}") {
                    ReportWorkspace(onBack = { tab = DemoDestination.PATIENTS },
                        onOpenDoctors = { tab = DemoDestination.TEAM }, viewModel = viewModel.report,
                        onReviewPatients = { _, ids -> reviewPatientIds = ids; tab = DemoDestination.PATIENTS })
                }
                tab == DemoDestination.ASSIGNMENTS -> pages.SaveableStateProvider("assignments-${state.shiftId}") {
                    AssignmentsExportWorkspace(onBack = { tab = DemoDestination.PATIENTS }, controller = viewModel.report)
                }
                tab == DemoDestination.ABOUT -> IntroAboutScreen(splash = false,
                    onBack = { tab = DemoDestination.PATIENTS })
                tab == DemoDestination.TEAM -> pages.SaveableStateProvider("doctors-${state.shiftId}") {
                    DoctorsWorkspace(onBack = { tab = DemoDestination.PATIENTS }, viewModel = viewModel.doctors)
                }
                else -> Scaffold(topBar = { AppTopBar(tab.title(uiText), onBack = { tab = DemoDestination.PATIENTS }) },
                    snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
                    pages.SaveableStateProvider("settings") {
                        DemoPreferencesContent(appearance, { appearance = it }, accent, { accent = it },
                            font, { font = it }, ward.patientDetailsExpanded, ward.twoColumn, ward.compactCards,
                            uiPreferences, { uiPreferencesSetting = it.encode() },
                            Modifier.fillMaxSize().padding(padding).padding(UiPadding.screen))
                    }
                }
            }
            if (showExit) ConfirmDialog(uiText(R.string.ui_demo_navigation_006), uiText(R.string.ui_demo_ward_screen_009),
                confirmText = uiText(R.string.ui_demo_navigation_006),
                onConfirm = { showExit = false; viewModel.endSession(); onExit() }, onDismiss = { showExit = false })
            if (showReset) ConfirmDialog(uiText(R.string.ui_demo_ward_screen_011), uiText(R.string.ui_demo_ward_screen_012),
                confirmText = uiText(R.string.ui_demo_ward_screen_013), destructive = true,
                onConfirm = { viewModel.reset(); reviewPatientIds = null; tab = DemoDestination.PATIENTS; showReset = false },
                onDismiss = { showReset = false })
            if (showActor) AppAlertDialog(onDismissRequest = { showActor = false },
                title = { Text(uiText(R.string.ui_demo_secondary_screens_011)) }, text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(uiText(R.string.ui_demo_secondary_screens_012))
                        state.doctors.forEach { doctor ->
                            AppChoiceRow(label = doctor.fullName, selected = state.actorId == doctor.id,
                                onClick = { viewModel.selectActor(doctor.id); showActor = false })
                        }
                    }
                }, confirmButton = { AppTextButton(onClick = { showActor = false }) { Text(uiText(R.string.ui_close)) } })
            if (showRecycle) PatientRecycleBinDialog(state.deleted, readOnly = busy, automaticCleanup = false,
                onRestore = viewModel.controller::restorePatient, onDismiss = { showRecycle = false })
        }
    }
}
