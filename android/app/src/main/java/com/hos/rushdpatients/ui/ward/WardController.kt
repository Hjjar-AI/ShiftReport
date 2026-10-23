package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.SortSpec
import kotlinx.coroutines.flow.StateFlow

/** Injected presentation boundary: production operations or an isolated in-memory demo. */
internal interface WardController {
    val liveProject: Boolean
    val state: StateFlow<WardUiState>
    val online: StateFlow<Boolean>
    val taskNow: StateFlow<Long>
    val editor: WardEditorState
    fun addPatient(patient: Patient, position: Int? = null, expectedPatients: List<Patient>? = null, expectedShiftRevision: Long? = null, onSuccess: () -> Unit = {})
    fun applyRollover(keepAssignments: Boolean)
    fun applySort(spec: SortSpec, expectedRevision: Long, onStale: (Shift?) -> Unit,
        onSuccess: () -> Unit = {})
    fun bringLatestData()
    fun bringPreviousData()
    fun deletePatient(patient: Patient, onSuccess: (Patient) -> Unit = {})
    fun dismissRollover()
    fun dismissSnackbar()
    fun dismissSnapshotPicker()
    fun loadLocalActivity()
    fun loadRecentActivity()
    fun loadRecycleBin()
    fun offerPreviousShift()
    fun restorePatient(patient: Patient)
    fun retryLastAction()
    fun saveDraft(draft: PatientDraft)
    fun selectDownloadedSnapshot(shiftId: String)
    fun setCompactCards(compact: Boolean)
    fun setGroupByMode(mode: GroupByMode)
    fun setPatientDetailsExpanded(expanded: Boolean)
    fun setTaskDone(patient: Patient, taskId: String, done: Boolean, onSuccess: (Patient) -> Unit)
    fun setPriority(patient: Patient, priority: Boolean)
    fun setSaveAndNext(enabled: Boolean)
    fun setTwoColumn(enabled: Boolean)
    fun updatePatient(patient: Patient, onStale: (Patient?) -> Unit = {},
        onSuccess: () -> Unit = {})
}

internal class LiveWardController(private val viewModel: WardViewModel) : WardController {
    override val liveProject = true
    override val state get() = viewModel.state
    override val online get() = viewModel.online
    override val taskNow get() = viewModel.taskNow
    override val editor get() = viewModel.editor
    override fun addPatient(patient: Patient, position: Int?, expectedPatients: List<Patient>?, expectedShiftRevision: Long?, onSuccess: () -> Unit) { viewModel.addPatient(patient, position, expectedPatients, expectedShiftRevision, onSuccess) }
    override fun applyRollover(keepAssignments: Boolean) { viewModel.applyRollover(keepAssignments) }
    override fun applySort(spec: SortSpec, expectedRevision: Long, onStale: (Shift?) -> Unit,
        onSuccess: () -> Unit) { viewModel.applySort(spec, expectedRevision, onStale, onSuccess) }
    override fun bringLatestData() { viewModel.bringLatestData() }
    override fun bringPreviousData() { viewModel.bringPreviousData() }
    override fun deletePatient(patient: Patient, onSuccess: (Patient) -> Unit) { viewModel.deletePatient(patient, onSuccess) }
    override fun dismissRollover() { viewModel.dismissRollover() }
    override fun dismissSnackbar() { viewModel.dismissSnackbar() }
    override fun dismissSnapshotPicker() { viewModel.dismissSnapshotPicker() }
    override fun loadLocalActivity() { viewModel.loadLocalActivity() }
    override fun loadRecentActivity() { viewModel.loadRecentActivity() }
    override fun loadRecycleBin() { viewModel.loadRecycleBin() }
    override fun offerPreviousShift() { viewModel.offerPreviousShift() }
    override fun restorePatient(patient: Patient) { viewModel.restorePatient(patient) }
    override fun retryLastAction() { viewModel.retryLastAction() }
    override fun saveDraft(draft: PatientDraft) { viewModel.saveDraft(draft) }
    override fun selectDownloadedSnapshot(shiftId: String) { viewModel.selectDownloadedSnapshot(shiftId) }
    override fun setCompactCards(compact: Boolean) { viewModel.setCompactCards(compact) }
    override fun setGroupByMode(mode: GroupByMode) { viewModel.setGroupByMode(mode) }
    override fun setPatientDetailsExpanded(expanded: Boolean) { viewModel.setPatientDetailsExpanded(expanded) }
    override fun setTaskDone(patient: Patient, taskId: String, done: Boolean, onSuccess: (Patient) -> Unit) { viewModel.setTaskDone(patient, taskId, done, onSuccess) }
    override fun setPriority(patient: Patient, priority: Boolean) { viewModel.setPriority(patient, priority) }
    override fun setSaveAndNext(enabled: Boolean) { viewModel.setSaveAndNext(enabled) }
    override fun setTwoColumn(enabled: Boolean) { viewModel.setTwoColumn(enabled) }
    override fun updatePatient(patient: Patient, onStale: (Patient?) -> Unit, onSuccess: () -> Unit) { viewModel.updatePatient(patient, onStale, onSuccess) }
}
