package com.hos.rushdpatients.ui.demo

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.*
import com.hos.rushdpatients.ui.ward.*

internal enum class DemoDestination(@StringRes private val titleRes: Int) {
    PATIENTS(R.string.ui_patients), DASHBOARD(R.string.ui_ward_drawer_010),
    ASSIGNMENTS(R.string.assignments_export_title), ABOUT(R.string.ui_ward_drawer_028),
    TEAM(R.string.ui_doctors_screen_001), REPORT(R.string.ui_demo_navigation_009), SETTINGS(R.string.ui_settings);
    fun title(uiText: UiStrings): String = uiText(titleRes)
}

/** Production drawer with a small, explicitly demo-only session-control slot. */
@Composable
internal fun DemoDrawer(
    state: DemoWardState, ward: WardUiState, busy: Boolean,
    onAction: (WardDrawerAction) -> Unit,
    onUndoFetch: (() -> Unit)?, onActor: () -> Unit, onReset: () -> Unit, onExit: () -> Unit
) {
    val uiText = rememberUiStrings()
    WardDrawer(ward, state.actor.fullName, isAdmin = true, isPermanentAdmin = false,
        online = false, onAction = onAction, additionalActions = {
            AppNotice(uiText(R.string.demo_simulation_notice))
            if (onUndoFetch != null) AppTextButton(onClick = onUndoFetch, enabled = !busy) {
                Text(uiText(R.string.demo_undo_fetch))
            }
            AppTextButton(onClick = onActor, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(R.string.ui_demo_secondary_screens_011))
            }
            AppTextButton(onClick = onReset, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(R.string.ui_demo_navigation_005))
            }
            AppOutlinedButton(onClick = onExit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(R.string.ui_demo_navigation_006))
            }
        })
}
