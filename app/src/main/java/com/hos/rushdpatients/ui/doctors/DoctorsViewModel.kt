package com.hos.rushdpatients.ui.doctors

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import com.hos.rushdpatients.data.repository.AuditRepository
import com.hos.rushdpatients.data.repository.DoctorRepository
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.domain.doctor.DoctorNaming
import com.hos.rushdpatients.domain.doctor.DoctorCsvCodec
import com.hos.rushdpatients.domain.doctor.DoctorValidationResult
import com.hos.rushdpatients.domain.doctor.DoctorValidator
import com.hos.rushdpatients.domain.auth.PasswordHasher
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
    private val patientRepository: PatientRepository,
    private val auditRepository: AuditRepository,
    private val syncService: SyncService,
    private val passwordHasher: PasswordHasher,
    private val adminAuthorizer: AdminAuthorizer
) : ViewModel() {

    private val _state = MutableStateFlow(DoctorsUiState())
    val state: StateFlow<DoctorsUiState> = _state.asStateFlow()
    private var pendingImportDoctors: List<Doctor>? = null

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

    fun addDoctor(
        firstName: String,
        lastName: String,
        gender: Gender,
        clinicalRole: ClinicalRole,
        pin: String?,
        customTitle: String?,
        telegramId: Long?,
        onSuccess: () -> Unit = {}
    ) {
        if (!isValid(firstName, lastName, customTitle)) return
        if (pin == null || !pin.all(Char::isDigit) ||
            pin.length !in AppConstants.PIN_MIN_LENGTH..AppConstants.PIN_MAX_LENGTH
        ) {
            fail("الرقم السري مطلوب ويجب أن يكون من 4 إلى 8 أرقام")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                val actor = adminAuthorizer.requireAdmin()
                if (doctorRepository.count() >= AppConstants.MAX_DOCTORS) {
                    fail("تم الوصول إلى الحد الأقصى لعدد الأطباء")
                    return@launch
                }
                val fullName = DoctorNaming.formatName(firstName, lastName)
                if (doctorRepository.getByFullName(fullName) != null) {
                    fail("الاسم موجود مسبقاً")
                    return@launch
                }
                if (telegramId != null && doctorRepository.getByTelegramId(telegramId) != null) {
                    fail("حساب تليجرام مرتبط بمستخدم آخر")
                    return@launch
                }
                doctorRepository.upsert(
                    Doctor(
                        id = DoctorNaming.stableId(fullName),
                        fullName = fullName,
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        gender = gender,
                        clinicalRole = clinicalRole,
                        telegramId = telegramId,
                        customTitle = customTitle?.takeIf { it.isNotBlank() },
                        extraOptions = setOf("pin:${passwordHasher.hash(pin)}")
                    )
                )
                auditRepository.record(actor.id, actor.fullName, AppConstants.AUDIT_DOCTOR_ADDED, fullName)
                finishWithSync("تم إضافة الطبيب", onSuccess)
            } catch (e: Exception) {
                fail(e.message ?: "تعذر إضافة الطبيب")
            }
        }
    }

    fun editDoctor(
        id: String,
        firstName: String,
        lastName: String,
        gender: Gender,
        clinicalRole: ClinicalRole,
        pin: String?,
        customTitle: String?,
        telegramId: Long?,
        onSuccess: () -> Unit = {}
    ) {
        if (!isValid(firstName, lastName, customTitle)) return
        if (pin != null && (!pin.all(Char::isDigit) ||
                    pin.length !in AppConstants.PIN_MIN_LENGTH..AppConstants.PIN_MAX_LENGTH)
        ) {
            fail("الرقم السري الجديد يجب أن يكون من 4 إلى 8 أرقام")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                val actor = adminAuthorizer.requireAdmin()
                val existing = doctorRepository.getById(id) ?: return@launch fail("الطبيب غير موجود")
                val fullName = DoctorNaming.formatName(firstName, lastName)
                val conflict = doctorRepository.getByFullName(fullName)
                if (conflict != null && conflict.id != id) return@launch fail("الاسم موجود مسبقاً")
                val tgConflict = telegramId?.let { doctorRepository.getByTelegramId(it) }
                if (tgConflict != null && tgConflict.id != id) {
                    return@launch fail("حساب تليجرام مرتبط بمستخدم آخر")
                }
                doctorRepository.upsert(
                    existing.copy(
                        fullName = fullName,
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        gender = gender,
                        clinicalRole = clinicalRole,
                        supervisorGroupChatId = existing.supervisorGroupChatId
                            .takeIf { clinicalRole == ClinicalRole.SUPERVISOR },
                        telegramId = telegramId,
                        customTitle = customTitle?.takeIf { it.isNotBlank() },
                        extraOptions = if (pin == null) existing.extraOptions else {
                            existing.extraOptions.filterNot { it.startsWith("pin:") }.toSet() +
                                    "pin:${passwordHasher.hash(pin)}"
                        }
                    )
                )
                auditRepository.record(actor.id, actor.fullName, AppConstants.AUDIT_DOCTOR_EDITED, fullName)
                finishWithSync("تم تحديث الطبيب", onSuccess)
            } catch (e: Exception) {
                fail(e.message ?: "تعذر تحديث الطبيب")
            }
        }
    }

    fun deleteDoctor(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                val actor = adminAuthorizer.requireAdmin()
                val doctor = doctorRepository.getById(id) ?: return@launch fail("الطبيب غير موجود")
                if (doctor.isAdmin) {
                    return@launch fail("أزل صلاحية المدير قبل حذف الطبيب")
                }
                if (patientRepository.countActiveReferencesToDoctor(id) > 0) {
                    return@launch fail("لا يمكن حذف طبيب مسؤول عن مرضى حاليين")
                }
                doctorRepository.softDelete(id)
                auditRepository.record(actor.id, actor.fullName, AppConstants.AUDIT_DOCTOR_DELETED, doctor.fullName)
                finishWithSync("تم حذف الطبيب", onSuccess)
            } catch (e: Exception) {
                fail(e.message ?: "تعذر حذف الطبيب")
            }
        }
    }

    fun refresh() {
        if (_state.value.saving) return
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

    fun exportDoctors(uri: Uri) {
        if (_state.value.exporting || _state.value.importing) return
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                val actor = adminAuthorizer.requireAdmin()
                val doctors = doctorRepository.getAllIncludingDeleted()
                val csv = DoctorCsvCodec.encode(doctors)
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("تعذر فتح ملف التصدير")
                    stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write("\uFEFF")
                        writer.write(csv)
                    }
                }
                auditRepository.record(
                    actor.id,
                    actor.fullName,
                    AppConstants.AUDIT_DOCTORS_EXPORTED,
                    "export_csv:${doctors.size}"
                )
                _state.update {
                    it.copy(exporting = false, snackbar = "تم تصدير ${doctors.size} سجل طبيب إلى CSV")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(exporting = false, snackbar = e.message ?: "تعذر تصدير سجل الأطباء")
                }
            }
        }
    }

    fun prepareDoctorImport(uri: Uri) {
        if (_state.value.exporting || _state.value.importing) return
        _state.update { it.copy(importing = true, importPreview = null) }
        viewModelScope.launch {
            try {
                adminAuthorizer.requireAdmin()
                val text = withContext(Dispatchers.IO) { readCsv(uri) }
                val decoded = DoctorCsvCodec.decode(text)
                val existing = doctorRepository.getAllIncludingDeleted()
                val existingKeys = existing.flatMap { doctor ->
                    listOfNotNull(
                        "id:${doctor.id}",
                        doctor.telegramId?.let { "tg:$it" },
                        "name:${doctor.fullName.lowercase()}"
                    )
                }.toSet()
                val newWithoutPin = decoded.doctors.count { doctor ->
                    !doctor.isDeleted && listOfNotNull(
                        "id:${doctor.id}",
                        doctor.telegramId?.let { "tg:$it" },
                        "name:${doctor.fullName.lowercase()}"
                    ).none { it in existingKeys }
                }
                pendingImportDoctors = decoded.doctors
                _state.update {
                    it.copy(
                        importing = false,
                        importPreview = DoctorImportPreview(
                            activeCount = decoded.activeCount,
                            adminCount = decoded.adminCount,
                            deletedCount = decoded.deletedCount,
                            newWithoutPinCount = newWithoutPin
                        )
                    )
                }
            } catch (e: Exception) {
                pendingImportDoctors = null
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
        pendingImportDoctors = null
        _state.update { it.copy(importPreview = null) }
    }

    fun confirmDoctorImport() {
        val imported = pendingImportDoctors ?: return
        if (_state.value.importing) return
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            try {
                val actor = adminAuthorizer.requireAdmin()
                val existing = doctorRepository.getAllIncludingDeleted()
                val merged = imported.map { incoming ->
                    val matches = existing.filter { candidate ->
                        candidate.id == incoming.id ||
                            (incoming.telegramId != null && candidate.telegramId == incoming.telegramId) ||
                            candidate.fullName.equals(incoming.fullName, ignoreCase = true)
                    }
                    require(matches.map { it.id }.distinct().size <= 1) {
                        "بيانات ${incoming.fullName} تطابق أكثر من طبيب محلي"
                    }
                    val local = matches.singleOrNull()
                    val localSecrets = local?.extraOptions.orEmpty()
                        .filter { it.startsWith("pin:") }
                        .toSet()
                    var result = incoming.copy(
                        id = local?.id ?: incoming.id,
                        extraOptions = incoming.extraOptions + localSecrets,
                        // A portable file may be old; importing it must not delete a
                        // currently active local clinician implicitly.
                        deletedAt = if (local != null && !local.isDeleted) null else incoming.deletedAt
                    )
                    if (local?.isPermanentAdmin == true || local?.id == actor.id) {
                        result = result.copy(
                            rank = local.rank,
                            isPermanentAdmin = local.isPermanentAdmin,
                            deletedAt = null
                        )
                    }
                    result
                }
                require(merged.map { it.id }.distinct().size == merged.size) {
                    "تطابق أكثر من صف مستورد مع الطبيب المحلي نفسه"
                }
                val finalById = existing.associateBy { it.id }.toMutableMap().apply {
                    merged.forEach { put(it.id, it) }
                }
                require(finalById.values.count { !it.isDeleted } <= AppConstants.MAX_DOCTORS) {
                    "سيؤدي الاستيراد إلى تجاوز الحد الأقصى لعدد الأطباء"
                }
                val finalActive = finalById.values.filterNot { it.isDeleted }
                require(finalActive.map { it.fullName.lowercase() }.distinct().size == finalActive.size) {
                    "سيؤدي الاستيراد إلى تكرار اسم طبيب محلي"
                }
                val finalTelegramIds = finalActive.mapNotNull { it.telegramId }
                require(finalTelegramIds.distinct().size == finalTelegramIds.size) {
                    "سيؤدي الاستيراد إلى تكرار معرّف تليجرام"
                }
                val finalAdminRanks = finalActive.filter { it.rank > 0 }.map { it.rank }
                require(finalAdminRanks.distinct().size == finalAdminRanks.size) {
                    "سيؤدي الاستيراد إلى تكرار رتبة مدير"
                }

                doctorRepository.upsertAll(merged)
                auditRepository.record(
                    actor.id,
                    actor.fullName,
                    AppConstants.AUDIT_DOCTORS_IMPORTED,
                    "import_csv:${merged.size}"
                )
                val sync = uploadRegistry()
                pendingImportDoctors = null
                _state.update {
                    it.copy(
                        importing = false,
                        importPreview = null,
                        snackbar = if (sync.isSuccess) {
                            "تم استيراد ${merged.size} سجل ومزامنة سجل الأطباء"
                        } else {
                            "تم الاستيراد محلياً، وتعذرت مزامنة تليجرام: " +
                                sync.exceptionOrNull()?.message.orEmpty()
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(importing = false, snackbar = e.message ?: "تعذر استيراد سجل الأطباء")
                }
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

    private fun isValid(firstName: String, lastName: String, customTitle: String?): Boolean {
        val validation = DoctorValidator.validate(firstName, lastName, customTitle)
        if (validation is DoctorValidationResult.Invalid) {
            _state.update { it.copy(snackbar = "بيانات غير صحيحة") }
            return false
        }
        return true
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
