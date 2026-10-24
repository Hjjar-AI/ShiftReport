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
    val onOpenAnnouncement: () -> Unit,
    val onOpenDoctors: () -> Unit = {}
)
