package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.hos.rushdpatients.config.ProjectConfigStore
import com.hos.rushdpatients.config.ProjectProvisioningManager
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.DoctorMutationRepository
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.sync.EmptyStartCoordinator
import com.hos.rushdpatients.sync.EmptyStartReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Shares the screen state and ViewModel scope; does not create a second lifecycle. */
internal class SettingsProjectOperations(
    private val uiText: UiStrings,
    private val _state: MutableStateFlow<SettingsUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val emptyStart: EmptyStartCoordinator, private val sessionManager: SessionManager,
    private val doctorMutations: DoctorMutationRepository, private val provisioningManager: ProjectProvisioningManager,
    private val projectConfigStore: ProjectConfigStore, private val adminAuthorizer: AdminAuthorizer,
    private val savedStateHandle: SavedStateHandle, private val load: () -> Unit,
    private val invalidatePublicationReview: () -> Unit
) {
    private var emptyStartToken: EmptyStartReview? = null

    fun prepareEmptyStart() {
        val current = _state.value
        if (current.emptyStartBusy || current.syncing || current.backupBusy || current.provisioningBusy || current.exportingCsv) return
        _state.update { it.copy(emptyStartBusy = true, emptyStartResult = null) }
        viewModelScope.launch {
            try {
                emptyStartToken = emptyStart.prepare()
                _state.update { it.copy(emptyStartReview = emptyStartToken?.text) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(emptyStartResult = e.message ?: uiText(R.string.ui_settings_project_operations_001)) } }
            finally { _state.update { it.copy(emptyStartBusy = false) } }
        }
    }

    fun dismissEmptyStart() {
        if (_state.value.emptyStartBusy) return
        emptyStartToken = null
        _state.update { it.copy(emptyStartReview = null, emptyStartResult = null) }
    }

    fun confirmEmptyStart() {
        val review = emptyStartToken ?: return
        if (_state.value.emptyStartBusy) return
        _state.update { it.copy(emptyStartBusy = true, emptyStartReview = null) }
        emptyStartToken = null
        invalidatePublicationReview()
        viewModelScope.launch {
            try {
                emptyStart.confirm(review)
                load()
                _state.update { it.copy(emptyStartResult = uiText(R.string.ui_settings_project_operations_002)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(emptyStartResult = e.message ?: uiText(R.string.ui_settings_project_operations_003)) } }
            finally { _state.update { it.copy(emptyStartBusy = false) } }
        }
    }

    fun setSupervisorGroupChatId(expected: Doctor, rawValue: String) {
        if (_state.value.savingSupervisorGroupId != null) return
        val session = sessionManager.current()
        if (session?.isAdmin != true) {
            _state.update { it.copy(snackbar = uiText(R.string.ui_settings_project_operations_004)) }
            return
        }
        val trimmed = rawValue.trim()
        val chatId = if (trimmed.isEmpty()) null else trimmed.toLongOrNull()
        if (trimmed.isNotEmpty() && (chatId == null || chatId >= 0L)) {
            _state.update {
                it.copy(snackbar = uiText(R.string.ui_settings_project_operations_005))
            }
            return
        }
        _state.update { it.copy(savingSupervisorGroupId = expected.id, snackbar = null) }
        viewModelScope.launch {
            try {
                doctorMutations.setSupervisorGroup(expected, chatId)
                _state.update { it.copy(savingSupervisorGroupId = null,
                    snackbar = uiText(R.string.ui_settings_project_operations_006)) }
            } catch (e: CancellationException) {
                _state.update { it.copy(savingSupervisorGroupId = null) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        savingSupervisorGroupId = null,
                        snackbar = e.message ?: uiText(R.string.ui_settings_project_operations_007)
                    )
                }
            }
        }
    }

    fun prepareProjectProvisioning(passphrase: String, share: Boolean = false, saveToDownloads: Boolean = false) {
        if (_state.value.provisioningBusy) return
        _state.update { it.copy(provisioningBusy = true, provisioningReady = false, snackbar = null) }
        val password = passphrase.toCharArray()
        viewModelScope.launch {
            try {
                adminAuthorizer.requireAdmin()
                val encrypted = provisioningManager.prepareExport(projectConfigStore.current(), password)
                try {
                    savedStateHandle[PENDING_PROVISIONING_ID] = provisioningManager.stageExport(encrypted)
                } finally {
                    encrypted.fill(0)
                }
                if (saveToDownloads) {
                    adminAuthorizer.requireAdmin()
                    val id = checkNotNull(savedStateHandle.get<String>(PENDING_PROVISIONING_ID))
                    val saved = provisioningManager.saveStagedToDownloads(id)
                    clearPendingProvisioning()
                    _state.update { it.copy(provisioningBusy = false,
                        snackbar = uiText(R.string.ui_settings_project_operations_008, com.hos.rushdpatients.ui.components.uiNumber(saved.byteCount))) }
                } else if (share) {
                    adminAuthorizer.requireAdmin()
                    val id = checkNotNull(savedStateHandle.get<String>(PENDING_PROVISIONING_ID))
                    val uri = provisioningManager.shareStagedExport(id)
                    clearPendingProvisioning()
                    _state.update { it.copy(provisioningBusy = false, provisioningShareUri = uri) }
                } else {
                    _state.update { it.copy(provisioningReady = true) }
                }
            } catch (e: CancellationException) {
                clearPendingProvisioning()
                _state.update { it.copy(provisioningBusy = false, provisioningReady = false) }
                throw e
            } catch (e: Exception) {
                clearPendingProvisioning()
                _state.update {
                    it.copy(provisioningBusy = false, provisioningReady = false,
                        snackbar = e.message ?: uiText(R.string.ui_settings_project_operations_009))
                }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun provisioningPickerLaunched() {
        _state.update { it.copy(provisioningReady = false) }
    }

    fun provisioningShareHandled(failed: Boolean) {
        _state.update {
            it.copy(provisioningShareUri = null,
                snackbar = if (failed) uiText(R.string.ui_settings_project_operations_010)
                else uiText(R.string.ui_settings_project_operations_011))
        }
    }

    fun provisioningPickerFailed() {
        viewModelScope.launch {
            clearPendingProvisioning()
            _state.update {
                it.copy(provisioningBusy = false, provisioningReady = false,
                    snackbar = uiText(R.string.ui_settings_project_operations_012))
            }
        }
    }

    fun exportProjectProvisioning(uri: Uri?) {
        val exportId = savedStateHandle.get<String>(PENDING_PROVISIONING_ID)
        _state.update { it.copy(provisioningReady = false, provisioningBusy = true, snackbar = null) }
        viewModelScope.launch {
            try {
                if (uri == null) return@launch
                check(exportId != null) { uiText(R.string.ui_settings_project_operations_013) }
                adminAuthorizer.requireAdmin()
                provisioningManager.writeStagedExport(uri, exportId)
                _state.update {
                    it.copy(snackbar = uiText(R.string.ui_settings_project_operations_014))
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    if (uri != null) provisioningManager.discardFailedDestination(uri)
                }
                throw e
            } catch (e: Exception) {
                if (uri != null) provisioningManager.discardFailedDestination(uri)
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_settings_project_operations_015)) }
            } finally {
                clearPendingProvisioning()
                _state.update { it.copy(provisioningBusy = false) }
            }
        }
    }

    private suspend fun clearPendingProvisioning() = withContext(NonCancellable) {
        val id = savedStateHandle.remove<String>(PENDING_PROVISIONING_ID)
        if (id != null) provisioningManager.discardStagedExport(id)
    }
}
