package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.ward.*
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.hos.rushdpatients.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.pdf.PdfReportExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

internal data class DemoWardState(
    val doctors: List<Doctor> = DemoData.doctors(),
    val patients: List<Patient> = DemoData.patients(doctors),
    val deleted: List<Patient> = emptyList(),
    val shiftDoctorIds: List<String> = doctors.take(2).map { it.id },
    val sort: SortSpec = SortSpec(),
    val date: LocalDate = com.hos.rushdpatients.util.ShiftDate.current(),
    val actorId: String = doctors.first().id,
    val message: String? = null,
    val shiftId: String = "demo-${java.util.UUID.randomUUID()}",
    val createdAt: Instant = Instant.now(),
    val revision: Long = 0,
    val expanded: Boolean = false, val twoColumn: Boolean = false, val compact: Boolean = false,
    val groupBy: GroupByMode = GroupByMode.NONE, val draft: PatientDraft? = null,
    val fetchChoiceRequired: Boolean = false, val fetchReviewText: String? = null
) {
    val shift: Shift get() = Shift(id = shiftId, date = date, doctorIds = shiftDoctorIds,
        createdAt = createdAt, updatedAt = createdAt, revision = revision, sortSpecJson = SortSpecCodec.encode(sort))
    val actor: Doctor get() = doctors.firstOrNull { it.id == actorId } ?: doctors.first()
    val names: Map<String, String> get() = doctors.associate { it.id to it.fullName }
    val shiftDoctors: List<Doctor> get() = shiftDoctorIds.mapNotNull { id -> doctors.find { it.id == id } }
    val ordered: List<Patient> get() = PatientComparators.ordered(patients, sort, names)
    val summary: ReportSummary get() = ReportSummary(patients.size,
        patients.count { it.diagnosisType == DiagnosisType.PSYCHIATRIC || it.diagnosisType == DiagnosisType.DUAL },
        patients.count { it.hasCompanion }, date, shiftDoctors)
}

