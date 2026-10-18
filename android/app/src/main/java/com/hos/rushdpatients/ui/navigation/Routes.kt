package com.hos.rushdpatients.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val WARD = "ward"
    const val REPORT_PREVIEW = "report/{shiftId}"
    const val ADMIN = "admin"
    const val DOCTORS = "doctors"
    const val SETTINGS = "settings"
    const val ANNOUNCEMENT = "announcement"
    const val VBA_IMPORT = "vba_import"
    const val ABOUT = "about"

    fun reportPreview(shiftId: String) = "report/$shiftId"
}
