package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.ui.components.UiStrings

import com.hos.rushdpatients.ui.ward.*
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.domain.sort.SortSpecCodec
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.delay
import com.hos.rushdpatients.R

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hos.rushdpatients.data.model.DiagnosisType
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.domain.report.ReportSummary
import com.hos.rushdpatients.domain.sort.PatientComparators
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.domain.task.PatientTasks
import com.hos.rushdpatients.pdf.PdfExportOptions
import com.hos.rushdpatients.pdf.PdfReportExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

internal data class DemoWardState(
    val doctors: List<Doctor> = DemoData.doctors(),
    val patients: List<Patient> = DemoData.patients(doctors),
    val deleted: List<Patient> = emptyList(),
    val shiftDoctorIds: List<String> = doctors.take(2).map { it.id },
    val sort: SortSpec = SortSpec(),
    val date: LocalDate = LocalDate.now(),
    val exporting: Boolean = false,
    val saveAndNext: Boolean = false,
    val actorId: String = doctors.first().id,
    val message: String? = null,
    val shiftId: String = "demo-${java.util.UUID.randomUUID()}",
    val createdAt: Instant = Instant.now(),
    val revision: Long = 0,
    val expanded: Boolean = false, val twoColumn: Boolean = false, val compact: Boolean = false,
    val groupBy: GroupByMode = GroupByMode.NONE, val draft: PatientDraft? = null
) {
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
    repository: InMemoryDemoWardRepository,
    private val pdfExporter: PdfReportExporter
) : ViewModel() {
    internal val editor = com.hos.rushdpatients.ui.ward.WardEditorState()
    private val _state = repository.state
    internal val state = _state.asStateFlow()
    private val clock = MutableStateFlow(System.currentTimeMillis())
    private val offline = MutableStateFlow(false)
    private fun wardState(snapshot: DemoWardState) = WardUiState(
        loading = false, hospitalName = uiText(R.string.ui_demo_navigation_001),
        shift = Shift(id = snapshot.shiftId, date = snapshot.date, doctorIds = snapshot.shiftDoctorIds,
            createdAt = snapshot.createdAt, updatedAt = snapshot.createdAt, revision = snapshot.revision,
            sortSpecJson = SortSpecCodec.encode(snapshot.sort)),
        patients = snapshot.ordered, doctors = snapshot.doctors, deletedPatients = snapshot.deleted,
        groupedPatients = groupPatients(uiText, snapshot.ordered, snapshot.doctors, snapshot.groupBy),
        sortSpec = snapshot.sort, groupByMode = snapshot.groupBy, patientDraft = snapshot.draft,
        patientDetailsExpanded = snapshot.expanded, twoColumn = snapshot.twoColumn,
        compactCards = snapshot.compact, saveAndNext = snapshot.saveAndNext, snackbar = snapshot.message
    )
    private val workspace = _state.map(::wardState).stateIn(viewModelScope,
        SharingStarted.Eagerly, wardState(_state.value))
    init { viewModelScope.launch { while (true) { delay(60_000L); clock.value = System.currentTimeMillis() } } }

    internal val controller: WardController = object : WardController {
        override val liveProject = false
        override val state get() = workspace
        override val online get() = offline
        override val taskNow get() = clock
        override val editor get() = this@DemoWardViewModel.editor
        private fun unavailable() { message(uiText(R.string.ui_demo_local_only)) }
        override fun addPatient(patient: Patient, position: Int?, expectedPatients: List<Patient>?,
            expectedShiftRevision: Long?, onSuccess: () -> Unit) {
            val current = _state.value
            if (current.patients.any { it.id == patient.id } ||
                expectedShiftRevision?.let { it != current.revision } == true ||
                expectedPatients?.associate { it.id to it.revision }?.let { expected ->
                    expected != current.patients.associate { it.id to it.revision }
                } == true) { unavailable(); return }
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
        override fun updatePatient(patient: Patient, onStale: (Patient?) -> Unit, onSuccess: () -> Unit) {
            if (save(patient, onStale)) onSuccess()
        }
        override fun deletePatient(patient: Patient, onSuccess: (Patient) -> Unit) {
            val saved = _state.value.patients.firstOrNull { it.id == patient.id }
            if (saved == null || saved.revision != patient.revision) { unavailable(); return }
            delete(patient); onSuccess(patient)
        }
        override fun restorePatient(patient: Patient) {
            if (_state.value.patients.none { it.id == patient.id }) restore(patient)
        }
        override fun applySort(spec: SortSpec, expectedRevision: Long, onStale: (Shift?) -> Unit, onSuccess: () -> Unit) {
            if (_state.value.revision != expectedRevision) { onStale(wardState(_state.value).shift); return }
            sort(spec); onSuccess()
        }
        override fun saveDraft(draft: PatientDraft) { _state.update { it.copy(draft = draft) } }
        override fun setPriority(patient: Patient, priority: Boolean) { save(patient.copy(isPriority = priority)) }
        override fun setSaveAndNext(enabled: Boolean) { this@DemoWardViewModel.setSaveAndNext(enabled) }
        override fun setPatientDetailsExpanded(expanded: Boolean) { _state.update { it.copy(expanded = expanded) } }
        override fun setTwoColumn(enabled: Boolean) { _state.update { it.copy(twoColumn = enabled) } }
        override fun setCompactCards(compact: Boolean) { _state.update { it.copy(compact = compact) } }
        override fun setGroupByMode(mode: GroupByMode) { _state.update { it.copy(groupBy = mode) } }
        override fun dismissSnackbar() { dismissMessage() }
        override fun loadRecycleBin() { }
        override fun loadLocalActivity() { }
        override fun loadRecentActivity() { unavailable() }
        override fun bringLatestData() { unavailable() }
        override fun bringPreviousData() { unavailable() }
        override fun offerPreviousShift() { unavailable() }
        override fun applyRollover(keepAssignments: Boolean) { unavailable() }
        override fun dismissRollover() { }
        override fun selectDownloadedSnapshot(shiftId: String) { unavailable() }
        override fun dismissSnapshotPicker() { }
        override fun retryLastAction() { unavailable() }
    }

    private var pendingExport: Pair<PdfExportOptions, Boolean>? = null
    internal fun endSession() { editor.clear(); pendingExport = null; _state.value = DemoWardState() }
    internal fun reset() {
        if (!_state.value.exporting) { editor.clear(); pendingExport = null; _state.value = DemoWardState(message = uiText(R.string.ui_demo_ward_view_model_001)) }
    }
    internal fun selectActor(id: String) {
        if (_state.value.doctors.any { it.id == id && !it.isDeleted }) _state.update { it.copy(actorId = id) }
    }
    internal fun setSaveAndNext(enabled: Boolean) = _state.update { it.copy(saveAndNext = enabled) }
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

    internal fun save(patient: Patient, onStale: (Patient?) -> Unit = {}): Boolean {
        val current = _state.value
        val before = current.patients.find { it.id == patient.id }
        if ((before != null && before.revision != patient.revision) || current.deleted.any { it.id == patient.id }) {
            onStale(before)
            return false
        }
        val actor = current.actor
        val oldTasks = before?.tasks.orEmpty().associateBy { it.id }
        val prepared = patient.copy(
            revision = (before?.revision ?: 0) + 1, updatedAt = Instant.now(),
            lastEditedByDoctorId = actor.id, lastEditedByName = actor.fullName,
            sortOrder = before?.sortOrder ?: current.patients.size + 1,
            tasks = patient.tasks.map { task ->
                val old = oldTasks[task.id]
                task.copy(ownerName = task.ownerDoctorId?.let(current.names::get),
                    completedByDoctorId = if (task.done) old?.completedByDoctorId ?: actor.id else null,
                    completedByName = if (task.done) old?.completedByName ?: actor.fullName else null,
                    completedAtEpochMillis = if (task.done) old?.completedAtEpochMillis ?: System.currentTimeMillis() else null)
            }
        )
        PatientTasks.requireValid(prepared.tasks)
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

    internal fun preparePdfExport(options: PdfExportOptions, elegant: Boolean) {
        if (!_state.value.exporting) pendingExport = options to elegant
    }

    internal fun exportPreparedPdf(uri: Uri?) {
        val prepared = pendingExport
        pendingExport = null
        if (uri == null) return
        if (prepared == null) { message(uiText(R.string.ui_demo_ward_view_model_005)); return }
        exportPdf(uri, prepared.first, prepared.second)
    }

    private fun exportPdf(uri: Uri?, options: PdfExportOptions, elegant: Boolean) {
        if (uri == null || _state.value.exporting) return
        val snapshot = _state.value
        if (snapshot.patients.isEmpty() || snapshot.shiftDoctors.isEmpty()) {
            message(uiText(R.string.ui_demo_ward_view_model_006))
            return
        }
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val file = File.createTempFile("demo-report-", ".pdf", context.cacheDir)
                    try {
                        pdfExporter.export(patients = snapshot.ordered, doctors = snapshot.shiftDoctors,
                            summary = snapshot.summary, residentNames = snapshot.names, supervisorNames = snapshot.names,
                            options = options.copy(hospitalName = "مشفى تجريبي", wardName = "جناح تجريبي"), elegant = elegant, documentLabel = "عرض تجريبي — بيانات وهمية وغير طبية",
                            outputFile = file)
                        check(file.length() > 0) { uiText(R.string.ui_demo_ward_view_model_007) }
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            file.inputStream().use { it.copyTo(output) }
                            output.flush()
                        } ?: error(uiText(R.string.ui_demo_ward_view_model_008))
                        val expected = file.readBytes()
                        val saved = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error(uiText(R.string.ui_demo_ward_view_model_009))
                        check(saved.contentEquals(expected)) { uiText(R.string.ui_demo_ward_view_model_010) }
                    } finally { file.delete() }
                }
                message(uiText(R.string.ui_demo_ward_view_model_011))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(e.message ?: uiText(R.string.ui_demo_ward_view_model_012))
            } finally { _state.update { it.copy(exporting = false) } }
        }
    }
}
