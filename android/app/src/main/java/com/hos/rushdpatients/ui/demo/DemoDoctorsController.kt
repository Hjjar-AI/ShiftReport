package com.hos.rushdpatients.ui.demo

import android.net.Uri
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.domain.doctor.*
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.ui.doctors.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** Real doctor forms/team management with transient mutations and fake registry publication. */
internal class DemoDoctorsController(
    private val repository: InMemoryDemoWardRepository,
    scope: CoroutineScope,
    private val uiText: UiStrings
) : DoctorsController {
    override val simulation = true
    override val fileTransfers = false
    private val baseline get() = repository.registry
    private val notices = MutableStateFlow(DoctorsUiState(loading = false))
    override val state = combine(repository.state, notices) { ward, notice ->
        notice.copy(doctors = ward.doctors, pendingRegistrySummary =
            if (ward.doctors == baseline) null else uiText(R.string.demo_registry_pending))
    }.stateIn(scope, SharingStarted.Eagerly, DoctorsUiState(loading = false, doctors = baseline))

    fun reset() { notices.value = DoctorsUiState(loading = false) }
    private fun mutate(onSuccess: () -> Unit, change: () -> List<Doctor>) {
        try {
            val doctors = change()
            DoctorTeams.validate(doctors)
            require(doctors.map { it.fullName.lowercase() }.distinct().size == doctors.size) {
                uiText(R.string.demo_doctor_duplicate)
            }
            repository.state.update { it.copy(doctors = doctors, shiftDoctorIds = it.shiftDoctorIds.filter { id -> doctors.any { doctor -> doctor.id == id } },
                revision = it.revision + 1) }
            notices.update { it.copy(operationIssue = null) }
            onSuccess()
        } catch (e: Exception) { notices.update { it.copy(operationIssue = e.message) } }
    }
    private fun prepared(input: DoctorEditInput, before: Doctor? = null): Doctor {
        require(DoctorValidator.validate(input.firstName, input.lastName, input.customTitle) is DoctorValidationResult.Valid)
        // Demo never retains entered PINs, Telegram identities or destinations.
        val id = before?.id ?: "demo-doctor-${java.util.UUID.randomUUID()}"
        val doctor = (before ?: Doctor(id = id, fullName = "", firstName = "", lastName = "", gender = input.gender)).copy(
            fullName = DoctorNaming.formatName(input.firstName, input.lastName, id),
            firstName = input.firstName.trim(), lastName = input.lastName.trim(), gender = input.gender,
            clinicalRole = input.clinicalRole, telegramId = null, customTitle = null)
        return DoctorTeams.assign(doctor, input.teamSupervisorId.takeIf { input.clinicalRole == ClinicalRole.RESIDENT })
    }
    override fun addDoctor(input: DoctorEditInput, onSuccess: () -> Unit) = mutate(onSuccess) {
        repository.state.value.doctors + prepared(input)
    }
    override fun editDoctor(expected: Doctor, input: DoctorEditInput, onStale: (Doctor?) -> Unit, onSuccess: () -> Unit) {
        val ward = repository.state.value
        val current = ward.doctors.find { it.id == expected.id } ?: run { onStale(null); return }
        if (current != expected) { onStale(current); return }
        mutate(onSuccess) {
            require(input.clinicalRole == current.clinicalRole || ward.patients.none {
                it.responsibleResidentId == current.id || it.responsibleSpecialistId == current.id
            }) { uiText(R.string.demo_doctor_in_use) }
            ward.doctors.map { if (it.id == current.id) prepared(input, current) else it }
        }
    }
    override fun deleteDoctor(expected: Doctor, onSuccess: () -> Unit) = mutate(onSuccess) {
        val ward = repository.state.value
        require(ward.doctors.find { it.id == expected.id } == expected)
        require(ward.doctors.size > 1 && expected.id != ward.actorId &&
            (ward.patients + ward.deleted).none { it.responsibleResidentId == expected.id || it.responsibleSpecialistId == expected.id }) {
            uiText(R.string.demo_doctor_in_use)
        }
        ward.doctors.filterNot { it.id == expected.id }
    }
    override fun saveTeams(expected: List<Doctor>, assignments: Map<String, String?>, onSuccess: () -> Unit) = mutate(onSuccess) {
        val doctors = repository.state.value.doctors
        require(doctors == expected)
        doctors.map { if (it.id in assignments) DoctorTeams.assign(it, assignments[it.id]) else it }
    }
    override fun publishDoctors(expected: List<Doctor>) {
        if (expected != repository.state.value.doctors) return
        repository.publishDoctors(expected)
        notices.update { it.copy(snackbar = uiText(R.string.demo_publish_complete)) }
    }
    override fun fetchDoctors() {
        if (repository.state.value.doctors != baseline) return
        repository.state.update { it.copy(doctors = baseline, revision = it.revision + 1) }
        notices.update { it.copy(snackbar = uiText(R.string.demo_fetch_complete)) }
    }
    override fun dismissOperationIssue() { notices.update { it.copy(operationIssue = null) } }
    override fun dismissSnackbar() { notices.update { it.copy(snackbar = null) } }
    private fun unavailable() { notices.update { it.copy(snackbar = uiText(R.string.ui_demo_local_only)) } }
    override fun prepareDoctorExport() = unavailable()
    override fun doctorExportPickerLaunched() = unavailable()
    override fun doctorExportPickerFailed() = unavailable()
    override fun exportDoctors(uri: Uri?) = unavailable()
    override fun prepareDoctorImport(uri: Uri) = unavailable()
    override fun dismissImportError() { notices.update { it.copy(importError = null) } }
    override fun dismissDoctorImport() { notices.update { it.copy(importPreview = null) } }
    override fun confirmDoctorImport() = unavailable()
}
