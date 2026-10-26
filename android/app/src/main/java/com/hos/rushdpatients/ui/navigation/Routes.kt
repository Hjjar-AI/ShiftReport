package com.hos.rushdpatients.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val WARD = "ward"
    const val ASSIGNMENTS = "assignments/{shiftId}"
    const val REPORT_PREVIEW = "report/{shiftId}"
    const val ADMIN = "admin"
    const val DOCTORS = "doctors"
    const val SETTINGS = "settings"
    const val SETTINGS_DETAIL = "settings/{workspace}"
    const val DATA_MANAGEMENT = "data_management"
    const val PROJECT_SETTINGS = "project_settings"
    const val DIAGNOSTICS = "diagnostics"
    const val VBA_IMPORT = "vba_import"
    const val ABOUT = "about"

    fun settingsWorkspace(workspace: String) = "settings/$workspace"

    fun assignments(shiftId: String) = "assignments/$shiftId"

    fun reportPreview(shiftId: String) = "report/$shiftId"
}
