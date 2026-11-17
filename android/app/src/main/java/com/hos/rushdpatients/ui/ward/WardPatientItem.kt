package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.R
import com.hos.rushdpatients.ui.components.rememberUiStrings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.hos.rushdpatients.data.model.Patient
import kotlinx.coroutines.launch

/** Card actions retain ward ownership and the existing Undo and repository guards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WardPatientItem(patient: Patient, cardModifier: Modifier, state: WardUiState,
    viewModel: WardController, workspace: WardWorkspaceState, allCardsExpanded: Boolean,
    expandedIds: Set<String>, collapsedIds: Set<String>, doctorNames: Map<String, String>,
    snackbarHost: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val uiText = rememberUiStrings()
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
        actions = remember(patient, allCardsExpanded, state.isReadOnly, state.saving, state.syncing, viewModel, editor, haptics, scope) {
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
