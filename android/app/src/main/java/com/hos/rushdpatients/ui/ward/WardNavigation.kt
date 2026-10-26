package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.settings.SettingsWorkspace

/** Explicit destinations; none silently redirects to a different settings page. */
class WardNavigation(
    val onOpenReport: (String) -> Unit,
    val onOpenAdmin: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenProject: () -> Unit,
    val onOpenDataManagement: () -> Unit,
    val onOpenWorkspace: (SettingsWorkspace) -> Unit,
    val onOpenDoctors: () -> Unit = {},
    val onOpenAssignments: (String) -> Unit
)

/** One exhaustive drawer dispatch for real and demo hosts; destinations supply their own data actions. */
internal fun WardNavigation.openDrawerAction(
    action: WardDrawerAction, shiftId: String?, isAdmin: Boolean, onRecycleBin: () -> Unit
) {
    when (action) {
        WardDrawerAction.REPORT -> shiftId?.let(onOpenReport)
        WardDrawerAction.ASSIGNMENTS -> if (isAdmin) shiftId?.let(onOpenAssignments) else Unit
        WardDrawerAction.SETTINGS -> onOpenSettings()
        WardDrawerAction.FILES -> onOpenWorkspace(SettingsWorkspace.FILES)
        WardDrawerAction.BACKUP -> onOpenWorkspace(SettingsWorkspace.BACKUP)
        WardDrawerAction.INVITATIONS -> onOpenWorkspace(SettingsWorkspace.INVITATIONS)
        WardDrawerAction.DESTINATIONS -> onOpenWorkspace(SettingsWorkspace.DESTINATIONS)
        WardDrawerAction.RESET -> onOpenWorkspace(SettingsWorkspace.RESET)
        WardDrawerAction.DIAGNOSTICS -> onOpenWorkspace(SettingsWorkspace.DIAGNOSTICS)
        WardDrawerAction.DATA_MANAGEMENT -> onOpenDataManagement()
        WardDrawerAction.RECYCLE_BIN -> onRecycleBin()
        WardDrawerAction.ADMIN -> if (isAdmin) onOpenAdmin() else Unit
        WardDrawerAction.PROJECT -> onOpenProject()
        WardDrawerAction.ABOUT -> onOpenAbout()
    }
}
