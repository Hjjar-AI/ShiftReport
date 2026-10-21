package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.domain.patient.PatientValidationError
import com.hos.rushdpatients.domain.patient.PatientValidationResult
import com.hos.rushdpatients.domain.patient.PatientValidator
import com.hos.rushdpatients.util.ShiftDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Shared save/revision/error handling; callers retain their existing coroutine ownership. */
internal class WardSaveGuard(
    private val _state: MutableStateFlow<WardUiState>,
    private val shiftRepository: ShiftRepository
) {

    fun editableShiftId(): String? {
        val shift = _state.value.shift ?: return null
        if (shift.date != ShiftDate.current()) {
            showError("هذه مناوبة محفوظة للعرض والاستعادة فقط ولا يمكن تعديلها")
            return null
        }
        return shift.id
    }

    suspend fun requireEditableShift(shiftId: String) {
        val shift = shiftRepository.getById(shiftId)
        require(shift != null && shift.date == ShiftDate.current()) {
            "هذه مناوبة محفوظة للعرض والاستعادة فقط ولا يمكن تعديلها"
        }
    }

    suspend fun validateForSave(patient: Patient): Boolean {
        val result = PatientValidator.validate(patient)
        if (result is PatientValidationResult.Invalid) {
            showError(validationMessage(result))
            return false
        }
        val doctorsById = _state.value.doctors.associateBy { it.id }
        val resident = patient.responsibleResidentId?.let(doctorsById::get)
        val specialist = patient.responsibleSpecialistId?.let(doctorsById::get)
        if (!patient.responsibleResidentId.isNullOrBlank() &&
            (resident == null || !resident.clinicalRole.canBeResident())
        ) {
            showError("اختر طبيباً مصنفاً كمقيم")
            return false
        }
        if (!patient.responsibleSpecialistId.isNullOrBlank() &&
            (specialist == null || !specialist.clinicalRole.canBeSupervisor())
        ) {
            showError("اختر طبيباً مصنفاً كاختصاصي")
            return false
        }
        return true
    }

    suspend fun saveAction(
        onSuccess: () -> Unit,
        onStale: suspend (Exception) -> Boolean = { false },
        action: suspend () -> Unit
    ) {
        _state.update { it.copy(saving = true) }
        try {
            action()
            _state.update { it.copy(saving = false) }
            onSuccess()
        } catch (e: CancellationException) {
            _state.update { it.copy(saving = false) }
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(saving = false) }
            try {
                if (!onStale(e)) showError(e.message ?: "تعذر حفظ التغييرات")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (reloadError: Exception) {
                showError(reloadError.message ?: "تعذر تحميل أحدث نسخة؛ المسودة باقية")
            }
        }
    }

    fun showError(message: String) = _state.update { it.copy(snackbar = message) }

    fun validationMessage(result: PatientValidationResult.Invalid): String =
        when (result.errors.firstOrNull()) {
            PatientValidationError.NAME_EMPTY -> "اسم المريض مطلوب"
            PatientValidationError.ADMITTANCE_NUMBER_EMPTY -> "رقم القبول الحالي مطلوب"
            PatientValidationError.ADMITTANCE_DATE_INVALID -> "تاريخ الدخول غير صحيح"
            PatientValidationError.BIRTH_DATE_INVALID -> "تاريخ الميلاد غير صحيح"
            PatientValidationError.INITIAL_DIAGNOSIS_EMPTY -> "التشخيص الأولي مطلوب"
            PatientValidationError.TREATMENT_PLAN_EMPTY -> "الخطة العلاجية مطلوبة"
            PatientValidationError.FOLLOW_UP_EMPTY -> "المتابعة مطلوبة"
            PatientValidationError.TASKS_INVALID -> "بيانات المهام غير صالحة"
            PatientValidationError.RESIDENT_EQUALS_SUPERVISOR -> "المقيم والمشرف يجب أن يكونا مختلفين"
            null -> "بيانات المريض غير صحيحة"
        }
}
