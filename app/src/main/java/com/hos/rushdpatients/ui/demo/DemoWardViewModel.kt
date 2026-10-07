package com.hos.rushdpatients.ui.demo

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
    val message: String? = null
) {
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
    @ApplicationContext private val context: Context,
    private val pdfExporter: PdfReportExporter
) : ViewModel() {
    private val _state = MutableStateFlow(DemoWardState())
    internal val state = _state.asStateFlow()

    internal fun endSession() { _state.value = DemoWardState() }
    internal fun reset() { if (!_state.value.exporting) _state.value = DemoWardState(message = "أُعيدت البيانات التجريبية") }
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
        val actor = current.doctors.first()
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
            message = "حُفظ التغيير في الجلسة التجريبية فقط") }
        return true
    }

    internal fun delete(patient: Patient) = _state.update { current ->
        current.copy(patients = current.patients.filterNot { it.id == patient.id },
            deleted = current.deleted + patient, message = "نُقل إلى المحذوفات التجريبية")
    }
    internal fun restore(patient: Patient) = _state.update { current ->
        current.copy(patients = current.patients + patient,
            deleted = current.deleted.filterNot { it.id == patient.id }, message = "استُعيد المريض التجريبي")
    }

    internal fun exportPdf(uri: Uri?, options: PdfExportOptions, elegant: Boolean) {
        if (uri == null || _state.value.exporting) return
        val snapshot = _state.value
        if (snapshot.patients.isEmpty() || snapshot.shiftDoctors.isEmpty()) {
            message("أضف مرضى واختر أطباء المناوبة أولاً")
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
                            options = options, elegant = elegant, documentLabel = "عرض تجريبي — بيانات وهمية وغير طبية",
                            outputFile = file)
                        check(file.length() > 0) { "تعذر إنشاء التقرير التجريبي" }
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            file.inputStream().use { it.copyTo(output) }
                            output.flush()
                        } ?: error("تعذر فتح ملف PDF")
                        val expected = file.readBytes()
                        val saved = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("تعذر التحقق من ملف PDF")
                        check(saved.contentEquals(expected)) { "لم يُحفظ التقرير كاملاً؛ أعد التصدير" }
                    } finally { file.delete() }
                }
                message("حُفظ تقرير PDF التجريبي؛ لم تُرفع أي بيانات")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(e.message ?: "تعذر تصدير التقرير التجريبي")
            } finally { _state.update { it.copy(exporting = false) } }
        }
    }
}
