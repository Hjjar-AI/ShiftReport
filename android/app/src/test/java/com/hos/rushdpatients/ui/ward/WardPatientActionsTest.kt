package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.data.repository.*
import com.hos.rushdpatients.domain.auth.SessionManager
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.util.ShiftDate
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WardPatientActionsTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val shift = Fixtures.shift(date = ShiftDate.current())
    private val row = Fixtures.patient().copy(revision = 7)
    private val state = MutableStateFlow(WardUiState(shift = shift, patients = listOf(row)))
    private val shifts = mockk<ShiftRepository> { coEvery { getById(shift.id) } returns shift }
    private val patients = mockk<PatientRepository>()
    private val mutations = mockk<WardMutationRepository>()
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val sessions = mockk<SessionManager> { every { current() } returns null }
    private fun actions(scope: CoroutineScope) = WardPatientActions(text, state, scope, shifts, patients,
        mutations, settings, sessions, Json, WardSaveGuard(text, state, shifts))

    @Test fun `draft debounce persists only latest clinical draft`() = runTest {
        val actions = actions(this)
        actions.saveDraft(PatientDraft(name = "شاي")); advanceTimeBy(200)
        actions.saveDraft(PatientDraft(name = "قهوة", labDraft = "نتيجة")); advanceTimeBy(351)
        val encoded = slot<String>()
        coVerify(exactly = 1) { settings.put(AppConstants.SETTING_PATIENT_DRAFT, capture(encoded)) }
        val saved = Json.decodeFromString<PatientDraft>(encoded.captured)
        assertEquals("قهوة", saved.name); assertEquals("نتيجة", saved.labDraft)
    }
    @Test fun `empty draft deletes persisted copy instead of retaining stale clinical text`() = runTest {
        actions(this).saveDraft(PatientDraft()); advanceTimeBy(351)
        coVerify { settings.delete(AppConstants.SETTING_PATIENT_DRAFT) }
        assertNull(state.value.patientDraft)
    }
    @Test fun `failed save keeps draft and never calls success`() = runTest {
        val draft = PatientDraft(name = "شاي", labDraft = "مسودة")
        state.value = state.value.copy(patientDraft = draft)
        coEvery { mutations.updatePatient(any(), shift.id, any()) } throws IllegalStateException("synthetic failure")
        actions(this).updatePatient(row, onSuccess = { fail("Unexpected success") }); runCurrent()
        assertEquals(draft, state.value.patientDraft)
        assertFalse(state.value.saving); assertEquals("synthetic failure", state.value.snackbar)
    }
    @Test fun `duplicate save during suspended mutation enters repository once`() = runTest {
        coEvery { mutations.updatePatient(any(), shift.id, any()) } coAnswers { awaitCancellation() }
        val actions = actions(backgroundScope)
        actions.updatePatient(row); runCurrent()
        actions.updatePatient(row); runCurrent()
        coVerify(exactly = 1) { mutations.updatePatient(any(), shift.id, any()) }
    }
    @Test fun `shift becoming archived before mutation prevents write`() = runTest {
        coEvery { shifts.getById(shift.id) } returns shift.copy(date = ShiftDate.current().minusDays(1))
        actions(this).updatePatient(row, onSuccess = { fail("Unexpected success") }); runCurrent()
        coVerify(exactly = 0) { mutations.updatePatient(any(), any(), any()) }
        assertFalse(state.value.saving); assertNotNull(state.value.snackbar)
    }
    @Test fun `task board callback receives repository stamped record`() = runTest {
        val patient = row.copy(tasks = listOf(Fixtures.task()))
        val saved = patient.copy(revision = 8, tasks = listOf(Fixtures.task(done = true)))
        coEvery { mutations.updatePatient(any(), shift.id, any()) } returns saved
        var result: com.hos.rushdpatients.data.model.Patient? = null
        actions(this).setTaskDone(patient, "task-1", true) { result = it }; runCurrent()
        assertEquals(saved, result)
        coVerify { mutations.updatePatient(match { it.tasks.single().done && it.tasks.single().completedByDoctorId == null }, shift.id, any()) }
    }
    @Test fun `no-op task action does not mutate or invoke success`() = runTest {
        val actions = actions(this)
        actions.setTaskDone(row, "missing", true) { fail("Unexpected success") }
        actions.setTaskDone(row.copy(tasks = listOf(Fixtures.task())), "task-1", false) { fail("Unexpected success") }
        runCurrent()
        coVerify(exactly = 0) { mutations.updatePatient(any(), any(), any()) }
    }
    @Test fun `successful delete callback uses saved repository revision`() = runTest {
        val deleted = row.copy(revision = 8, deletedAt = Fixtures.timestamp)
        coEvery { mutations.deletePatient(row, shift.id, any()) } returns deleted
        var result: com.hos.rushdpatients.data.model.Patient? = null
        actions(this).deletePatient(row) { result = it }; runCurrent()
        assertEquals(deleted, result); assertFalse(state.value.saving)
    }
    @Test fun `scope cancellation drops pending draft persistence`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        actions(scope).saveDraft(PatientDraft(name = "مسودة")); scope.cancel(); advanceTimeBy(1000)
        coVerify(exactly = 0) { settings.put(any(), any()) }
    }
}
