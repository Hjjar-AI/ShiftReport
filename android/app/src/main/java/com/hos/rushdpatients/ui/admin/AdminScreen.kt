package com.hos.rushdpatients.ui.admin

import com.hos.rushdpatients.ui.components.AppSnackbarHost
import com.hos.rushdpatients.ui.components.AppActionCard
import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.components.uiNumber
import com.hos.rushdpatients.ui.components.AppTopBar
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppOutlinedButton
import com.hos.rushdpatients.ui.components.AppCard
import com.hos.rushdpatients.ui.components.AppSection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    onOpenDoctors: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiText = rememberUiStrings()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAssignAdmin by remember { mutableStateOf(false) }
    var showRemoveAdmin by remember { mutableStateOf(false) }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = uiText(R.string.ui_admin_screen_001), onBack = onBack)
        },
        snackbarHost = { AppSnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(UiSpacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
        ) {
            state.currentActor?.let { actor ->
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(UiPadding.content)) {
                        Text(uiText(R.string.ui_admin_screen_002, actor.fullName))
                        Text(uiText(R.string.ui_admin_screen_003, actor.rank))
                        Text(
                            uiText(R.string.ui_admin_screen_004),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            AppSection(title = uiText(R.string.ui_admin_screen_005)) {
                Text(uiText(R.string.ui_admin_screen_006, uiNumber(state.admins.size), uiNumber(state.nonAdmins.size)))
            }

            AppActionCard(
                title = uiText(R.string.ui_admin_screen_007),
                description = uiText(R.string.ui_admin_screen_008),
                actionLabel = uiText(R.string.ui_admin_screen_009),
                onClick = onOpenDoctors,
                primary = true
            )

            AppSection(title = uiText(R.string.ui_admin_screen_010)) {
                Text(
                    uiText(R.string.ui_admin_screen_011),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppOutlinedButton(
                    onClick = { showAssignAdmin = true },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(uiText(R.string.ui_admin_screen_012)) }
                AppOutlinedButton(
                    onClick = { showRemoveAdmin = true },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(uiText(R.string.ui_admin_screen_013)) }
            }
        }
    }

    if (showAssignAdmin) {
        AssignAdminDialog(
            candidates = state.nonAdmins,
            saving = state.saving,
            onConfirm = { doctor ->
                viewModel.assignAdmin(doctor, null) { msg ->
                    showAssignAdmin = false
                    notify(msg)
                }
            },
            onDismiss = { showAssignAdmin = false }
        )
    }

    if (showRemoveAdmin) {
        RemoveAdminDialog(
            admins = state.admins.filter { it.id != state.currentActor?.id },
            saving = state.saving,
            onConfirm = { doctor ->
                viewModel.removeAdmin(doctor) { msg ->
                    showRemoveAdmin = false
                    notify(msg)
                }
            },
            onDismiss = { showRemoveAdmin = false }
        )
    }
}
