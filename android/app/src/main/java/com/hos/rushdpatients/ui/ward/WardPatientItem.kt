package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.theme.UiOpacity
import com.hos.rushdpatients.ui.components.AppTextButton
import com.hos.rushdpatients.ui.components.appContentEntrance

import com.hos.rushdpatients.ui.components.AppSnackbarHost
import com.hos.rushdpatients.ui.components.AppSheet
import com.hos.rushdpatients.ui.components.AppIcons
import com.hos.rushdpatients.ui.components.uiLabel

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import com.hos.rushdpatients.ui.settings.SettingsWorkspace
import com.hos.rushdpatients.ui.theme.UiDialog
import com.hos.rushdpatients.ui.components.appDialogBody
import com.hos.rushdpatients.ui.theme.UiLayout
import androidx.compose.foundation.rememberScrollState
import com.hos.rushdpatients.ui.components.rememberFloatingActionSpace
import com.hos.rushdpatients.ui.components.NoticeKind
import com.hos.rushdpatients.ui.components.AppNotice
import com.hos.rushdpatients.ui.theme.UiPadding
import com.hos.rushdpatients.ui.theme.UiSize
import com.hos.rushdpatients.ui.theme.UiSpacing
import com.hos.rushdpatients.ui.components.AppButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupByMode
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch

/** Card actions retain ward ownership and the existing Undo and repository guards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WardPatientItem(patient: Patient, cardModifier: Modifier, state: WardUiState,
    viewModel: WardController, workspace: WardWorkspaceState, allCardsExpanded: Boolean,
    expandedIds: Set<String>, collapsedIds: Set<String>, doctorNames: Map<String, String>,
    snackbarHost: SnackbarHostState) {
    val uiText = rememberUiStrings()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val editor = viewModel.editor
    val animatedPatientId = workspace.animatedPatientId
    val recentlyEditedId = workspace.recentlyEditedId
    PatientCard(
        modifier = cardModifier,
        state = PatientCardState(patient = patient,
            expanded = if (allCardsExpanded) patient.id !in collapsedIds else patient.id in expandedIds,
            animateExpansion = animatedPatientId == patient.id,
            doctorNames = doctorNames,
            readOnly = state.isReadOnly || state.saving || state.syncing,
            recentlyEdited = recentlyEditedId == patient.id,
            pinned = false,
            compact = state.compactCards),
        actions = remember(patient, allCardsExpanded, state.isReadOnly, state.saving, state.syncing, viewModel, editor, haptics) {
            PatientCardActions(
                onOpen = {
                    if (state.canInteract && editor.dialog == null) {
                        if (state.isReadOnly) workspace.toggleCard(patient.id, allCardsExpanded)
                        else editor.editTarget = patient
                    }
                },
                onLongPress = {
                    if (state.canStartEdit(editor)) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        editor.editTarget = patient
                    }
                },
                onEdit = {
                    if (state.canStartEdit(editor)) {
                        editor.editTarget = patient
                    }
                },
                onDelete = { editor.deleteTarget = patient },
                onCopy = { editor.copyTarget = patient },
                onPriorityChange = { priority ->
                    viewModel.setPriority(patient, priority) {
                        scope.launch {
                            snackbarHost.currentSnackbarData?.dismiss()
                            if (snackbarHost.showSnackbar(uiText(R.string.priority_changed),
                                    actionLabel = uiText(R.string.ui_task_undo),
                                    withDismissAction = true,
                                    duration = androidx.compose.material3.SnackbarDuration.Short) == androidx.compose.material3.SnackbarResult.ActionPerformed)
                                viewModel.setPriority(patient, patient.isPriority)
                        }
                    }
                },
                onPinToggle = {
                    if (state.canStartEdit(editor)) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.movePatientToTop(patient)
                    }
                },
                onExpandToggle = { workspace.toggleCard(patient.id, allCardsExpanded) }
            )
        }
    )

}
