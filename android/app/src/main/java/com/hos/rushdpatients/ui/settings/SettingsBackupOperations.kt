package com.hos.rushdpatients.ui.settings

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.domain.backup.EncryptedBackupManager
import com.hos.rushdpatients.domain.export.PendingExportStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Shares the screen state and ViewModel scope; does not create a second lifecycle. */
internal class SettingsBackupOperations(
    private val uiText: UiStrings,
    private val _state: MutableStateFlow<SettingsUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val encryptedBackupManager: EncryptedBackupManager,
    private val savedStateHandle: SavedStateHandle, private val pendingExports: PendingExportStore,
    private val settingsRepository: SettingsRepository, private val refreshStorageHealth: suspend () -> Unit
) {

    fun prepareEncryptedBackup(passphrase: String) {
        if (_state.value.backupBusy) return
        _state.update { it.copy(backupBusy = true, backupReady = false, snackbar = null) }
        val password = passphrase.toCharArray()
        viewModelScope.launch {
            try {
                val prepared = encryptedBackupManager.prepareExport(password)
                try {
                    savedStateHandle[PENDING_BACKUP_ID] = pendingExports.stage(prepared.bytes)
                    savedStateHandle[PENDING_BACKUP_COUNT] = prepared.patientCount
                } finally {
                    prepared.bytes.fill(0)
                }
                _state.update { it.copy(backupReady = true) }
            } catch (e: CancellationException) {
                clearPendingBackup()
                _state.update { it.copy(backupBusy = false, backupReady = false) }
                throw e
            } catch (e: Exception) {
                clearPendingBackup()
                _state.update { it.copy(backupBusy = false, backupReady = false,
                    snackbar = e.message ?: uiText(R.string.ui_settings_backup_operations_001)) }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun backupPickerLaunched() = _state.update { it.copy(backupReady = false) }

    fun backupPickerFailed() {
        viewModelScope.launch {
            clearPendingBackup()
            _state.update { it.copy(backupBusy = false, backupReady = false,
                snackbar = uiText(R.string.ui_settings_backup_operations_002)) }
        }
    }

    fun exportEncryptedBackup(uri: Uri?) {
        val id = savedStateHandle.get<String>(PENDING_BACKUP_ID)
        val count = savedStateHandle.get<Int>(PENDING_BACKUP_COUNT) ?: 0
        _state.update { it.copy(backupBusy = true, backupReady = false, snackbar = null) }
        viewModelScope.launch {
            try {
                if (uri == null) return@launch
                check(id != null) { uiText(R.string.ui_settings_backup_operations_003) }
                pendingExports.write(uri, id)
                settingsRepository.putLong(AppConstants.SETTING_LAST_ENCRYPTED_BACKUP_AT,
                    Instant.now().toEpochMilli())
                _state.update { it.copy(snackbar = uiText(R.string.ui_settings_backup_operations_004, count)) }
                refreshStorageHealth()
            } catch (e: CancellationException) {
                if (uri != null) pendingExports.discardDestination(uri)
                throw e
            } catch (e: Exception) {
                if (uri != null) pendingExports.discardDestination(uri)
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_settings_backup_operations_005)) }
            } finally {
                clearPendingBackup()
                _state.update { it.copy(backupBusy = false) }
            }
        }
    }

    private suspend fun clearPendingBackup() = withContext(NonCancellable) {
        val id = savedStateHandle.remove<String>(PENDING_BACKUP_ID)
        savedStateHandle.remove<Int>(PENDING_BACKUP_COUNT)
        if (id != null) pendingExports.discard(id)
    }

    fun restoreEncryptedBackup(uri: Uri, password: String) {
        if (_state.value.backupBusy) return
        _state.update { it.copy(backupBusy = true, snackbar = null) }
        viewModelScope.launch {
            runCatching { encryptedBackupManager.restore(uri, password.toCharArray()) }
                .onSuccess { count ->
                    _state.update {
                        it.copy(backupBusy = false, snackbar = uiText(R.string.ui_settings_backup_operations_006, count))
                    }
                    refreshStorageHealth()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(backupBusy = false, snackbar = error.message ?: uiText(R.string.ui_settings_backup_operations_007))
                    }
                }
        }
    }
}
