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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WardPatientActionsTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val shift = Fixtures.shift(date = ShiftDate.current())
    private val row = Fixtures.patient().copy(revision = 7)
    private val state = MutableStateFlow(WardUiState(loading = false, shift = shift, patients = listOf(row)))
    private val shifts = mockk<ShiftRepository> { coEvery { getById(shift.id) } returns shift }
    private val patients = mockk<PatientRepository>()
    private val mutations = mockk<WardMutationRepository>()
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val sessions = mockk<SessionManager> { every { current() } returns null }
    private fun actions(scope: CoroutineScope) = WardPatientActions(text, state, scope, shifts, patients,
        mutations, settings, sessions, Json, WardSaveGuard(text, state))

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
        coEvery { mutations.updatePatientFromForm(any(), shift.id) } throws IllegalStateException("synthetic failure")
        actions(this).updatePatient(row, onSuccess = { fail("Unexpected success") }); runCurrent()
        assertEquals(draft, state.value.patientDraft)
        assertFalse(state.value.saving); assertEquals("synthetic failure", state.value.snackbar)
    }
    @Test fun `duplicate save during suspended mutation enters repository once`() = runTest {
        coEvery { mutations.updatePatientFromForm(any(), shift.id) } coAnswers { awaitCancellation() }
        val actions = actions(backgroundScope)
        actions.updatePatient(row); runCurrent()
        actions.updatePatient(row); runCurrent()
        coVerify(exactly = 1) { mutations.updatePatientFromForm(any(), shift.id) }
    }
    @Test fun `local reload failure prevents mutation and retains loaded patient`() = runTest {
        state.value = state.value.copy(error = "synthetic reload failure")
        actions(this).updatePatient(row, onSuccess = { fail("Unexpected success") }); runCurrent()
        coVerify(exactly = 0) { mutations.updatePatientFromForm(any(), any()) }
        assertEquals(row, state.value.patients.single())
        assertFalse(state.value.saving); assertNotNull(state.value.snackbar)
    }
    @Test fun `task board callback receives repository stamped record`() = runTest {
        val patient = row.copy(tasks = listOf(Fixtures.task()))
        val saved = patient.copy(revision = 8, tasks = listOf(Fixtures.task(done = true)))
        coEvery { patients.getById(patient.id) } returns patient
        coEvery { mutations.updatePatient(any(), shift.id) } returns saved
        var result: com.hos.rushdpatients.data.model.Patient? = null
        actions(this).setTaskDone(patient, "task-1", true) { result = it }; runCurrent()
        assertEquals(saved, result)
        coVerify { mutations.updatePatient(match { it.tasks.single().done && it.tasks.single().completedByDoctorId == null }, shift.id) }
    }
    @Test fun `missing patient task action cannot mutate or claim success`() = runTest {
        coEvery { patients.getById(row.id) } returns null
        actions(this).setTaskDone(row, "missing", true) { fail("Unexpected success") }
        runCurrent()
        coVerify(exactly = 0) { mutations.updatePatient(any(), any()) }
        assertFalse(state.value.saving)
        assertNotNull(state.value.snackbar)
    }
    @Test fun `successful delete callback uses saved repository revision`() = runTest {
        val deleted = row.copy(revision = 8, deletedAt = Fixtures.timestamp)
        coEvery { mutations.deletePatient(row, shift.id) } returns deleted
        var result: com.hos.rushdpatients.data.model.Patient? = null
        actions(this).deletePatient(row) { result = it }; runCurrent()
        assertEquals(deleted, result); assertFalse(state.value.saving)
    }
    @Test fun `scope cancellation drops pending draft persistence`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        actions(scope).saveDraft(PatientDraft(name = "مسودة")); scope.cancel(); advanceTimeBy(1000)
        coVerify(exactly = 0) { settings.put(any(), any()) }
    }
    @Test fun `failed autosave retains add input and successful retry clears its warning`() = runTest {
        val draft = PatientDraft(name = "شاي", labDraft = "نتيجة\n☕")
        coEvery { settings.put(AppConstants.SETTING_PATIENT_DRAFT, any()) } throws java.io.IOException("synthetic full disk")
        val actions = actions(this)
        actions.saveDraft(draft); advanceUntilIdle()
        assertEquals(draft, state.value.patientDraft)
        assertNotNull(state.value.draftSaveIssue)
        assertNull(state.value.draftSaveIssue?.patientId)
        coEvery { settings.put(AppConstants.SETTING_PATIENT_DRAFT, any()) } returns Unit
        actions.saveDraft(draft); advanceUntilIdle()
        assertNull(state.value.draftSaveIssue)
        assertEquals(draft, state.value.patientDraft)
    }
    @Test fun `edit warning is patient scoped and another successful autosave cannot clear it`() = runTest {
        coEvery { settings.put(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + "tea", any()) } throws java.io.IOException("synthetic failure")
        val actions = actions(this)
        actions.saveEditDraft("tea", PatientDraft(name = "شاي")); advanceUntilIdle()
        assertEquals("tea", state.value.draftSaveIssue?.patientId)
        actions.saveEditDraft("coffee", PatientDraft(name = "قهوة")); advanceUntilIdle()
        assertEquals("tea", state.value.draftSaveIssue?.patientId)
        coEvery { settings.put(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + "tea", any()) } returns Unit
        actions.saveEditDraft("tea", PatientDraft(name = "شاي")); advanceUntilIdle()
        assertNull(state.value.draftSaveIssue)
    }
    @Test fun `switching patients persists each exact draft independently`() = runTest {
        val actions = actions(this)
        val tea = PatientDraft(name = "شاي", treatmentDraft = "دواء 123\n💊")
        val coffee = PatientDraft(name = "قهوة", labDraft = "سطر آخر")
        actions.saveEditDraft("tea", tea); advanceTimeBy(100)
        actions.saveEditDraft("coffee", coffee); advanceUntilIdle()
        coVerify(exactly = 1) { settings.put(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + "tea", Json.encodeToString(PatientDraft.serializer(), tea)) }
        coVerify(exactly = 1) { settings.put(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + "coffee", Json.encodeToString(PatientDraft.serializer(), coffee)) }
    }
    @Test fun `discard cancels queued edit autosave without recreating draft`() = runTest {
        val actions = actions(this)
        actions.saveEditDraft(row.id, PatientDraft(name = "شاي"))
        actions.discardEditDraft(row.id); advanceUntilIdle()
        coVerify(exactly = 0) { settings.put(any(), any()) }
        coVerify(exactly = 1) { settings.delete(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + row.id) }
    }
    @Test fun `successful clinical save drains queued autosave before atomic draft cleanup`() = runTest {
        coEvery { mutations.updatePatientFromForm(row, shift.id) } returns row.copy(revision = 8)
        val actions = actions(this)
        actions.saveEditDraft(row.id, PatientDraft(name = "شاي"))
        var successes = 0
        actions.updatePatient(row, onSuccess = { successes++ }); advanceUntilIdle()
        assertEquals(1, successes)
        coVerify(exactly = 1) { settings.put(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + row.id, any()) }
        coVerifyOrder {
            settings.put(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + row.id, any())
            mutations.updatePatientFromForm(row, shift.id)
        }
    }
    @Test fun `draft load failure cannot replace retained add input`() = runTest {
        val retained = PatientDraft(name = "شاي", labDraft = "مسودة")
        state.value = state.value.copy(patientDraft = retained)
        coEvery { settings.get(AppConstants.SETTING_PATIENT_DRAFT) } throws java.io.IOException("synthetic read failure")
        actions(this).loadDraft(); advanceUntilIdle()
        assertEquals(retained, state.value.patientDraft)
        assertNotNull(state.value.draftSaveIssue)
    }
    @Test fun `cancelled autosave never creates a storage warning`() = runTest {
        coEvery { settings.put(any(), any()) } throws CancellationException("synthetic cancellation")
        actions(this).saveDraft(PatientDraft(name = "شاي")); advanceUntilIdle()
        assertNull(state.value.draftSaveIssue)
    }
    @Test fun `recycle observation distinguishes loading empty error and successful retry`() = runTest {
        val stream = MutableSharedFlow<List<com.hos.rushdpatients.data.model.Patient>>()
        every { patients.observeSoftDeletedForShift(shift.id) } returns stream
        val actions = actions(backgroundScope)
        actions.loadRecycleBin(); runCurrent()
        assertTrue(state.value.recycleBinLoading)
        stream.emit(emptyList()); runCurrent()
        assertFalse(state.value.recycleBinLoading); assertFalse(state.value.recycleBinFailed)
        every { patients.observeSoftDeletedForShift(shift.id) } returns flow { throw java.io.IOException("synthetic read failure") }
        actions.loadRecycleBin(); runCurrent()
        assertTrue(state.value.recycleBinFailed); assertTrue(state.value.deletedPatients.isEmpty())
        every { patients.observeSoftDeletedForShift(shift.id) } returns flowOf(listOf(row))
        actions.loadRecycleBin(); runCurrent()
        assertFalse(state.value.recycleBinFailed); assertEquals(listOf(row), state.value.deletedPatients)
        actions.stopRecycleBinObservation()
    }
    @Test fun `open recycle observation refreshes after restore and purge`() = runTest {
        val stream = MutableStateFlow(listOf(row))
        every { patients.observeSoftDeletedForShift(shift.id) } returns stream
        val actions = actions(backgroundScope)
        actions.loadRecycleBin(); runCurrent()
        assertEquals(listOf(row), state.value.deletedPatients)
        stream.value = emptyList(); runCurrent()
        assertTrue(state.value.deletedPatients.isEmpty()); assertFalse(state.value.recycleBinLoading)
        stream.value = listOf(row.copy(id = "another")); runCurrent()
        assertEquals("another", state.value.deletedPatients.single().id)
        actions.stopRecycleBinObservation()
    }
    @Test fun `dismissed or wrong section recycle results cannot expose stale patients`() = runTest {
        val stream = MutableStateFlow(listOf(row))
        every { patients.observeSoftDeletedForShift(shift.id) } returns stream
        val actions = actions(backgroundScope)
        actions.loadRecycleBin(); runCurrent()
        actions.stopRecycleBinObservation()
        stream.value = listOf(row.copy(id = "later")); runCurrent()
        assertTrue(state.value.deletedPatients.isEmpty())
        actions.loadRecycleBin(); runCurrent()
        state.value = state.value.copy(shift = shift.copy(id = "other"), deletedPatients = emptyList())
        stream.value = listOf(row.copy(id = "wrong-project")); runCurrent()
        assertTrue(state.value.deletedPatients.isEmpty())
        actions.stopRecycleBinObservation()
    }
    @Test fun `missing ward recycle load terminates with retryable failure`() = runTest {
        state.value = state.value.copy(shift = null)
        actions(this).loadRecycleBin()
        assertFalse(state.value.recycleBinLoading); assertTrue(state.value.recycleBinFailed)
        verify(exactly = 0) { patients.observeSoftDeletedForShift(any()) }
    }
    @Test fun `double restore enters repository once and failure releases controls`() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { mutations.restorePatient(row, shift.id) } coAnswers { release.await(); throw java.io.IOException("synthetic restore failure") }
        val actions = actions(this)
        actions.restorePatient(row); actions.restorePatient(row); runCurrent()
        assertTrue(state.value.saving)
        coVerify(exactly = 1) { mutations.restorePatient(row, shift.id) }
        release.complete(Unit); runCurrent()
        assertFalse(state.value.saving); assertNotNull(state.value.snackbar)
        coEvery { mutations.restorePatient(row, shift.id) } returns row
        actions.restorePatient(row); advanceUntilIdle()
        coVerify(exactly = 2) { mutations.restorePatient(row, shift.id) }
    }

    @Test fun `discard waits for in flight autosave before deleting persisted draft`() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { settings.put(any(), any()) } coAnswers { withContext(NonCancellable) { release.await() } }
        val actions = actions(this)
        actions.saveEditDraft(row.id, PatientDraft(name = "شاي")); advanceTimeBy(351)
        actions.discardEditDraft(row.id); runCurrent()
        coVerify(exactly = 0) { settings.delete(any()) }
        release.complete(Unit); advanceUntilIdle()
        coVerifyOrder {
            settings.put(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + row.id, any())
            settings.delete(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + row.id)
        }
    }
    @Test fun `form commit waits for in flight autosave so it cannot recreate obsolete draft`() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { settings.put(any(), any()) } coAnswers { withContext(NonCancellable) { release.await() } }
        coEvery { mutations.updatePatientFromForm(row, shift.id) } returns row
        val actions = actions(this)
        actions.saveEditDraft(row.id, PatientDraft(name = "شاي")); advanceTimeBy(351)
        var saved = false
        actions.updatePatient(row, onSuccess = { saved = true }); runCurrent()
        coVerify(exactly = 0) { mutations.updatePatientFromForm(any(), any()) }
        assertFalse(saved)
        release.complete(Unit); advanceUntilIdle()
        assertTrue(saved)
        coVerifyOrder {
            settings.put(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + row.id, any())
            mutations.updatePatientFromForm(row, shift.id)
        }
    }

    @Test fun `failed form save retains complete latest autosaved add and edit input`() = runTest {
        val persisted = mutableMapOf<String, String>()
        coEvery { settings.put(any(), any()) } answers { persisted[firstArg()] = secondArg(); Unit }
        coEvery { mutations.addPatientFromForm(row, shift.id, null) } throws java.io.IOException("synthetic clinical write failure")
        coEvery { mutations.updatePatientFromForm(row, shift.id) } throws java.io.IOException("synthetic clinical write failure")
        val actions = actions(this)
        val add = PatientDraft(name = "قهوة", labDraft = "latest add input")
        actions.saveDraft(add)
        actions.addPatient(row, onSuccess = { fail("Failed add claimed success") }); advanceUntilIdle()
        assertEquals(add, state.value.patientDraft)
        assertEquals(add, Json.decodeFromString<PatientDraft>(persisted.getValue(AppConstants.SETTING_PATIENT_DRAFT)))
        val edit = PatientDraft(name = "شاي", labDraft = "latest edit input")
        actions.saveEditDraft(row.id, edit)
        actions.updatePatient(row, onSuccess = { fail("Failed edit claimed success") }); advanceUntilIdle()
        assertEquals(edit, Json.decodeFromString<PatientDraft>(persisted.getValue(AppConstants.SETTING_PATIENT_EDIT_DRAFT_PREFIX + row.id)))
        assertFalse(state.value.saving)
        coVerify(exactly = 0) { settings.delete(any()) }
    }

}
