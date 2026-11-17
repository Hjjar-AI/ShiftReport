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

/** Editor orchestration retains the existing editor/forms and captures the successor before save. */
@Composable
internal fun WardEditorDialogs(state: WardUiState, viewModel: WardController,
    workspace: WardWorkspaceState, roundsPatients: List<Patient>, snackbarHost: SnackbarHostState) {
    val editor = viewModel.editor
    val uiText = rememberUiStrings()
    val scope = rememberCoroutineScope()
    var returnTarget by workspace::returnTarget
    if (editor.showAdd && !state.patientDraftLoaded) {
        PatientDraftLoadDialog(state.patientDraftLoadFailed,
            onRetry = viewModel::retryLoadDraft, onDismiss = { editor.showAdd = false })
    }
    if (editor.showAdd && state.patientDraftLoaded) {
        val insertionPatients = editor.insertionPatients
        AddPatientDialog(
            doctors = state.doctors,
            draft = state.patientDraft,
            stateStore = editor.addForm,
            draftWarning = state.draftSaveIssue?.takeIf { it.patientId == null }?.message,
            saving = state.saving,
            position = editor.insertionPosition,
            positionLimit = insertionPatients.size + 1,
            onPositionChange = { value -> editor.insertionPosition = value.filter { it.isDigit() }.take(3) },
            onDraftChanged = viewModel::saveDraft,
            onConfirm = {
                val requested = editor.insertionPosition.map { digit -> Character.digit(digit, 10) }.joinToString("").toIntOrNull()
                if (editor.insertionPosition.isBlank() || requested != null) {
                    viewModel.addPatient(it, requested) { editor.showAdd = false }
                }
            },
            onDismiss = { editor.showAdd = false }
        )
    }


    fun saveEdited(p: Patient, draft: Patient, onUnavailable: (Patient?) -> Unit, advance: Boolean) {
        // Capture the displayed successor before saving can change filters/order.
        val nextId = if (advance) patientRoundsNavigation(roundsPatients.map { it.id }, p.id)?.nextId else null
        viewModel.updatePatient(draft, onUnavailable) {
            val current = viewModel.state.value
            val next = nextId?.takeIf { !current.isReadOnly && current.shift?.id == state.shift?.id }
                ?.let { id -> current.patients.firstOrNull { it.id == id && !it.isDeleted } }
            if (next != null) {
                returnTarget = null
                editor.editTarget = next
            } else {
                returnTarget = PatientReturnTarget(draft.id, draft.revision)
                editor.editTarget = null
            }
        }
    }
    WardPatientDialogs(state, editor.editTarget, editor.copyTarget, editor.deleteTarget, roundsPatients.map { it.id },
        onLoadEditDraft = viewModel::loadEditDraft, onEditDraftChanged = viewModel::saveEditDraft,
        onDiscardEditDraft = viewModel::discardEditDraft,
        editSession = editor.editSession, copyInitial = editor.copyInitial, copyForm = editor.copyForm,
        onSaveEdited = ::saveEdited,
        onDismissEdit = { editor.editTarget = null },
        onCopyConfirmed = { patient -> viewModel.addPatient(patient) { editor.copyTarget = null } },
        onDismissCopy = { editor.copyTarget = null }, onDeleteConfirmed = { p ->
            viewModel.deletePatient(p) { deletedPatient ->
                editor.deleteTarget = null
                scope.launch {
                    val result = snackbarHost.showSnackbar(
                        message = uiText(R.string.ui_ward_screen_014, p.name),
                        actionLabel = uiText(R.string.ui_ward_screen_015)
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.restorePatient(deletedPatient)
                    }
                }
            }
        }, onDismissDelete = { editor.deleteTarget = null })

}
