package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.R

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.repository.StalePatientEditException
import com.hos.rushdpatients.data.repository.StaleShiftEditException
import com.hos.rushdpatients.data.repository.PatientRepository
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.data.repository.SettingsRepository
import com.hos.rushdpatients.data.repository.WardMutationRepository
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Uses the owning ViewModel state/scope; repository guards remain unchanged. */
internal class WardPatientActions(
    private val uiText: UiStrings,
    private val _state: MutableStateFlow<WardUiState>,
    private val viewModelScope: kotlinx.coroutines.CoroutineScope,
    private val shiftRepository: ShiftRepository,
    private val patientRepository: PatientRepository,
    private val wardMutations: WardMutationRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionManager: SessionManager,
    private val json: Json,
    private val saveGuard: WardSaveGuard
) {
    private var draftSaveJob: Job? = null

    private val editDraftJobs = mutableMapOf<String, Job>()
    fun loadEditDraft(id: String, done: (PatientDraft?) -> Unit) {
        viewModelScope.launch {
            try {
                val draft = settingsRepository.get("patient_edit_draft:$id")?.let { json.decodeFromString<PatientDraft>(it) }
                done(draft)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { saveGuard.showError(uiText(R.string.patient_edit_draft_unavailable)); done(null) }
        }
    }
    fun saveEditDraft(id: String, draft: PatientDraft) {
        editDraftJobs.remove(id)?.cancel()
        editDraftJobs[id] = viewModelScope.launch {
            delay(350)
            settingsRepository.put("patient_edit_draft:$id", json.encodeToString(PatientDraft.serializer(), draft))
        }
    }
    fun discardEditDraft(id: String) {
        editDraftJobs.remove(id)?.cancel()
        viewModelScope.launch {
            try { settingsRepository.delete("patient_edit_draft:$id") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { saveGuard.showError(uiText(R.string.patient_edit_draft_discard_failed)) }
        }
    }
    private suspend fun clearEditDraft(id: String) {
        editDraftJobs.remove(id)?.cancel()
        settingsRepository.delete("patient_edit_draft:$id")
    }

    fun loadDraft() {
        viewModelScope.launch {
            val draft = settingsRepository.get(AppConstants.SETTING_PATIENT_DRAFT)
                ?.let { runCatching { json.decodeFromString<PatientDraft>(it) }.getOrNull() }
                ?.takeUnless { it.isEmpty }
            _state.update { it.copy(patientDraft = draft) }
        }
    }

    fun saveDraft(draft: PatientDraft) {
        _state.update { it.copy(patientDraft = draft.takeUnless { value -> value.isEmpty }) }
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(350)
            if (draft.isEmpty) settingsRepository.delete(AppConstants.SETTING_PATIENT_DRAFT)
            else settingsRepository.put(
                AppConstants.SETTING_PATIENT_DRAFT,
                json.encodeToString(PatientDraft.serializer(), draft)
            )
        }
    }

    private suspend fun clearDraft() {
        draftSaveJob?.cancel()
        settingsRepository.delete(AppConstants.SETTING_PATIENT_DRAFT)
        _state.update { it.copy(patientDraft = null) }
    }

    fun addPatient(patient: Patient, position: Int? = null, expectedPatients: List<Patient>? = null,
        expectedShiftRevision: Long? = null, onSuccess: () -> Unit = {}) {
        val shiftId = saveGuard.editableShiftId() ?: return
        viewModelScope.launch {
            if (!saveGuard.validateForSave(patient)) return@launch
            if (patientRepository.countForShift(shiftId) >= AppConstants.MAX_PATIENTS_PER_SHIFT) {
                saveGuard.showError(uiText(R.string.ui_ward_patient_actions_001))
                return@launch
            }
            saveGuard.saveAction(onSuccess = {
                _state.update { it.copy(snackbar = uiText(R.string.ui_ward_patient_actions_002)) }
                onSuccess()
            }) {
                saveGuard.requireEditableShift(shiftId)
                wardMutations.addPatient(patient, shiftId, sessionManager.current(), position, expectedPatients, expectedShiftRevision)
                clearDraft()
            }
        }
    }

    fun movePatientToTop(patient: Patient) {
        val shiftId = saveGuard.editableShiftId() ?: return
        val revision = _state.value.shift?.revision ?: return
        viewModelScope.launch {
            saveGuard.saveAction(onSuccess = {}) {
                wardMutations.movePatientToTop(patient, shiftId, revision, sessionManager.current())
            }
        }
    }

    fun updatePatient(
        patient: Patient,
        onStale: (Patient?) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val shiftId = saveGuard.editableShiftId() ?: return
        viewModelScope.launch {
            if (!saveGuard.validateForSave(patient)) return@launch
            saveGuard.saveAction(onSuccess = {
                _state.update { it.copy(snackbar = uiText(R.string.ui_ward_patient_actions_002)) }
                onSuccess()
            }, onStale = { error ->
                if (error !is StalePatientEditException) false else {
                    val latest = patientRepository.getById(patient.id)
                    onStale(latest?.takeIf { patientRepository.getShiftId(it.id) == shiftId })
                    true
                }
            }) {
                saveGuard.requireEditableShift(shiftId)
                wardMutations.updatePatient(patient, shiftId, sessionManager.current())
                clearEditDraft(patient.id)
            }
        }
    }

    fun setTaskDone(patient: Patient, taskId: String, done: Boolean, onSuccess: (Patient) -> Unit) {
        if (_state.value.saving || _state.value.syncing) return
        val shiftId = saveGuard.editableShiftId() ?: return
        if (patient.tasks.none { it.id == taskId && it.done != done }) return
        val changed = patient.copy(tasks = patient.tasks.map { task ->
            if (task.id != taskId) task else task.copy(done = done,
                completedByDoctorId = null, completedByName = null, completedAtEpochMillis = null)
        })
        viewModelScope.launch {
            if (!saveGuard.validateForSave(changed)) return@launch
            var saved: Patient? = null
            saveGuard.saveAction(onSuccess = { saved?.let(onSuccess) }) {
                saveGuard.requireEditableShift(shiftId)
                saved = wardMutations.updatePatient(changed, shiftId, sessionManager.current())
            }
        }
    }

    fun deletePatient(patient: Patient, onSuccess: (Patient) -> Unit = {}) {
        val shiftId = saveGuard.editableShiftId() ?: return
        var deleted: Patient? = null
        viewModelScope.launch {
            saveGuard.saveAction(onSuccess = { deleted?.let(onSuccess) }) {
                saveGuard.requireEditableShift(shiftId)
                deleted = wardMutations.deletePatient(patient, shiftId, sessionManager.current())
            }
        }
    }

    fun loadRecycleBin() {
        val shiftId = _state.value.shift?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(deletedPatients = patientRepository.getSoftDeletedForShift(shiftId)) }
        }
    }

    fun restorePatient(patient: Patient) {
        val shiftId = saveGuard.editableShiftId() ?: return
        viewModelScope.launch {
            saveGuard.saveAction(onSuccess = { loadRecycleBin() }) {
                saveGuard.requireEditableShift(shiftId)
                wardMutations.restorePatient(patient, shiftId, sessionManager.current())
            }
        }
    }

    fun setPriority(patient: Patient, priority: Boolean) {
        val shiftId = saveGuard.editableShiftId() ?: return
        viewModelScope.launch {
            saveGuard.saveAction(onSuccess = {}) {
                saveGuard.requireEditableShift(shiftId)
                wardMutations.updatePatient(
                    patient.copy(isPriority = priority), shiftId, sessionManager.current()
                )
            }
        }
    }

    fun setShiftDoctors(
        ids: List<String>,
        expectedRevision: Long,
        onStale: (Shift?) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        val shiftId = saveGuard.editableShiftId() ?: return
        val shift = _state.value.shift?.takeIf { it.id == shiftId } ?: return
        val activeIds = _state.value.doctors.filterNot { it.isDeleted }.mapTo(mutableSetOf()) { it.id }
        when {
            ids.size < AppConstants.MIN_SHIFT_DOCTORS -> {
                saveGuard.showError(uiText(R.string.ui_ward_patient_actions_003))
                return
            }
            ids.distinct().size != ids.size || ids.any { it !in activeIds } -> {
                saveGuard.showError(uiText(R.string.ui_ward_patient_actions_004))
                return
            }
        }
        viewModelScope.launch {
            saveGuard.saveAction(onSuccess, onStale = { error ->
                if (error !is StaleShiftEditException) false else {
                    onStale(shiftRepository.getById(shiftId))
                    true
                }
            }) {
                saveGuard.requireEditableShift(shiftId)
                wardMutations.setShiftDoctors(
                    shiftId = shift.id,
                    doctorIds = ids,
                    expectedRevision = expectedRevision,
                    actor = sessionManager.current()
                )
            }
        }
    }

    fun applySort(
        spec: SortSpec,
        expectedRevision: Long,
        onStale: (Shift?) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        val shiftId = saveGuard.editableShiftId() ?: return
        viewModelScope.launch {
            saveGuard.saveAction(onSuccess, onStale = { error ->
                if (error !is StaleShiftEditException) false else {
                    onStale(shiftRepository.getById(shiftId))
                    true
                }
            }) {
                saveGuard.requireEditableShift(shiftId)
                wardMutations.setShiftSort(
                    shiftId = shiftId,
                    sortSpecJson = SortSpecCodec.encode(spec),
                    expectedRevision = expectedRevision,
                    actor = sessionManager.current()
                )
            }
        }
    }
}