/** Deliberately has no clinical repository, settings store, session, or network dependencies. */
@HiltViewModel
class DemoWardViewModel @Inject constructor(
    private val uiText: UiStrings,
    @ApplicationContext private val context: Context,
    private val repository: InMemoryDemoWardRepository,
    pdfExporter: PdfReportExporter,
    assignmentRenderer: com.hos.rushdpatients.pdf.PdfAssignmentRenderer,
    mediaStoreSaver: com.hos.rushdpatients.pdf.MediaStoreSaver,
    textReportBuilder: com.hos.rushdpatients.domain.report.TextReportBuilder
) : ViewModel() {
    internal val editor = com.hos.rushdpatients.ui.ward.WardEditorState()
    private val _state = repository.state
    internal val state = _state.asStateFlow()
    private val dayLabel = MutableStateFlow(com.hos.rushdpatients.util.ShiftDate.current())
    private val offline = MutableStateFlow(false)
    internal val report = DemoReportController(context, uiText, viewModelScope, repository,
        pdfExporter, assignmentRenderer, mediaStoreSaver, textReportBuilder, ::selectDoctors)
    internal val doctors = DemoDoctorsController(repository, viewModelScope, uiText)
    internal val busy get() = report.busy && !report.state.value.loading
    private var pendingPublication: DemoWardState? = null
    private val editDrafts = mutableMapOf<String, PatientDraft>()
    init {
        viewModelScope.launch {
            while (true) {
                val boundary = com.hos.rushdpatients.util.ShiftDate.endOf(com.hos.rushdpatients.util.ShiftDate.current()).toEpochMilli()
                kotlinx.coroutines.delay((boundary - System.currentTimeMillis()).coerceAtLeast(1_000L))
                refreshDayLabel()
            }
        }
    }
    override fun onCleared() { report.reset(); super.onCleared() }
    internal fun refreshDayLabel() { dayLabel.value = com.hos.rushdpatients.util.ShiftDate.current() }

    private fun wardState(snapshot: DemoWardState) = WardUiState(
        loading = false, hospitalName = uiText(R.string.ui_demo_navigation_001),
        shift = snapshot.shift,
        recycleBinLoading = false,
        fetchChoiceRequired = snapshot.fetchChoiceRequired, fetchReviewText = snapshot.fetchReviewText,
        synchronization = repository.changes(snapshot).let { changes -> WardSynchronization(
            pending = changes.pending, pendingCount = changes.count, selectedSnapshot = changes.state,
            publishedAt = repository.publishedAt, downloadedAt = repository.downloadedAt,
            lastReportAt = repository.receipts.value.lastOrNull()?.at) },
        patients = snapshot.ordered, doctors = snapshot.doctors, deletedPatients = snapshot.deleted,
        groupedPatients = groupPatients(uiText, snapshot.ordered, snapshot.doctors, snapshot.groupBy),
        sortSpec = snapshot.sort, groupByMode = snapshot.groupBy, patientDraft = snapshot.draft,
        patientDetailsExpanded = snapshot.expanded, twoColumn = snapshot.twoColumn,
        compactCards = snapshot.compact, snackbar = snapshot.message
    )
    private val workspace = _state.map(::wardState).stateIn(viewModelScope,
        SharingStarted.Eagerly, wardState(_state.value))

    internal val controller: WardController = object : WardController {
        override val liveProject = false
        override val synchronizationAvailable = true
        override val state get() = workspace
        override val online get() = offline
        override val dayLabel get() = this@DemoWardViewModel.dayLabel
        override val editor get() = this@DemoWardViewModel.editor
        private fun unavailable() { message(uiText(R.string.ui_demo_local_only)) }
        override fun addPatient(patient: Patient, position: Int?, onSuccess: () -> Unit) {
            val current = _state.value
            if (current.patients.any { it.id == patient.id }) { unavailable(); return }
            if (save(patient)) {
                _state.update { saved ->
                    if (position == null) saved.copy(draft = null) else {
                        val ordered = saved.ordered.filterNot { it.id == patient.id }.toMutableList()
                        ordered.add((position - 1).coerceIn(0, ordered.size), saved.patients.first { it.id == patient.id })
                        saved.copy(patients = ordered.mapIndexed { index, row -> row.copy(sortOrder = index + 1) },
                            sort = SortSpec.EMPTY, draft = null)
                    }
                }
                onSuccess()
            }
        }
        override fun movePatientToTop(patient: Patient) {
            val current = _state.value
            val saved = current.patients.firstOrNull { it.id == patient.id } ?: return
            val rest = current.ordered.filterNot { it.id == patient.id }
            val rows = if (saved.isPriority) listOf(saved) + rest
                else rest.filter { it.isPriority } + saved + rest.filterNot { it.isPriority }
            _state.update { it.copy(patients = rows.mapIndexed { index, row ->
                if (row.sortOrder == index + 1) row else row.copy(sortOrder = index + 1, revision = row.revision + 1)
            }, sort = SortSpec.EMPTY, revision = it.revision + 1) }
        }
        override fun updatePatient(patient: Patient, onUnavailable: (Patient?) -> Unit, onSuccess: () -> Unit) {
            if (save(patient, onUnavailable)) onSuccess()
        }
        override fun deletePatient(patient: Patient, onSuccess: (Patient) -> Unit) {
            val saved = _state.value.patients.firstOrNull { it.id == patient.id }
            if (saved == null) { unavailable(); return }
            delete(patient); onSuccess(patient)
        }
        override fun restorePatient(patient: Patient) {
            if (_state.value.patients.none { it.id == patient.id }) restore(patient)
        }
        override fun applySort(spec: SortSpec, onSuccess: () -> Unit) {
            sort(spec); onSuccess()
        }
        override fun saveDraft(draft: PatientDraft) { _state.update { it.copy(draft = draft) } }
        override fun setTaskDone(patient: Patient, taskId: String, done: Boolean, onSuccess: (Patient) -> Unit) {
            val latest = _state.value.patients.firstOrNull { it.id == patient.id } ?: return
            if (latest.tasks.none { it.id == taskId && it.done != done }) return
            val changed = latest.copy(tasks = latest.tasks.map { task ->
                if (task.id != taskId) task else task.copy(done = done,
                    completedByDoctorId = null, completedByName = null, completedAtEpochMillis = null)
            })
            if (save(changed)) _state.value.patients.firstOrNull { it.id == patient.id }?.let(onSuccess)
        }
        override fun setPriority(patient: Patient, priority: Boolean, onSuccess: () -> Unit) {
            val latest = _state.value.patients.firstOrNull { it.id == patient.id } ?: return
            if (save(latest.copy(isPriority = priority))) onSuccess()
        }
        override fun setPatientDetailsExpanded(expanded: Boolean) { _state.update { it.copy(expanded = expanded) } }
        override fun setTwoColumn(enabled: Boolean) { _state.update { it.copy(twoColumn = enabled) } }
        override fun setCompactCards(compact: Boolean) { _state.update { it.copy(compact = compact) } }
        override fun setGroupByMode(mode: GroupByMode) { _state.update { it.copy(groupBy = mode) } }
        override fun dismissSnackbar() { dismissMessage() }
        override fun loadRecycleBin() { }
        override fun bringLatestData() {
            refreshDayLabel()
            if (repository.changes().pending) _state.update { it.copy(fetchChoiceRequired = true) }
            else replaceAndFetch()
        }
        override fun replaceAndFetch(allowOlder: Boolean) {
            repository.fetch()
            message(uiText(R.string.demo_fetch_complete))
        }
        override fun reviewPendingPublication() {
            pendingPublication = _state.value
            _state.update { it.copy(fetchChoiceRequired = false, fetchReviewText = uiText(R.string.demo_publish_review)) }
        }
        override fun confirmPendingPublication() {
            val snapshot = pendingPublication ?: return
            if (repository.changes(snapshot) != repository.changes()) {
                dismissFetchReview(); message(uiText(R.string.demo_review_changed)); return
            }
            repository.publish(snapshot)
            dismissFetchReview()
            message(uiText(R.string.demo_publish_complete))
        }
        override fun dismissFetchReview() {
            pendingPublication = null
            _state.update { it.copy(fetchChoiceRequired = false, fetchReviewText = null) }
        }
        override fun loadEditDraft(id: String, done: (PatientDraft?) -> Unit) { done(editDrafts[id]) }
        override fun saveEditDraft(id: String, draft: PatientDraft) { editDrafts[id] = draft }
        override fun discardEditDraft(id: String) { editDrafts.remove(id) }
        override fun retryLastAction() { bringLatestData() }
    }

    internal fun endSession() {
        editor.clear(); editDrafts.clear(); pendingPublication = null
        report.reset(); repository.reset(); doctors.reset()
    }
    internal fun reset() {
        if (!busy) {
            endSession()
            message(uiText(R.string.ui_demo_ward_view_model_001))
        }
    }
    internal fun undoFetch() { repository.undoFetch() }
    internal val canUndoFetch get() = repository.undo != null
    internal fun selectActor(id: String) {
        if (_state.value.doctors.any { it.id == id && !it.isDeleted }) _state.update { it.copy(actorId = id) }
    }
    internal fun dismissMessage() = _state.update { it.copy(message = null) }
    internal fun message(value: String) = _state.update { it.copy(message = value) }
    internal fun sort(spec: SortSpec) = _state.update { current ->
        current.copy(sort = spec, patients = PatientComparators.ordered(current.patients, spec, current.names)
            .mapIndexed { index, patient -> patient.copy(sortOrder = index + 1) }, revision = current.revision + 1)
    }
    internal fun selectDoctors(ids: List<String>) {
        if (ids.isNotEmpty() && ids.distinct().size == ids.size && ids.all { it in _state.value.names }) {
            _state.update { it.copy(shiftDoctorIds = ids, revision = it.revision + 1) }
        }
    }

    internal fun save(patient: Patient, onUnavailable: (Patient?) -> Unit = {}): Boolean {
        val current = _state.value
        val before = current.patients.find { it.id == patient.id }
        if (current.deleted.any { it.id == patient.id }) {
            onUnavailable(before)
            return false
        }
        val prepared = com.hos.rushdpatients.domain.patient.PatientAssignmentChange.onSave(
            patient, before, com.hos.rushdpatients.util.ShiftDate.current()
        ).copy(
            revision = (before?.revision ?: 0) + 1, updatedAt = Instant.EPOCH,
            lastEditedByDoctorId = null, lastEditedByName = null,
            sortOrder = before?.sortOrder ?: current.patients.size + 1,
            tasks = patient.tasks.map { task ->
                task.copy(completedByDoctorId = null, completedByName = null, completedAtEpochMillis = null)
            }
        )
        PatientTasks.requireValid(prepared.tasks)
        com.hos.rushdpatients.domain.patient.PatientDoctorAssignments.requireValid(prepared, current.doctors.associateBy { it.id })
        _state.update { it.copy(patients = it.patients.filterNot { row -> row.id == prepared.id } + prepared,
            message = uiText(R.string.ui_demo_ward_view_model_002), revision = it.revision + 1) }
        return true
    }

    internal fun delete(patient: Patient) = _state.update { current ->
        current.copy(patients = current.patients.filterNot { it.id == patient.id },
            deleted = current.deleted + patient, revision = current.revision + 1, message = uiText(R.string.ui_demo_ward_view_model_003))
    }
    internal fun restore(patient: Patient) = _state.update { current ->
        current.copy(patients = current.patients + patient,
            deleted = current.deleted.filterNot { it.id == patient.id }, revision = current.revision + 1, message = uiText(R.string.ui_demo_ward_view_model_004))
    }

}
