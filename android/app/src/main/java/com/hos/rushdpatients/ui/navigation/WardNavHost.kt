package com.hos.rushdpatients.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hos.rushdpatients.domain.auth.Session
import com.hos.rushdpatients.migration.VbaImportScreen
import com.hos.rushdpatients.ui.admin.AdminScreen
import com.hos.rushdpatients.ui.about.IntroAboutScreen
import com.hos.rushdpatients.ui.announcement.AnnouncementScreen
import com.hos.rushdpatients.ui.doctors.DoctorsScreen
import com.hos.rushdpatients.ui.report.ReportPreviewScreen
import com.hos.rushdpatients.ui.settings.SettingsScreen
import com.hos.rushdpatients.ui.settings.SettingsWorkspace
import com.hos.rushdpatients.ui.ward.WardNavigation
import com.hos.rushdpatients.ui.ward.WardScreen

@Composable
fun WardNavHost(
    session: Session,
    onSignOut: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.WARD
    ) {
        composable(Routes.WARD) {
            WardScreen(
                isAdmin = session.isAdmin,
                currentDoctorId = session.doctorId,
                navigation = WardNavigation(onOpenReport = { shiftId ->
                    navController.navigate(Routes.reportPreview(shiftId))
                },
                onOpenAdmin = { navController.navigate(Routes.ADMIN) },
                onOpenDoctors = { if (session.isAdmin) navController.navigate(Routes.DOCTORS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenDataManagement = { navController.navigate(Routes.DATA_MANAGEMENT) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenProject = { navController.navigate(Routes.PROJECT_SETTINGS) },
                onOpenWorkspace = { navController.navigate(Routes.settingsWorkspace(it.name)) },
                onOpenAnnouncement = { navController.navigate(Routes.ANNOUNCEMENT) })
            )
        }

        composable(
            route = Routes.REPORT_PREVIEW,
            arguments = listOf(navArgument("shiftId") { type = NavType.StringType })
        ) {
            ReportPreviewScreen(onBack = { navController.popBackStack() },
                onOpenDoctors = { if (session.isAdmin) navController.navigate(Routes.DOCTORS) })
        }

        composable(Routes.ADMIN) {
            AdminRoute(session.isAdmin, { navController.popBackStack() }) {
                AdminScreen(onBack = { navController.popBackStack() },
                    onOpenDoctors = { navController.navigate(Routes.DOCTORS) })
            }
        }
        composable(Routes.DOCTORS) {
            AdminRoute(session.isAdmin, { navController.popBackStack() }) {
                DoctorsScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Routes.ANNOUNCEMENT) {
            AdminRoute(session.isAdmin, { navController.popBackStack() }) {
                AnnouncementScreen(onBack = { navController.popBackStack() })
            }
        }

        listOf(
            Routes.SETTINGS to SettingsWorkspace.PREFERENCES,
            Routes.DATA_MANAGEMENT to SettingsWorkspace.DATA,
            Routes.PROJECT_SETTINGS to SettingsWorkspace.PROJECT,
            Routes.DIAGNOSTICS to SettingsWorkspace.DIAGNOSTICS
        ).forEach { (route, workspace) ->
            composable(route) {
                SettingsScreen(
                    isAdmin = session.isAdmin,
                    workspace = workspace,
                    onOpenDoctors = { if (session.isAdmin) navController.navigate(Routes.DOCTORS) },
                    onOpenWorkspace = { navController.navigate(Routes.settingsWorkspace(it.name)) },
                    onBack = { navController.popBackStack() },
                    onSignOut = {
                        onSignOut()
                        navController.popBackStack(Routes.WARD, inclusive = false)
                    },
                    onOpenVbaImport = { navController.navigate(Routes.VBA_IMPORT) }
                )
            }
        }

        composable(Routes.SETTINGS_DETAIL, arguments = listOf(navArgument("workspace") { type = NavType.StringType })) { entry ->
            val workspace = SettingsWorkspace.entries.firstOrNull { it.name == entry.arguments?.getString("workspace") }
                ?: SettingsWorkspace.PREFERENCES
            SettingsScreen(isAdmin = session.isAdmin, workspace = workspace,
                onOpenDoctors = { if (session.isAdmin) navController.navigate(Routes.DOCTORS) },
                onOpenWorkspace = { navController.navigate(Routes.settingsWorkspace(it.name)) },
                onBack = { navController.popBackStack() },
                onSignOut = { onSignOut(); navController.popBackStack(Routes.WARD, inclusive = false) },
                onOpenVbaImport = { navController.navigate(Routes.VBA_IMPORT) })
        }

        composable(Routes.VBA_IMPORT) {
            AdminRoute(session.isAdmin, { navController.popBackStack() }) {
                VbaImportScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.ABOUT) {
            IntroAboutScreen(splash = false, onBack = { navController.popBackStack() })
        }
    }
}

/** Navigation presentation only. Privileged mutations retain their live repository/service checks. */
@Composable
private fun AdminRoute(allowed: Boolean, onDenied: () -> Unit, content: @Composable () -> Unit) {
    if (allowed) content() else LaunchedEffect(allowed) { onDenied() }
}
