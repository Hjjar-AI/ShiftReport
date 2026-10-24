package com.hos.rushdpatients.testutil

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import com.hos.rushdpatients.ui.RootViewModel
import com.hos.rushdpatients.ui.WardAppRoot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.config.*
import com.hos.rushdpatients.domain.auth.*
import com.hos.rushdpatients.domain.backup.EncryptedBackupManager
import com.hos.rushdpatients.domain.export.*
import com.hos.rushdpatients.domain.report.*
import com.hos.rushdpatients.network.ReportSender
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.sync.*
import com.hos.rushdpatients.ui.admin.AdminViewModel
import com.hos.rushdpatients.ui.announcement.AnnouncementViewModel
import com.hos.rushdpatients.domain.announcement.AnnouncementPublisher
import com.hos.rushdpatients.domain.usecase.*
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.ui.connection.ProjectConnectionViewModel
import com.hos.rushdpatients.ui.doctors.DoctorsViewModel
import com.hos.rushdpatients.ui.login.*
import com.hos.rushdpatients.ui.navigation.*
import com.hos.rushdpatients.ui.report.ReportPreviewViewModel
import com.hos.rushdpatients.ui.settings.SettingsViewModel
import com.hos.rushdpatients.ui.setup.*
import com.hos.rushdpatients.ui.ward.WardViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

