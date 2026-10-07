package com.hos.rushdpatients.ui.doctors

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.DoctorMutationRepository
import com.hos.rushdpatients.data.repository.StaleDoctorEditException
import com.hos.rushdpatients.domain.doctor.DoctorImportPlan
import com.hos.rushdpatients.data.repository.StaleDoctorImportException
import com.hos.rushdpatients.domain.doctor.DoctorEditInput
import com.hos.rushdpatients.domain.doctor.DoctorCsvCodec
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.doctor.DoctorMergeChoice
import kotlinx.coroutines.CancellationException
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
    @ApplicationContext private val context: Context,
    private val doctorRepository: DoctorRepository,
    private val doctorMutations: DoctorMutationRepository,
    private val auditRepository: AuditRepository,
    private val syncService: SyncService,
    private val adminAuthorizer: AdminAuthorizer
) : ViewModel() {

    private val _state = MutableStateFlow(DoctorsUiState())
    val state: StateFlow<DoctorsUiState> = _state.asStateFlow()
    private var pendingImportPlan: DoctorImportPlan? = null
    // Prepared before opening the system picker; never put registry bytes in saved UI state.
    private var pendingExport: ByteArray? = null
    private var pendingExportCount = 0

    init {
        observe()
        viewModelScope.launch {
            syncService.doctorConflicts.collect { conflicts ->
                _state.update { it.copy(mergeConflicts = conflicts) }
            }
        }
    }

    fun resolveRegistryConflicts(choices: Map<String, DoctorMergeChoice>) {
        if (_state.value.saving || _state.value.importing) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                syncService.resolveDoctorConflicts(choices).fold(
                    onSuccess = { _state.update { it.copy(saving = false, snackbar = "تم دمج سجل الأطباء ومزامنته") } },
                    onFailure = { error -> fail(error.message ?: "تعذر تطبيق دمج سجل الأطباء") }
                )
            } catch (e: CancellationException) {
                _state.update { it.copy(saving = false) }
                throw e
            }
        }
    }

    private fun observe() {
        viewModelScope.launch {
            doctorRepository.observeAll().collect { list ->
                _state.update { it.copy(loading = false, doctors = list) }
            }
        }
    }

    fun addDoctor(input: DoctorEditInput, onSuccess: () -> Unit = {}) {
        if (_state.value.saving || _state.value.importing) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                doctorMutations.add(input)
                finishWithSync("تم إضافة الطبيب", onSuccess)
            } catch (e: CancellationException) {
                _state.update { it.copy(saving = false) }
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "تعذر إضافة الطبيب")
            }
        }
    }

    fun editDoctor(
        expected: Doctor, input: DoctorEditInput,
        onStale: (Doctor?) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        if (_state.value.saving || _state.value.importing) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                doctorMutations.edit(expected, input)
                finishWithSync("تم تحديث الطبيب", onSuccess)
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
                    fail(reloadError.message ?: "تعذر تحميل أحدث سجل؛ المسودة باقية")
                }
            } catch (e: Exception) {
                fail(e.message ?: "تعذر تحديث الطبيب")
            }
        }
    }

    fun deleteDoctor(expected: Doctor, onSuccess: () -> Unit = {}) {
        if (_state.value.saving || _state.value.importing) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                doctorMutations.delete(expected)
                finishWithSync("تم حذف الطبيب", onSuccess)
            } catch (e: CancellationException) {
                _state.update { it.copy(saving = false) }
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "تعذر حذف الطبيب")
            }
        }
    }

    fun refresh() {
        if (_state.value.saving || _state.value.importing) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = runCatching {
                adminAuthorizer.requireAdmin()
                uploadRegistry().getOrThrow()
            }
            _state.update {
                it.copy(
                    saving = false,
                    snackbar = result.fold(
                        onSuccess = { "تمت مزامنة سجل الأطباء" },
                        onFailure = { error -> error.message ?: "فشلت مزامنة سجل الأطباء" }
                    )
                )
            }
        }
    }

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun prepareDoctorExport() {
        if (_state.value.saving || _state.value.exporting || _state.value.importing) return
        _state.update { it.copy(exporting = true, snackbar = null) }
        viewModelScope.launch {
            try {
                adminAuthorizer.requireAdmin()
                val doctors = doctorRepository.getAllIncludingDeleted()
                require(doctors.isNotEmpty()) { "لا يوجد أطباء للتصدير" }
                pendingExport = withContext(Dispatchers.IO) {
                    ("\uFEFF" + DoctorCsvCodec.encode(doctors)).toByteArray(Charsets.UTF_8)
                }
                pendingExportCount = doctors.size
                _state.update { it.copy(exportReady = true) }
            } catch (e: CancellationException) {
                cancelDoctorExport()
                throw e
            } catch (e: Exception) {
                cancelDoctorExport()
                _state.update { it.copy(snackbar = e.message ?: "تعذر تجهيز سجل الأطباء") }
            }
        }
    }

    fun doctorExportPickerLaunched() = _state.update { it.copy(exportReady = false) }

    fun cancelDoctorExport() {
        pendingExport?.fill(0)
        pendingExport = null
        pendingExportCount = 0
        _state.update { it.copy(exporting = false, exportReady = false) }
    }

    fun doctorExportPickerFailed() {
        cancelDoctorExport()
        _state.update { it.copy(snackbar = "تعذر فتح مكان الحفظ؛ أعد تصدير سجل الأطباء") }
    }

    fun exportDoctors(uri: Uri?) {
        if (uri == null) {
            cancelDoctorExport()
            return
        }
        val bytes = pendingExport
        val count = pendingExportCount
        pendingExport = null
        pendingExportCount = 0
        if (bytes == null) {
            _state.update { it.copy(exporting = false, exportReady = false,
                snackbar = "انتهت جلسة التصدير؛ احذف الملف الفارغ وأعد تصدير سجل الأطباء") }
            return
        }
        _state.update { it.copy(exporting = true, exportReady = false) }
        viewModelScope.launch {
            try {
                val actor = adminAuthorizer.requireAdmin()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(bytes)
                        stream.flush()
                    } ?: error("تعذر فتح ملف التصدير")
                    val saved = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("تعذر التحقق من ملف الأطباء")
                    try {
                        check(saved.contentEquals(bytes)) { "لم يُحفظ ملف الأطباء كاملاً؛ أعد التصدير" }
                    } finally {
                        saved.fill(0)
                    }
                }
                auditRepository.record(actor.id, actor.fullName,
                    AppConstants.AUDIT_DOCTORS_EXPORTED, "export_csv:$count")
                _state.update { it.copy(snackbar = "تم تصدير $count سجل طبيب والتحقق من الملف") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(snackbar = e.message ?: "تعذر تصدير سجل الأطباء") }
            } finally {
                bytes.fill(0)
                _state.update { it.copy(exporting = false) }
            }
        }
    }

    override fun onCleared() {
        pendingExport?.fill(0)
        super.onCleared()
    }

    fun prepareDoctorImport(uri: Uri) {
        if (_state.value.saving || _state.value.exporting || _state.value.importing) return
        pendingImportPlan = null
        _state.update { it.copy(importing = true, importPreview = null) }
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
                pendingImportPlan = plan
                _state.update {
                    it.copy(
                        importing = false,
                        importPreview = DoctorImportPreview(
                            activeCount = plan.reviewedChanges.count { doctor -> !doctor.isDeleted },
                            adminCount = plan.reviewedChanges.count { doctor -> !doctor.isDeleted && doctor.isAdmin },
                            deletedCount = plan.reviewedChanges.count { doctor -> doctor.isDeleted },
                            newWithoutPinCount = newWithoutPin
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
                        snackbar = e.message ?: "تعذر قراءة ملف الأطباء"
                    )
                }
            }
        }
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
                        "تم استيراد $count سجل ومزامنة سجل الأطباء"
                    } else {
                        "تم الاستيراد محلياً، وتعذرت مزامنة تليجرام: " + sync.exceptionOrNull()?.message.orEmpty()
                    })
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(importing = false) }
                throw e
            } catch (e: StaleDoctorImportException) {
                pendingImportPlan = null
                _state.update { it.copy(importing = false, importPreview = null, snackbar = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(importing = false, snackbar = e.message ?: "تعذر استيراد سجل الأطباء") }
            }
        }
    }

    private fun readCsv(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("تعذر فتح ملف الاستيراد")
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8_192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                result.append(buffer, 0, count)
                require(result.length <= 2_000_000) { "ملف الأطباء أكبر من الحد المسموح" }
            }
            result.toString()
        }
    }

    private suspend fun finishWithSync(localMessage: String, onSuccess: () -> Unit) {
        val syncResult = uploadRegistry()
        _state.update {
            it.copy(
                saving = false,
                snackbar = syncResult.fold(
                    onSuccess = { localMessage },
                    onFailure = { error -> "$localMessage محلياً، وتعذرت مزامنة تليجرام: ${error.message.orEmpty()}" }
                )
            )
        }
        onSuccess()
    }

    private fun fail(message: String) {
        _state.update { it.copy(saving = false, snackbar = message) }
    }

    private suspend fun uploadRegistry(): Result<Unit> {
        return syncService.uploadCurrentDoctors()
    }
}
