package com.hos.rushdpatients.ui.demo

import com.hos.rushdpatients.ui.components.UiStrings

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
    val message: String? = null
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
    private val pdfExporter: PdfReportExporter
) : ViewModel() {
    private val _state = MutableStateFlow(DemoWardState())
    internal val state = _state.asStateFlow()

    private var pendingExport: Pair<PdfExportOptions, Boolean>? = null
    internal fun endSession() { pendingExport = null; _state.value = DemoWardState() }
    internal fun reset() {
        if (!_state.value.exporting) { pendingExport = null; _state.value = DemoWardState(message = uiText(R.string.ui_demo_ward_view_model_001)) }
    }
    internal fun selectActor(id: String) {
        if (_state.value.doctors.any { it.id == id && !it.isDeleted }) _state.update { it.copy(actorId = id) }
    }
    internal fun setSaveAndNext(enabled: Boolean) = _state.update { it.copy(saveAndNext = enabled) }
    internal fun dismissMessage() = _state.update { it.copy(message = null) }
    internal fun message(value: String) = _state.update { it.copy(message = value) }
    internal fun sort(spec: SortSpec) = _state.update { current ->
        current.copy(sort = spec, patients = PatientComparators.ordered(current.patients, spec, current.names))
    }
    internal fun selectDoctors(ids: List<String>) {
        if (ids.isNotEmpty() && ids.distinct().size == ids.size && ids.all { it in _state.value.names }) {
            _state.update { it.copy(shiftDoctorIds = ids) }
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
            message = uiText(R.string.ui_demo_ward_view_model_002)) }
        return true
    }

    internal fun delete(patient: Patient) = _state.update { current ->
        current.copy(patients = current.patients.filterNot { it.id == patient.id },
            deleted = current.deleted + patient, message = uiText(R.string.ui_demo_ward_view_model_003))
    }
    internal fun restore(patient: Patient) = _state.update { current ->
        current.copy(patients = current.patients + patient,
            deleted = current.deleted.filterNot { it.id == patient.id }, message = uiText(R.string.ui_demo_ward_view_model_004))
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
