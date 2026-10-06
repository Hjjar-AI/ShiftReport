package com.hos.rushdpatients.ui.doctors

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
import com.hos.rushdpatients.domain.doctor.DoctorValidationResult
import com.hos.rushdpatients.domain.doctor.DoctorValidator
import com.hos.rushdpatients.domain.auth.PasswordHasher
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.sync.DoctorsRegistryCodec
import com.hos.rushdpatients.sync.SyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DoctorsViewModel @Inject constructor(
    private val doctorRepository: DoctorRepository,
    private val patientRepository: PatientRepository,
    private val auditRepository: AuditRepository,
    private val syncService: SyncService,
    private val passwordHasher: PasswordHasher,
    private val adminAuthorizer: AdminAuthorizer
) : ViewModel() {

    private val _state = MutableStateFlow(DoctorsUiState())
    val state: StateFlow<DoctorsUiState> = _state.asStateFlow()

    init { observe() }

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
        val all = doctorRepository.getAll()
        return syncService.uploadDoctors(DoctorsRegistryCodec.encode(all))
    }
}
