package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.ui.components.AppText

import com.hos.rushdpatients.ui.components.AppIcons

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hos.rushdpatients.ui.theme.UiSpacing

/** Tool presentation only; the screen retains the captured registry and review decisions. */
@Composable
internal fun DoctorRegistryTools(busy: Boolean, fileTransfers: Boolean, transferExpanded: Boolean,
    onReviewPublication: () -> Unit, onImport: () -> Unit, onFetch: () -> Unit,
    onToggleTransfer: () -> Unit, onTeams: () -> Unit, onExport: () -> Unit) {
    val uiText = rememberUiStrings()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(UiPadding.content),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
        ) {
            AppText(uiText(R.string.ui_doctor_registry_tools), style = MaterialTheme.typography.titleMedium)
            AppText(uiText(R.string.ui_doctors_screen_014),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppOutlinedButton(
                onClick = { onReviewPublication() },
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) { AppText(uiText(R.string.ui_doctors_screen_006)) }
            AppOutlinedButton(
                onClick = { onImport() },
                enabled = !busy && fileTransfers, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(AppIcons.FolderOpen, contentDescription = null)
                AppText(uiText(R.string.ui_doctors_screen_015), Modifier.padding(start = UiSpacing.small))
            }
            AppOutlinedButton(onClick = { onFetch() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy) {
                AppText(uiText(R.string.ui_doctors_screen_005))
            }
            com.hos.rushdpatients.ui.components.AppSectionHeader(
                title = uiText(R.string.ui_doctors_secondary_tools), expanded = transferExpanded,
                onToggle = { onToggleTransfer() })
            if (transferExpanded) {
                AppOutlinedButton(onClick = { onTeams() }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) {
                    AppText(uiText(R.string.ui_team_management_dialog_002))
                }
                AppOutlinedButton(onClick = onExport,
                    enabled = !busy && fileTransfers, modifier = Modifier.fillMaxWidth()) {
                    AppText(uiText(R.string.ui_doctors_screen_016))
                }
            }
        }
    }
}