/** Production screens and WardNavHost, real isolated models; no installed clinical DB. */
abstract class ProductionUiFixture {
    protected val server = SharedTelegramServer()
    protected lateinit var client: IndependentClient
    protected var scenario: ActivityScenario<IsolatedWorkflowActivity>? = null
    protected lateinit var nav: NavHostController
    protected lateinit var setup: ProjectSetupViewModel
    protected lateinit var login: LoginViewModel
    protected lateinit var ward: WardViewModel
    protected lateinit var report: ReportPreviewViewModel
    protected lateinit var doctorVm: DoctorsViewModel
    protected lateinit var settingsVm: SettingsViewModel
    protected lateinit var root: RootViewModel
    @Volatile protected var route: String? = null
    protected var pickerOwner: ActivityResultRegistryOwner? = null
    protected var fontScale by mutableStateOf(1f)
    protected var widthDp by mutableStateOf(360)
    protected var heightDp by mutableStateOf(900)
    protected var nativeWindow = false
    private lateinit var text: UiStrings
    private lateinit var ledger: ReportDeliveryLedger
    private lateinit var sender: ReportSender
    private lateinit var csv: PatientCsvExporter
    private lateinit var bootstrap: BootstrapManager
    private fun key(type: Class<*>) = "androidx.lifecycle.ViewModelProvider.DefaultKey:${type.name}"
    private inline fun <reified T : ViewModel> get(store: ViewModelStore, create: () -> T): T {
        val key = key(T::class.java)
        val existing = store[key]
        if (existing != null) return existing as T
        return create().also { store.put(key, it) }
    }
    @Before fun openIsolatedUi() = runBlocking<Unit> {
        client = IndependentClient("ui", server); client.initialize(); client.seedPatient()
        val enrolled = client.actor.copy(extraOptions = setOf("pin:" + PasswordHasher().hash("2468")))
        client.doctors.upsert(enrolled)
        val registry = DoctorsRegistryCodec.encode(listOf(enrolled))
        server.files["registry"] = registry.toByteArray(Charsets.UTF_8)
        server.state = SyncState(doctorsData = registry, doctorsFileId = "registry", doctorsMessageId = 2)
        client.settings.put(AppConstants.SETTING_DOCTORS_BASE_REGISTRY, server.state.doctorsData.orEmpty())
        text = UiStrings(client.context)
        ledger = ReportDeliveryLedger(client.settings, client.sessions, client.doctors)
        sender = ReportSender(client.context, ProjectDataCipher(client.projects, client.dispatchers), client.telegram,
            client.topics, client.shifts, client.audit, ledger)
        csv = PatientCsvExporter(client.context, client.shifts, client.patients, client.doctors, MediaStoreSaver(), client.dispatchers)
        bootstrap = BootstrapManager(client.context, client.telegram, client.topics, server.json, client.doctors)
    }
    protected fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { action() }
    protected fun recreateActivity() {
        onMain { route = null }
        checkNotNull(scenario).recreate()
    }
    protected fun launch(loggedIn: Boolean = true, joinSetup: Boolean = false) {
        if (!loggedIn) client.sessions.clear()
        if (joinSetup) client.projects.saveDraft(client.projects.current())
        IsolatedWorkflowActivity.install = { activity ->
            val store = activity.viewModelStore
            get(store) { ProjectConnectionViewModel(text, ProjectConnectionManager(client.projects,
                ProjectProvisioningManager(client.context, client.dispatchers), client.telegram, client.authorizer, client.dispatchers),
                client.context, client.dispatchers) }
            root = get(store) { RootViewModel(text, client.sessions, client.settings, BiometricHelper(client.context),
                client.audit, client.doctors, PasswordHasher(), client.projects) }
            setup = get(store) { ProjectSetupViewModel(text, client.context, client.projects, client.telegram, bootstrap,
                BootstrapSeeder(client.telegram, client.topics, client.projects), client.sessions, client.settings,
                ProjectProvisioningManager(client.context, client.dispatchers)) }
            login = get(store) { LoginViewModel(text, client.context, client.doctors, client.sessions, bootstrap,
                TelegramLoginManager(client.telegram), LocalPinLoginManager(client.doctors, client.settings, PasswordHasher(), client.dispatchers),
                PasswordHasher(), client.audit, client.settings, client.projects) }
        }
        IsolatedWorkflowActivity.content = { activity ->
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply {
                if (!nativeWindow) screenWidthDp = widthDp
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides (if (nativeWindow) density else Density(density.density, fontScale)), LocalConfiguration provides configuration,
                LocalActivityResultRegistryOwner provides (pickerOwner ?: activity)) {
                val bounds = if (nativeWindow) Modifier.fillMaxSize()
                    else Modifier.widthIn(max = widthDp.dp).heightIn(max = heightDp.dp).fillMaxSize()
                MaterialTheme { Box(bounds) {
                    WardAppRoot(activity, root) {
                        val controller = rememberNavController()
                        nav = controller
                        // Install isolated models before Hilt looks up each entry. The graph itself is production.
                        remember(controller) {
                            controller.addOnDestinationChangedListener { navigation, destination, arguments ->
                                val store = checkNotNull(navigation.currentBackStackEntry).viewModelStore
                                when (destination.route) {
                                    Routes.WARD -> ward = get(store) { wardModel() }
                                    Routes.REPORT_PREVIEW -> report = get(store) { reportModel(arguments?.getString("shiftId").orEmpty()) }
                                    Routes.DOCTORS -> doctorVm = get(store) { doctorsModel() }
                                    Routes.SETTINGS, Routes.SETTINGS_DETAIL, Routes.PROJECT_SETTINGS, Routes.DATA_MANAGEMENT, Routes.DIAGNOSTICS -> {
                                        settingsVm = get(store) { settingsModel() }
                                        get(store) { ProjectConnectionViewModel(text, ProjectConnectionManager(client.projects,
                                            ProjectProvisioningManager(client.context, client.dispatchers), client.telegram, client.authorizer, client.dispatchers), client.context, client.dispatchers) }
                                    }
                                    Routes.ADMIN -> get(store) { AdminViewModel(text, client.doctors, client.sessions,
                                        PromoteAdminUseCase(client.doctorMutations), DemoteAdminUseCase(client.doctorMutations), client.sync) }
                                    Routes.ANNOUNCEMENT -> get(store) { AnnouncementViewModel(text, AnnouncementPublisher(client.telegram,
                                        client.topics, client.projects, ProjectDataCipher(client.projects, client.dispatchers), client.remote,
                                        client.doctorSync, ledger, client.audit, client.dispatchers), ledger) }
                                    else -> error("No isolated models configured for this destination")
                                }
                                route = destination.route
                            }
                            true
                        }
                        controller
                    }
                } }
            }
        }
        scenario = ActivityScenario.launch(IsolatedWorkflowActivity::class.java)
    }
    private fun wardModel() = WardViewModel(text, client.context, client.shifts, client.patients, client.wardMutations,
        client.doctors, client.sync, client.settings, csv, client.cursors, client.sessions, client.audit, client.authorizer,
        server.json, ledger, client.projects)
    private fun reportModel(id: String): ReportPreviewViewModel {
        val classic = PdfReportExporter(client.context)
        return ReportPreviewViewModel(text, SavedStateHandle(mapOf("shiftId" to id)), client.context, client.reports,
            TextReportBuilder(), sender, classic, PdfCardsExporter(classic), PdfAssignmentExporter(client.authorizer, client.context),
            MediaStoreSaver(), client.settings, client.doctors, client.shifts, client.wardMutations, client.sessions,
            client.authorizer, client.sync, client.projects, ledger)
    }
    private fun doctorsModel() = DoctorsViewModel(text, client.context, client.doctors, client.settings,
        client.doctorMutations, client.audit, client.sync, client.authorizer,
        PendingExportStore(client.context, client.dispatchers), SavedStateHandle())
    private fun settingsModel() = SettingsViewModel(text, client.context, client.settings, client.doctors, client.doctorMutations,
        client.shifts, client.patients, client.cursors, client.sessions, client.sync,
        EmptyStartCoordinator(client.context, client.db, client.settings, client.shifts, client.cursors, client.audit,
            client.doctorSync, client.remote, client.telegram, client.topics, client.dispatchers), csv, BiometricHelper(client.context),
        EncryptedBackupManager(client.context, client.db, client.shifts, client.patients, client.doctors, client.settings, client.dispatchers),
        client.projects, ProjectProvisioningManager(client.context, client.dispatchers), client.authorizer,
        PendingExportStore(client.context, client.dispatchers), SavedStateHandle())
    @After fun closeIsolatedUi() {
        scenario?.close(); IsolatedWorkflowActivity.clearFixture()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        if (::client.isInitialized) client.close()
    }
}
