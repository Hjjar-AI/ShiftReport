package com.hos.rushdpatients.ui.doctors

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import kotlinx.coroutines.flow.combine
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.DoctorMutationRepository
import com.hos.rushdpatients.data.repository.StaleDoctorEditException
import com.hos.rushdpatients.domain.export.PendingExportStore
import com.hos.rushdpatients.domain.doctor.DoctorImportPlan
import com.hos.rushdpatients.domain.doctor.changes
import com.hos.rushdpatients.data.repository.StaleDoctorImportException
import com.hos.rushdpatients.domain.doctor.DoctorEditInput
import com.hos.rushdpatients.domain.doctor.DoctorCsvCodec
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import com.hos.rushdpatients.sync.DoctorsRegistryCodec
import com.hos.rushdpatients.sync.SyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DoctorsViewModel @Inject constructor(
    private val uiText: UiStrings,
    @ApplicationContext private val context: Context,
    private val doctorRepository: DoctorRepository,
    private val settingsRepository: SettingsRepository,
    private val doctorMutations: DoctorMutationRepository,
    private val auditRepository: AuditRepository,
    private val syncService: SyncService,
    private val adminAuthorizer: AdminAuthorizer,
    private val pendingExports: PendingExportStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(DoctorsUiState(
        exporting = savedStateHandle.get<String>(PENDING_EXPORT_ID) != null
    ))
    val state: StateFlow<DoctorsUiState> = _state.asStateFlow()
    private var pendingImportPlan: DoctorImportPlan? = null
    private companion object {
        const val PENDING_EXPORT_ID = "pending_doctor_export_id"
        const val PENDING_EXPORT_COUNT = "pending_doctor_export_count"
    }

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            combine(doctorRepository.observeAll(),
                settingsRepository.observe(AppConstants.SETTING_DOCTORS_SYNC_PENDING),
                settingsRepository.observe(AppConstants.SETTING_DOCTORS_BASE_REGISTRY)) { list, _, _ -> list }
                .collect { list ->
                    val pending = doctorRepository.pendingSharedChangesSummary()
                    _state.update { it.copy(loading = false, doctors = list, pendingRegistrySummary = pending?.message) }
                }
        }
    }

    fun addDoctor(input: DoctorEditInput, onSuccess: () -> Unit = {}) {
        if (_state.value.saving || _state.value.importing) return
        _state.update { it.copy(saving = true, operationIssue = null) }
        viewModelScope.launch {
            try {
                doctorMutations.add(input)
                finishLocally(uiText(R.string.ui_doctors_view_model_001), onSuccess)
            } catch (e: CancellationException) {
                _state.update { it.copy(saving = false) }
                throw e
            } catch (e: Exception) {
                fail(e.message ?: uiText(R.string.ui_doctors_view_model_002))
            }
        }
    }

    fun editDoctor(
        expected: Doctor, input: DoctorEditInput,
        onStale: (Doctor?) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        if (_state.value.saving || _state.value.importing) return
        _state.update { it.copy(saving = true, operationIssue = null) }
        viewModelScope.launch {
            try {
                doctorMutations.edit(expected, input)
                finishLocally(uiText(R.string.ui_doctors_view_model_003), onSuccess)
            } catch (e: CancellationException) {
                _state.update { it.copy(saving = false) }
                throw e
            } catch (e: StaleDoctorEditException) {
                try {
                    val latest = doctorRepository.getById(expected.id)
                    _state.update { it.copy(saving = false) }
                    onStale(latest)
                } catch (cancelled: CancellationException) {
                    _state.update { it.copy(saving = false) }
                    throw cancelled
                } catch (reloadError: Exception) {
                    fail(reloadError.message ?: uiText(R.string.ui_doctors_view_model_004))
                }
            } catch (e: Exception) {
                fail(e.message ?: uiText(R.string.ui_doctors_view_model_005))
            }
        }
    }

    fun saveTeams(expected: List<Doctor>, assignments: Map<String, String?>, onSuccess: () -> Unit) {
        if (_state.value.saving || _state.value.importing) return
        _state.update { it.copy(saving = true, operationIssue = null) }
        viewModelScope.launch {
            try {
                doctorMutations.updateTeams(expected, assignments)
                finishLocally(uiText(R.string.ui_doctors_view_model_006), onSuccess)
            } catch (e: CancellationException) {
                _state.update { it.copy(saving = false) }; throw e
            } catch (e: Exception) { fail(e.message ?: uiText(R.string.ui_doctors_view_model_007)) }
        }
    }

    fun deleteDoctor(expected: Doctor, onSuccess: () -> Unit = {}) {
        if (_state.value.saving || _state.value.importing) return
        _state.update { it.copy(saving = true, operationIssue = null) }
        viewModelScope.launch {
            try {
                doctorMutations.delete(expected)
                finishLocally(uiText(R.string.ui_doctors_view_model_008), onSuccess)
            } catch (e: CancellationException) {
                _state.update { it.copy(saving = false) }
                throw e
            } catch (e: Exception) {
                fail(e.message ?: uiText(R.string.ui_doctors_view_model_009))
            }
        }
    }

    fun fetchDoctors() = registryOperation(false)

    fun publishDoctors(expected: List<Doctor>) = registryOperation(true, expected)

    private fun registryOperation(publish: Boolean, expected: List<Doctor>? = null) {
        if (_state.value.saving || _state.value.importing || _state.value.exporting) return
        _state.update { it.copy(saving = true, operationIssue = null) }
        viewModelScope.launch {
            try {
                adminAuthorizer.requireAdmin()
                if (publish) {
                    val reviewed = withContext(Dispatchers.IO) {
                        DoctorsRegistryCodec.encode(requireNotNull(expected).sortedBy { it.id })
                    }
                    syncService.uploadDoctors(reviewed).getOrThrow()
                } else syncService.refreshDoctorsFromRemote().getOrThrow()
                _state.update { it.copy(operationIssue = null, lastLocalOperation = null,
                    snackbar = if (publish) uiText(R.string.ui_doctors_view_model_010) else uiText(R.string.ui_doctors_view_model_011)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(operationIssue = e.message ?: uiText(R.string.ui_doctors_view_model_012)) } }
            finally { _state.update { it.copy(saving = false) } }
        }
    }

    fun dismissOperationIssue() = _state.update { it.copy(operationIssue = null) }

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun prepareDoctorExport() {
        if (_state.value.saving || _state.value.exporting || _state.value.importing) return
        _state.update { it.copy(exporting = true, snackbar = null) }
        viewModelScope.launch {
            try {
                adminAuthorizer.requireAdmin()
                val doctors = doctorRepository.getAllIncludingDeleted()
                require(doctors.isNotEmpty()) { uiText(R.string.ui_doctors_view_model_013) }
                val bytes = withContext(Dispatchers.IO) {
                    ("\uFEFF" + DoctorCsvCodec.encode(doctors)).toByteArray(Charsets.UTF_8)
                }
                try {
                    savedStateHandle[PENDING_EXPORT_ID] = pendingExports.stage(bytes)
                    savedStateHandle[PENDING_EXPORT_COUNT] = doctors.size
                } finally {
                    bytes.fill(0)
                }
                _state.update { it.copy(exportReady = true) }
            } catch (e: CancellationException) {
                clearPendingExport()
                _state.update { it.copy(exporting = false, exportReady = false) }
                throw e
            } catch (e: Exception) {
                clearPendingExport()
                _state.update { it.copy(exporting = false, exportReady = false) }
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_doctors_view_model_014)) }
            }
        }
    }

    fun doctorExportPickerLaunched() = _state.update { it.copy(exportReady = false) }

    private suspend fun clearPendingExport() = withContext(NonCancellable) {
        val id = savedStateHandle.remove<String>(PENDING_EXPORT_ID)
        savedStateHandle.remove<Int>(PENDING_EXPORT_COUNT)
        if (id != null) pendingExports.discard(id)
    }

    fun doctorExportPickerFailed() {
        viewModelScope.launch {
            clearPendingExport()
            _state.update { it.copy(exporting = false, exportReady = false,
                snackbar = uiText(R.string.ui_doctors_view_model_015)) }
        }
    }

    fun exportDoctors(uri: Uri?) {
        val id = savedStateHandle.get<String>(PENDING_EXPORT_ID)
        val count = savedStateHandle.get<Int>(PENDING_EXPORT_COUNT) ?: 0
        _state.update { it.copy(exporting = true, exportReady = false) }
        viewModelScope.launch {
            try {
                if (uri == null) return@launch
                check(id != null) { uiText(R.string.ui_doctors_view_model_016) }
                val actor = adminAuthorizer.requireAdmin()
                pendingExports.write(uri, id)
                auditRepository.record(actor.id, actor.fullName,
                    AppConstants.AUDIT_DOCTORS_EXPORTED, "export_csv:$count")
                _state.update { it.copy(snackbar = uiText(R.string.ui_doctors_view_model_017, count)) }
            } catch (e: CancellationException) {
                if (uri != null) pendingExports.discardDestination(uri)
                throw e
            } catch (e: Exception) {
                if (uri != null) pendingExports.discardDestination(uri)
                _state.update { it.copy(snackbar = e.message ?: uiText(R.string.ui_doctors_view_model_018)) }
            } finally {
                clearPendingExport()
                _state.update { it.copy(exporting = false) }
            }
        }
    }

    fun prepareDoctorImport(uri: Uri) {
        if (_state.value.saving || _state.value.exporting || _state.value.importing) {
            _state.update { it.copy(importError = uiText(R.string.ui_doctors_view_model_019)) }
            return
        }
        pendingImportPlan = null
        _state.update { it.copy(importing = true, importPreview = null, importError = null) }
        viewModelScope.launch {
            try {
                adminAuthorizer.requireAdmin()
                val decoded = withContext(Dispatchers.IO) { DoctorCsvCodec.decode(readCsv(uri)) }
                val plan = doctorMutations.prepareImport(decoded.doctors)
                val existing = plan.expectedRegistry
                val existingKeys = existing.flatMap { doctor ->
                    listOfNotNull(
                        "id:${doctor.id}",
                        doctor.telegramId?.let { "tg:$it" },
                        "name:${doctor.fullName.lowercase()}"
                    )
                }.toSet()
                val newWithoutPin = plan.reviewedChanges.count { doctor ->
                    !doctor.isDeleted && listOfNotNull(
                        "id:${doctor.id}",
                        doctor.telegramId?.let { "tg:$it" },
                        "name:${doctor.fullName.lowercase()}"
                    ).none { it in existingKeys }
                }
                val changes = plan.changes()
                val finalRegistry = existing.associateBy { it.id }.toMutableMap().apply {
                    plan.reviewedChanges.forEach { put(it.id, it) }
                }.values
                pendingImportPlan = plan
                _state.update {
                    it.copy(
                        importing = false,
                        importPreview = DoctorImportPreview(
                            activeCount = finalRegistry.count { doctor -> !doctor.isDeleted },
                            adminCount = finalRegistry.count { doctor -> !doctor.isDeleted && doctor.isAdmin },
                            deletedCount = finalRegistry.count { doctor -> doctor.isDeleted },
                            newWithoutPinCount = newWithoutPin,
                            added = changes.added, changed = changes.changed,
                            unchanged = changes.unchanged, retained = changes.retained
                        )
                    )
                }
            } catch (e: CancellationException) {
                pendingImportPlan = null
                _state.update { it.copy(importing = false, importPreview = null) }
                throw e
            } catch (e: Exception) {
                pendingImportPlan = null
                _state.update {
                    it.copy(
                        importing = false,
                        importPreview = null,
                        importError = e.message ?: uiText(R.string.ui_doctors_view_model_020)
                    )
                }
            }
        }
    }

    fun dismissImportError() {
        _state.update { it.copy(importError = null) }
    }

    fun dismissDoctorImport() {
        pendingImportPlan = null
        _state.update { it.copy(importPreview = null) }
    }

    fun confirmDoctorImport() {
        val plan = pendingImportPlan ?: return
        if (_state.value.importing || _state.value.saving) return
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            try {
                val count = doctorMutations.importRegistry(plan)
                // The local transaction committed; do not permit retrying this import after a network failure.
                pendingImportPlan = null
                _state.update { it.copy(importPreview = null) }
                val sync = uploadRegistry()
                _state.update {
                    it.copy(importing = false, snackbar = if (sync.isSuccess) {
                        uiText(R.string.ui_doctors_view_model_021, count)
                    } else {
                        uiText(R.string.ui_doctors_view_model_022) + sync.exceptionOrNull()?.message.orEmpty()
                    })
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(importing = false) }
                throw e
            } catch (e: StaleDoctorImportException) {
                pendingImportPlan = null
                _state.update { it.copy(importing = false, importPreview = null, snackbar = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(importing = false, snackbar = e.message ?: uiText(R.string.ui_doctors_view_model_023)) }
            }
        }
    }

    private fun readCsv(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error(uiText(R.string.ui_doctors_view_model_024))
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8_192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                result.append(buffer, 0, count)
                require(result.length <= 2_000_000) { uiText(R.string.ui_doctors_view_model_025) }
            }
            result.toString()
        }
    }

    private fun finishLocally(localMessage: String, onSuccess: () -> Unit) {
        _state.update { it.copy(saving = false, operationIssue = null, lastLocalOperation = localMessage,
            snackbar = uiText(R.string.ui_doctors_view_model_026, localMessage)) }
        onSuccess()
    }

    private fun fail(message: String) {
        _state.update { it.copy(saving = false, operationIssue = message, snackbar = null) }
    }

    private suspend fun uploadRegistry(): Result<Unit> {
        return syncService.uploadCurrentDoctors()
    }
}
