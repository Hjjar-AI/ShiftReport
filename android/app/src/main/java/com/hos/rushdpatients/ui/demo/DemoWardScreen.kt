package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.ui.components.AppSnackbarHost
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import com.hos.rushdpatients.pdf.PdfStyle
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.theme.*
import com.hos.rushdpatients.ui.ward.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Only temporary settings/team/local-export chrome is demo-specific. The clinical workspace is real. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoWardScreen(onExit: () -> Unit, viewModel: DemoWardViewModel = hiltViewModel()) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ward by viewModel.controller.state.collectAsStateWithLifecycle()
    val pages = rememberSaveableStateHolder()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(DemoDestination.PATIENTS) }
    var appearance by rememberSaveable { mutableStateOf(AppAppearance.LIGHT) }
    var accent by rememberSaveable { mutableStateOf(AppThemePreset.VANILLA) }
    var uiPreferencesSetting by rememberSaveable { mutableStateOf(UiPreferences().encode()) }
    val uiPreferences = UiPreferences.decode(uiPreferencesSetting)
    var font by rememberSaveable { mutableStateOf(AppFontScale.NORMAL) }
    var showExit by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showDoctors by remember { mutableStateOf(false) }
    var showRecycle by remember { mutableStateOf(false) }
    val main = tab == DemoDestination.PATIENTS || tab == DemoDestination.DASHBOARD
    val baseDensity = LocalDensity.current
    val density = remember(baseDensity, font) {
        Density(baseDensity.density, (baseDensity.fontScale * font.multiplier).coerceIn(.92f, 2f))
    }
    fun navigate(destination: DemoDestination) { tab = destination }
    @Composable
    fun DemoMenu(close: (after: () -> Unit) -> Unit) {
        DemoDrawer(state, tab, onNavigate = { close { navigate(it) } },
            onRecycle = { close { showRecycle = true } },
            onReset = { close { showReset = true } }, onExit = { close { showExit = true } })
    }
    val navigation = WardNavigation(
        onOpenReport = { navigate(DemoDestination.REPORT) },
        onOpenAdmin = { navigate(DemoDestination.TEAM) },
        onOpenDoctors = { navigate(DemoDestination.TEAM) },
        onOpenSettings = { navigate(DemoDestination.SETTINGS) },
        onOpenAbout = { viewModel.message(uiText(R.string.ui_demo_local_only)) },
        onOpenProject = { viewModel.message(uiText(R.string.ui_demo_local_only)) },
        onOpenDataManagement = { viewModel.message(uiText(R.string.ui_demo_local_only)) },
        onOpenWorkspace = { navigate(DemoDestination.SETTINGS) },
        onOpenAnnouncement = { viewModel.message(uiText(R.string.ui_demo_local_only)) }
    )
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        viewModel.exportPreparedPdf(it)
    }
    BackHandler(enabled = !state.exporting && viewModel.editor.dialog == null) {
        if (main && tab == DemoDestination.PATIENTS) showExit = true else tab = DemoDestination.PATIENTS
    }
    LaunchedEffect(state.message, main) {
        if (!main) state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }
    RushdPatientsTheme(preset = accent, appearance = appearance, uiPreferences = uiPreferences) {
        AppSystemBars()
        CompositionLocalProvider(LocalDensity provides density) {
            if (main) pages.SaveableStateProvider("ward") {
                key(state.shiftId) {
                    WardWorkspace(isAdmin = false, currentDoctorId = state.actorId, navigation = navigation,
                        viewModel = viewModel.controller,
                        initialView = if (tab == DemoDestination.DASHBOARD) WardViewMode.DASHBOARD else WardViewMode.ALL,
                        onViewChanged = { tab = if (it == WardViewMode.DASHBOARD) DemoDestination.DASHBOARD else DemoDestination.PATIENTS },
                        drawerContent = { close -> DemoMenu(close) })
                }
            } else ModalNavigationDrawer(drawerState = drawer, drawerContent = {
                DemoMenu { after -> scope.launch { drawer.close(); after() } }
            }) {
                Scaffold(topBar = { AppTopBar(tab.title(uiText), onBack = { tab = DemoDestination.PATIENTS }) },
                    snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
                    pages.SaveableStateProvider(tab.name) {
                        val modifier = Modifier.fillMaxSize().padding(padding).padding(UiPadding.screen)
                        when (tab) {
                            DemoDestination.TEAM -> DemoTeamContent(state, { showDoctors = true }, viewModel::selectActor, modifier)
                            DemoDestination.SETTINGS -> DemoPreferencesContent(appearance, { appearance = it }, accent, { accent = it },
                                font, { font = it }, ward.patientDetailsExpanded, ward.twoColumn, ward.compactCards,
                                state.saveAndNext, viewModel::setSaveAndNext, uiPreferences, { uiPreferencesSetting = it.encode() }, modifier)
                            DemoDestination.REPORT -> DemoReportContent(state, { showDoctors = true }, onExport = { options, style ->
                                viewModel.preparePdfExport(options, style == PdfStyle.CARDS)
                                try { export.launch("ShiftReport_DEMO_${state.date}.pdf") }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { viewModel.exportPreparedPdf(null); viewModel.message(e.message ?: uiText(R.string.ui_demo_ward_screen_008)) }
                            }, modifier = modifier)
                            else -> Unit
                        }
                    }
                }
            }
            if (showExit) ConfirmDialog(uiText(R.string.ui_demo_navigation_006), uiText(R.string.ui_demo_ward_screen_009),
                confirmText = uiText(R.string.ui_demo_navigation_006),
                onConfirm = { showExit = false; viewModel.endSession(); onExit() }, onDismiss = { showExit = false })
            if (showReset) ConfirmDialog(uiText(R.string.ui_demo_ward_screen_011), uiText(R.string.ui_demo_ward_screen_012),
                confirmText = uiText(R.string.ui_demo_ward_screen_013), destructive = true,
                onConfirm = { viewModel.reset(); tab = DemoDestination.PATIENTS; showReset = false }, onDismiss = { showReset = false })
            if (showDoctors) ShiftDoctorPicker(allDoctors = state.doctors, initialSelected = state.shiftDoctorIds,
                initialRevision = state.revision, onConfirm = { ids, revision, onStale ->
                    if (revision != viewModel.state.value.revision) onStale(ward.shift)
                    else { viewModel.selectDoctors(ids); showDoctors = false }
                }, onDismiss = { showDoctors = false })
            if (showRecycle) PatientRecycleBinDialog(state.deleted, readOnly = false,
                onRestore = viewModel.controller::restorePatient, onDismiss = { showRecycle = false })
        }
    }
}
