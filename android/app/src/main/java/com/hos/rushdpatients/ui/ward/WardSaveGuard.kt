package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

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
    private val uiText: UiStrings,
    private val _state: MutableStateFlow<WardUiState>,
    private val shiftRepository: ShiftRepository
) {

    fun editableShiftId(): String? {
        if (_state.value.loading || _state.value.error != null) {
            showError(uiText(R.string.ui_ward_refresh_before_edit))
            return null
        }
        val shift = _state.value.shift ?: return null
        return shift.id
    }

    suspend fun requireEditableShift(shiftId: String) {
        val shift = shiftRepository.getById(shiftId)
        require(shift != null) {
            uiText(R.string.ui_ward_save_guard_001)
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
            showError(uiText(R.string.ui_ward_save_guard_002))
            return false
        }
        if (!patient.responsibleSpecialistId.isNullOrBlank() &&
            (specialist == null || !specialist.clinicalRole.canBeSupervisor())
        ) {
            showError(uiText(R.string.ui_ward_save_guard_003))
            return false
        }
        return true
    }

    suspend fun saveAction(
        onSuccess: () -> Unit,
        onUnavailable: suspend (Exception) -> Boolean = { false },
        action: suspend () -> Unit
    ) {
        if (_state.value.saving) return
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
                if (!onUnavailable(e)) showError(com.hos.rushdpatients.ui.components.failureText(uiText, e) ?: uiText(R.string.ui_ward_save_guard_004))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (reloadError: Exception) {
                showError(reloadError.message ?: uiText(R.string.ui_ward_save_guard_005))
            }
        }
    }

    fun showError(message: String) = _state.update { it.copy(snackbar = message) }

    fun validationMessage(result: PatientValidationResult.Invalid): String =
        when (result.errors.firstOrNull()) {
            PatientValidationError.ADMITTANCE_DATE_INVALID -> uiText(R.string.ui_ward_save_guard_008)
            PatientValidationError.BIRTH_DATE_INVALID -> uiText(R.string.ui_ward_save_guard_009)
            PatientValidationError.TASKS_INVALID -> uiText(R.string.ui_ward_save_guard_013)
            PatientValidationError.RESIDENT_EQUALS_SUPERVISOR -> uiText(R.string.ui_ward_save_guard_014)
            null -> uiText(R.string.ui_ward_save_guard_015)
        }
}
