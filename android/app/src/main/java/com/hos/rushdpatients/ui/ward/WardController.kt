package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.SortSpec
import kotlinx.coroutines.flow.StateFlow

/** Injected presentation boundary: production operations or an isolated in-memory demo. */
internal interface WardController {
    val synchronizationAvailable: Boolean get() = liveProject
    val liveProject: Boolean
    val state: StateFlow<WardUiState>
    val online: StateFlow<Boolean>
    val dayLabel: StateFlow<java.time.LocalDate>
    val editor: WardEditorState
    fun addPatient(patient: Patient, position: Int? = null, onSuccess: () -> Unit = {})
    fun applySort(spec: SortSpec,
        onSuccess: () -> Unit = {})
    fun bringLatestData()
    fun replaceAndFetch(allowOlder: Boolean = false) {}
    fun reviewPendingPublication() {}
    fun confirmPendingPublication() {}
    fun dismissFetchReview() {}
    fun deletePatient(patient: Patient, onSuccess: (Patient) -> Unit = {})
    fun dismissSnackbar()
    fun loadRecycleBin()
    // Memory-only controllers do not hold a database subscription.
    fun stopRecycleBinObservation() {}
    fun restorePatient(patient: Patient)
    fun reloadWard() {}
    fun retryLastAction()
    fun loadEditDraft(id: String, done: (PatientDraft?) -> Unit) { done(null) }
    fun discardEditDraft(id: String) {}
    fun saveEditDraft(id: String, draft: PatientDraft) {}
    fun saveDraft(draft: PatientDraft)
    fun setCompactCards(compact: Boolean)
    fun setGroupByMode(mode: GroupByMode)
    fun setPatientDetailsExpanded(expanded: Boolean)
    fun setTaskDone(patient: Patient, taskId: String, done: Boolean, onSuccess: (Patient) -> Unit)
    fun setPriority(patient: Patient, priority: Boolean, onSuccess: () -> Unit = {})
    fun setTwoColumn(enabled: Boolean)
    fun movePatientToTop(patient: Patient) {}
    fun updatePatient(patient: Patient, onUnavailable: (Patient?) -> Unit = {},
        onSuccess: () -> Unit = {})
}

internal class LiveWardController(private val viewModel: WardViewModel) : WardController {
    override val liveProject = true
    override val state get() = viewModel.state
    override val online get() = viewModel.online
    override val dayLabel get() = viewModel.dayLabel
    override val editor get() = viewModel.editor
    override fun addPatient(patient: Patient, position: Int?, onSuccess: () -> Unit) { viewModel.addPatient(patient, position, onSuccess) }
    override fun applySort(spec: SortSpec,
        onSuccess: () -> Unit) { viewModel.applySort(spec, onSuccess) }
    override fun bringLatestData() { viewModel.bringLatestData() }
    override fun replaceAndFetch(allowOlder: Boolean) { viewModel.replaceAndFetch(allowOlder) }
    override fun reviewPendingPublication() { viewModel.reviewPendingPublication() }
    override fun confirmPendingPublication() { viewModel.confirmPendingPublication() }
    override fun dismissFetchReview() { viewModel.dismissFetchReview() }
    override fun deletePatient(patient: Patient, onSuccess: (Patient) -> Unit) { viewModel.deletePatient(patient, onSuccess) }
    override fun dismissSnackbar() { viewModel.dismissSnackbar() }
    override fun loadRecycleBin() { viewModel.loadRecycleBin() }
    override fun stopRecycleBinObservation() { viewModel.stopRecycleBinObservation() }
    override fun restorePatient(patient: Patient) { viewModel.restorePatient(patient) }
    override fun reloadWard() { viewModel.load() }
    override fun retryLastAction() { viewModel.retryLastAction() }
    override fun loadEditDraft(id: String, done: (PatientDraft?) -> Unit) { viewModel.loadEditDraft(id, done) }
    override fun discardEditDraft(id: String) { viewModel.discardEditDraft(id) }
    override fun saveEditDraft(id: String, draft: PatientDraft) { viewModel.saveEditDraft(id, draft) }
    override fun saveDraft(draft: PatientDraft) { viewModel.saveDraft(draft) }
    override fun setCompactCards(compact: Boolean) { viewModel.setCompactCards(compact) }
    override fun setGroupByMode(mode: GroupByMode) { viewModel.setGroupByMode(mode) }
    override fun setPatientDetailsExpanded(expanded: Boolean) { viewModel.setPatientDetailsExpanded(expanded) }
    override fun setTaskDone(patient: Patient, taskId: String, done: Boolean, onSuccess: (Patient) -> Unit) { viewModel.setTaskDone(patient, taskId, done, onSuccess) }
    override fun setPriority(patient: Patient, priority: Boolean, onSuccess: () -> Unit) { viewModel.setPriority(patient, priority, onSuccess) }
    override fun setTwoColumn(enabled: Boolean) { viewModel.setTwoColumn(enabled) }
    override fun movePatientToTop(patient: Patient) { viewModel.movePatientToTop(patient) }
    override fun updatePatient(patient: Patient, onUnavailable: (Patient?) -> Unit, onSuccess: () -> Unit) { viewModel.updatePatient(patient, onUnavailable, onSuccess) }
}
