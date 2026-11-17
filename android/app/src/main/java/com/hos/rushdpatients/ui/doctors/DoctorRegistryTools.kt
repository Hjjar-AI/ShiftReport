package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.ui.components.readingPaneWidth
import com.hos.rushdpatients.ui.components.AppText

import com.hos.rushdpatients.ui.components.AppSnackbarHost
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.AppIconButton
import androidx.annotation.StringRes

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.rememberFloatingActionSpace
import androidx.compose.foundation.layout.PaddingValues
import com.hos.rushdpatients.domain.patient.ArabicSearchNormalizer
import com.hos.rushdpatients.ui.components.AppLoadingState
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppCard
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.hos.rushdpatients.ui.theme.UiSpacing
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.doctor.DoctorCsvCodec
import com.hos.rushdpatients.ui.components.ConfirmDialog
import com.hos.rushdpatients.ui.components.EmptyState

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
