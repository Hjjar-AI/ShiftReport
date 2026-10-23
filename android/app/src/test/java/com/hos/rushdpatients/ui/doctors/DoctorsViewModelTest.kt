package com.hos.rushdpatients.ui.doctors

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.AdminAuthorizer
import com.hos.rushdpatients.domain.doctor.DoctorEditInput
import com.hos.rushdpatients.domain.export.PendingExportStore
import com.hos.rushdpatients.sync.SyncService
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.testutil.ViewModelFixture
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DoctorsViewModelTest : ViewModelFixture() {
    private val doctor = Fixtures.doctor()
    private val registry = MutableStateFlow(listOf(doctor))
    private val pending = MutableStateFlow<String?>(null)
    private val baseline = MutableStateFlow<String?>(null)
    private val doctors = mockk<DoctorRepository>()
    private val settings = mockk<SettingsRepository>()
    private val mutations = mockk<DoctorMutationRepository>()
    private val audit = mockk<AuditRepository>(relaxed = true)
    private val sync = mockk<SyncService>()
    private val admin = mockk<AdminAuthorizer>()
    private val exports = mockk<PendingExportStore>(relaxed = true)
    private val handle = SavedStateHandle()
    private val input = DoctorEditInput("أرز", "شجرة", Gender.MALE, ClinicalRole.RESIDENT, null, null, null)
    private val uri = mockk<Uri>()
    private fun vm() = keep(DoctorsViewModel(text, mockk<Context>(), doctors, settings, mutations,
        audit, sync, admin, exports, handle))
    @Before fun seedRegistry() {
        every { doctors.observeAll() } returns registry
        every { settings.observe(AppConstants.SETTING_DOCTORS_SYNC_PENDING) } returns pending
        every { settings.observe(AppConstants.SETTING_DOCTORS_BASE_REGISTRY) } returns baseline
        coEvery { doctors.pendingSharedChangesSummary() } returns null
        coEvery { admin.requireAdmin() } returns doctor.copy(rank = 1)
    }
    @Test fun registryAndPendingSummaryAreObservedWithoutAutomaticPublication() {
        val vm = vm()
        assertEquals(listOf(doctor), vm.state.value.doctors); assertFalse(vm.state.value.loading)
        coEvery { doctors.pendingSharedChangesSummary() } returns "synthetic pending change"
        pending.value = "true"
        assertEquals("synthetic pending change", vm.state.value.pendingRegistrySummary)
        coVerify(exactly = 0) { sync.uploadCurrentDoctors() }
    }
    @Test fun successfulAddWaitsForRepositoryThenCallsSuccessWithoutPublishing() {
        coEvery { mutations.add(input) } returns 1L
        val vm = vm(); var successes = 0; vm.addDoctor(input) { successes++ }
        assertEquals(1, successes); assertFalse(vm.state.value.saving)
        coVerify(exactly = 1) { mutations.add(input) }
        coVerify(exactly = 0) { sync.uploadCurrentDoctors() }
    }
    @Test fun failedAddPreservesRegistryAndDoesNotCallSuccess() {
        coEvery { mutations.add(input) } throws IllegalArgumentException("synthetic duplicate identity")
        val vm = vm(); var successes = 0; vm.addDoctor(input) { successes++ }
        assertEquals(0, successes); assertEquals(listOf(doctor), vm.state.value.doctors)
        assertFalse(vm.state.value.saving); assertEquals("synthetic duplicate identity", vm.state.value.snackbar)
    }
    @Test fun duplicateAddAndOtherMutationsAreBlockedDuringSave() {
        coEvery { mutations.add(input) } coAnswers { awaitCancellation() }
        val vm = vm(); vm.addDoctor(input); vm.addDoctor(input); vm.deleteDoctor(doctor)
        vm.editDoctor(doctor, input, {}); vm.saveTeams(listOf(doctor), emptyMap()) {}
        coVerify(exactly = 1) { mutations.add(any()) }
        coVerify(exactly = 0) { mutations.delete(any()) }
        coVerify(exactly = 0) { mutations.edit(any(), any()) }
        coVerify(exactly = 0) { mutations.updateTeams(any(), any()) }
    }
    @Test fun addCancellationReleasesSavingWithoutSuccess() {
        coEvery { mutations.add(input) } coAnswers { awaitCancellation() }
        val vm = vm(); var successes = 0; vm.addDoctor(input) { successes++ }; clearViewModels()
        assertFalse(vm.state.value.saving); assertEquals(0, successes); assertNull(vm.state.value.snackbar)
    }
    @Test fun successfulEditUsesExactExpectedSnapshotAndInput() {
        coEvery { mutations.edit(doctor, input) } returns 1L
        val vm = vm(); var successes = 0; var conflicts = 0
        vm.editDoctor(doctor, input, { conflicts++ }, { successes++ })
        coVerify(exactly = 1) { mutations.edit(doctor, input) }
        assertEquals(1, successes); assertEquals(0, conflicts); assertFalse(vm.state.value.saving)
    }
    @Test fun staleEditReturnsLatestDoctorWithoutRetryingMutation() {
        val latest = doctor.copy(fullName = "أرز محدث")
        coEvery { mutations.edit(doctor, input) } throws StaleDoctorEditException()
        coEvery { doctors.getById(doctor.id) } returns latest
        val vm = vm(); var conflict: Doctor? = null; var successes = 0
        vm.editDoctor(doctor, input, { conflict = it }, { successes++ })
        assertEquals(latest, conflict); assertEquals(0, successes); assertFalse(vm.state.value.saving)
        coVerify(exactly = 1) { mutations.edit(doctor, input) }
    }
    @Test fun deletedStaleDoctorIsReportedAsUnavailable() {
        coEvery { mutations.edit(doctor, input) } throws StaleDoctorEditException()
        coEvery { doctors.getById(doctor.id) } returns null
        val vm = vm(); var called = false
        vm.editDoctor(doctor, input, { called = true; assertNull(it) })
        assertTrue(called); assertFalse(vm.state.value.saving)
    }
    @Test fun staleReloadFailureBecomesVisibleWithoutSuccessOrConflictCallback() {
        coEvery { mutations.edit(doctor, input) } throws StaleDoctorEditException()
        coEvery { doctors.getById(doctor.id) } throws IllegalStateException("synthetic reload failure")
        val vm = vm(); var callbacks = 0
        vm.editDoctor(doctor, input, { callbacks++ }, { callbacks++ })
        assertEquals(0, callbacks); assertFalse(vm.state.value.saving)
        assertEquals("synthetic reload failure", vm.state.value.snackbar)
    }
    @Test fun ordinaryEditFailureDoesNotTriggerStaleReload() {
        coEvery { mutations.edit(doctor, input) } throws IllegalArgumentException("synthetic invalid edit")
        val vm = vm(); vm.editDoctor(doctor, input, { fail("Unexpected stale callback") })
        coVerify(exactly = 0) { doctors.getById(any()) }
        assertEquals("synthetic invalid edit", vm.state.value.snackbar)
    }
    @Test fun teamSavePassesReviewedRegistryAndAssignmentsTogether() {
        val assignments = mapOf(doctor.id to "supervisor")
        coEvery { mutations.updateTeams(listOf(doctor), assignments) } returns 1L
        val vm = vm(); var successes = 0; vm.saveTeams(listOf(doctor), assignments) { successes++ }
        assertEquals(1, successes); assertFalse(vm.state.value.saving)
        coVerify { mutations.updateTeams(listOf(doctor), assignments) }
        coVerify(exactly = 0) { sync.uploadCurrentDoctors() }
    }
    @Test fun deleteFailureDoesNotRemoveDoctorOptimistically() {
        coEvery { mutations.delete(doctor) } throws IllegalStateException("synthetic active clinical references")
        val vm = vm(); var successes = 0; vm.deleteDoctor(doctor) { successes++ }
        assertEquals(0, successes); assertEquals(listOf(doctor), vm.state.value.doctors)
        assertFalse(vm.state.value.saving)
    }
    @Test fun successfulDeleteUsesExpectedSnapshotAndCallsSuccessOnce() {
        coEvery { mutations.delete(doctor) } returns 1L
        val vm = vm(); var successes = 0; vm.deleteDoctor(doctor) { successes++ }
        coVerify(exactly = 1) { mutations.delete(doctor) }
        assertEquals(1, successes); assertFalse(vm.state.value.saving)
    }
    @Test fun fetchRequiresLiveAdminBeforeReadingRemoteRegistry() {
        coEvery { admin.requireAdmin() } throws IllegalStateException("synthetic denied")
        val vm = vm(); vm.fetchDoctors()
        coVerify(exactly = 0) { sync.refreshDoctorsFromRemote() }
        assertFalse(vm.state.value.saving); assertEquals("synthetic denied", vm.state.value.snackbar)
    }
    @Test fun fetchFailureDoesNotPublishLocalRegistry() {
        coEvery { sync.refreshDoctorsFromRemote() } returns Result.failure(IllegalStateException("synthetic remote conflict"))
        val vm = vm(); vm.fetchDoctors()
        assertFalse(vm.state.value.saving); assertEquals("synthetic remote conflict", vm.state.value.snackbar)
        coVerify(exactly = 0) { sync.uploadCurrentDoctors() }
    }
    @Test fun duplicateFetchCannotStartAnotherNetworkOperation() {
        coEvery { sync.refreshDoctorsFromRemote() } coAnswers { awaitCancellation() }
        val vm = vm(); vm.fetchDoctors(); vm.fetchDoctors()
        coVerify(exactly = 1) { sync.refreshDoctorsFromRemote() }
    }
    @Test fun fetchCancellationReleasesSavingWithoutOrdinaryError() {
        coEvery { sync.refreshDoctorsFromRemote() } coAnswers { awaitCancellation() }
        val vm = vm(); vm.fetchDoctors(); clearViewModels()
        assertFalse(vm.state.value.saving); assertNull(vm.state.value.snackbar)
    }
    @Test fun recreatedPendingExportBlocksRegistryFetchAndNewExport() {
        handle["pending_doctor_export_id"] = "opaque-id"
        val vm = vm(); vm.fetchDoctors(); vm.prepareDoctorExport()
        assertTrue(vm.state.value.exporting)
        coVerify(exactly = 0) { sync.refreshDoctorsFromRemote() }
        coVerify(exactly = 0) { exports.stage(any()) }
    }
    @Test fun cancelledExportPickerDiscardsSavedStagingWithoutWriting() {
        handle["pending_doctor_export_id"] = "opaque-id"; handle["pending_doctor_export_count"] = 2
        val vm = vm(); vm.exportDoctors(null)
        assertTrue(handle.keys().isEmpty()); assertFalse(vm.state.value.exporting)
        coVerify { exports.discard("opaque-id") }
        coVerify(exactly = 0) { exports.write(any(), any()) }
    }
    @Test fun pickerFailureDiscardsSavedStagingAndReleasesExportState() {
        handle["pending_doctor_export_id"] = "opaque-id"; handle["pending_doctor_export_count"] = 2
        val vm = vm(); vm.doctorExportPickerFailed()
        assertTrue(handle.keys().isEmpty()); assertFalse(vm.state.value.exporting)
        coVerify { exports.discard("opaque-id") }
    }
    @Test fun destinationFailureCleansPartialFileWithoutSuccessAudit() {
        handle["pending_doctor_export_id"] = "opaque-id"; handle["pending_doctor_export_count"] = 2
        coEvery { exports.write(uri, "opaque-id") } throws IllegalStateException("synthetic write failure")
        val vm = vm(); vm.exportDoctors(uri)
        coVerify { exports.discardDestination(uri); exports.discard("opaque-id") }
        coVerify(exactly = 0) { audit.record(any(), any(), any(), any(), any(), any(), any()) }
        assertFalse(vm.state.value.exporting); assertTrue(handle.keys().isEmpty())
    }
    @Test fun successfulExportAuditsActorAndCountAfterVerifiedWrite() {
        handle["pending_doctor_export_id"] = "opaque-id"; handle["pending_doctor_export_count"] = 2
        val vm = vm(); vm.exportDoctors(uri)
        coVerifyOrder {
            admin.requireAdmin(); exports.write(uri, "opaque-id")
            audit.record(doctor.id, doctor.fullName, AppConstants.AUDIT_DOCTORS_EXPORTED, "export_csv:2", null, null, null)
            exports.discard("opaque-id")
        }
        assertFalse(vm.state.value.exporting); assertTrue(handle.keys().isEmpty())
    }
    @Test fun importConfirmationWithoutPreparedPlanCannotMutateOrUpload() {
        val vm = vm(); vm.confirmDoctorImport()
        coVerify(exactly = 0) { mutations.importRegistry(any()) }
        coVerify(exactly = 0) { sync.uploadCurrentDoctors() }
    }
}
