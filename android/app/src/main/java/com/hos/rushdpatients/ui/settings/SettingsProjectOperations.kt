package com.hos.rushdpatients.ui.settings

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
            catch (e: Exception) { _state.update { it.copy(emptyStartResult = e.message ?: "تعذر إعداد البداية الفارغة") } }
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
                _state.update { it.copy(emptyStartResult = "تم نشر البداية الفارغة ومسح المرضى والمناوبات على هذا الجهاز. بقي الأطباء والاتصال وسجل تليجرام. على الأجهزة الأخرى جلب البيانات قبل العمل.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(emptyStartResult = e.message ?: "تعذر إكمال البداية الفارغة؛ افحص النسخة الحالية قبل الإعادة") } }
            finally { _state.update { it.copy(emptyStartBusy = false) } }
        }
    }

    fun setSupervisorGroupChatId(expected: Doctor, rawValue: String) {
        if (_state.value.savingSupervisorGroupId != null) return
        val session = sessionManager.current()
        if (session?.isAdmin != true) {
            _state.update { it.copy(snackbar = "هذه الإعدادات متاحة للمدير فقط") }
            return
        }
        val trimmed = rawValue.trim()
        val chatId = if (trimmed.isEmpty()) null else trimmed.toLongOrNull()
        if (trimmed.isNotEmpty() && (chatId == null || chatId >= 0L)) {
            _state.update {
                it.copy(snackbar = "معرف مجموعة تليجرام يجب أن يكون رقماً سالباً")
            }
            return
        }
        _state.update { it.copy(savingSupervisorGroupId = expected.id, snackbar = null) }
        viewModelScope.launch {
            try {
                doctorMutations.setSupervisorGroup(expected, chatId)
                _state.update { it.copy(savingSupervisorGroupId = null,
                    snackbar = "حُفظت وجهة المشرف محلياً؛ افتح سجل الأطباء واختر «نشر سجل الأطباء» لمشاركتها") }
            } catch (e: CancellationException) {
                _state.update { it.copy(savingSupervisorGroupId = null) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        savingSupervisorGroupId = null,
                        snackbar = e.message ?: "تعذر حفظ معرف المجموعة"
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
                        snackbar = "تم حفظ ملف الانضمام والتحقق من ${com.hos.rushdpatients.ui.components.uiNumber(saved.byteCount)} بايت في التنزيلات/ShiftReport. شارك عبارة المرور عبر قناة منفصلة.") }
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
                        snackbar = e.message ?: "فشل تجهيز ملف الانضمام")
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
                snackbar = if (failed) "تعذر فتح المشاركة؛ جرّب حفظ الملف من نافذة إنشاء ملف الانضمام"
                else "ملف الانضمام المشفر جاهز. شارك عبارة المرور عبر قناة منفصلة.")
        }
    }

    fun provisioningPickerFailed() {
        viewModelScope.launch {
            clearPendingProvisioning()
            _state.update {
                it.copy(provisioningBusy = false, provisioningReady = false,
                    snackbar = "تعذر فتح مكان الحفظ؛ أعد إنشاء ملف الانضمام")
            }
        }
    }

    fun exportProjectProvisioning(uri: Uri?) {
        val exportId = savedStateHandle.get<String>(PENDING_PROVISIONING_ID)
        _state.update { it.copy(provisioningReady = false, provisioningBusy = true, snackbar = null) }
        viewModelScope.launch {
            try {
                if (uri == null) return@launch
                check(exportId != null) { "انتهت جلسة التصدير؛ أعد إنشاء ملف الانضمام" }
                adminAuthorizer.requireAdmin()
                provisioningManager.writeStagedExport(uri, exportId)
                _state.update {
                    it.copy(snackbar = "تم حفظ ملف الانضمام المشفر والتحقق منه. شارك عبارة المرور عبر قناة منفصلة.")
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    if (uri != null) provisioningManager.discardFailedDestination(uri)
                }
                throw e
            } catch (e: Exception) {
                if (uri != null) provisioningManager.discardFailedDestination(uri)
                _state.update { it.copy(snackbar = e.message ?: "فشل حفظ ملف الانضمام") }
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
