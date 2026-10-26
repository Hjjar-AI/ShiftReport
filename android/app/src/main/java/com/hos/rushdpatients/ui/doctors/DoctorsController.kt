package com.hos.rushdpatients.ui.doctors

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.domain.doctor.DoctorEditInput

/** The shared screen depends on actions/state, never on a particular connection or store. */
internal interface DoctorsController {
    val state: StateFlow<DoctorsUiState>
    val fileTransfers: Boolean get() = true
    val simulation: Boolean get() = false
    fun addDoctor(input: DoctorEditInput, onSuccess: () -> Unit)
    fun editDoctor(expected: Doctor, input: DoctorEditInput, onStale: (Doctor?) -> Unit, onSuccess: () -> Unit)
    fun deleteDoctor(expected: Doctor, onSuccess: () -> Unit)
    fun saveTeams(expected: List<Doctor>, assignments: Map<String, String?>, onSuccess: () -> Unit)
    fun publishDoctors(expected: List<Doctor>)
    fun fetchDoctors()
    fun dismissOperationIssue()
    fun dismissSnackbar()
    fun prepareDoctorExport()
    fun doctorExportPickerLaunched()
    fun doctorExportPickerFailed()
    fun exportDoctors(uri: Uri?)
    fun prepareDoctorImport(uri: Uri)
    fun dismissImportError()
    fun dismissDoctorImport()
    fun confirmDoctorImport()
}

internal class LiveDoctorsController(private val viewModel: DoctorsViewModel) : DoctorsController {
    override val state get() = viewModel.state
    override fun addDoctor(input: DoctorEditInput, onSuccess: () -> Unit) { viewModel.addDoctor(input, onSuccess) }
    override fun editDoctor(expected: Doctor, input: DoctorEditInput, onStale: (Doctor?) -> Unit, onSuccess: () -> Unit) { viewModel.editDoctor(expected, input, onStale, onSuccess) }
    override fun deleteDoctor(expected: Doctor, onSuccess: () -> Unit) { viewModel.deleteDoctor(expected, onSuccess) }
    override fun saveTeams(expected: List<Doctor>, assignments: Map<String, String?>, onSuccess: () -> Unit) { viewModel.saveTeams(expected, assignments, onSuccess) }
    override fun publishDoctors(expected: List<Doctor>) { viewModel.publishDoctors(expected) }
    override fun fetchDoctors() { viewModel.fetchDoctors() }
    override fun dismissOperationIssue() { viewModel.dismissOperationIssue() }
    override fun dismissSnackbar() { viewModel.dismissSnackbar() }
    override fun prepareDoctorExport() { viewModel.prepareDoctorExport() }
    override fun doctorExportPickerLaunched() { viewModel.doctorExportPickerLaunched() }
    override fun doctorExportPickerFailed() { viewModel.doctorExportPickerFailed() }
    override fun exportDoctors(uri: Uri?) { viewModel.exportDoctors(uri) }
    override fun prepareDoctorImport(uri: Uri) { viewModel.prepareDoctorImport(uri) }
    override fun dismissImportError() { viewModel.dismissImportError() }
    override fun dismissDoctorImport() { viewModel.dismissDoctorImport() }
    override fun confirmDoctorImport() { viewModel.confirmDoctorImport() }
}
