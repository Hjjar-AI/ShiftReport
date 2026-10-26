package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.ClinicalRole
import com.hos.rushdpatients.data.repository.ShiftRepository
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.util.ShiftDate
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WardSaveGuardTest {
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val shifts = mockk<ShiftRepository>()
    private val state = MutableStateFlow(WardUiState())
    private val guard = WardSaveGuard(text, state)
    @Test fun `second save during active mutation cannot execute or claim success`() = runBlocking<Unit> {
        guard.saveAction(onSuccess = {}) {
            guard.saveAction(onSuccess = { fail("Duplicate success") }) { fail("Duplicate mutation") }
            assertTrue(state.value.saving)
        }
        assertFalse(state.value.saving)
    }
    @Test fun `stored section remains editable across calendar days`() {
        assertNull(guard.editableShiftId())
        state.value = WardUiState(loading = false, shift = Fixtures.shift(date = ShiftDate.current().minusDays(1)))
        assertEquals("shift-1", guard.editableShiftId())
        state.value = state.value.copy(shift = null)
        assertNull(guard.editableShiftId())
    }
    @Test fun `local loading and recovery errors block editing`() = runBlocking<Unit> {
        state.value = WardUiState(loading = true, shift = Fixtures.shift())
        assertNull(guard.editableShiftId())
        state.value = state.value.copy(loading = false, error = "synthetic reload failure")
        assertNull(guard.editableShiftId())
        assertNotNull(state.value.snackbar)
    }
    @Test fun `save exposes busy state and clears it before success`() = runBlocking<Unit> {
        var succeeded = false
        guard.saveAction(onSuccess = { assertFalse(state.value.saving); succeeded = true }) {
            assertTrue(state.value.saving)
        }
        assertTrue(succeeded)
        assertFalse(state.value.saving)
    }
    @Test fun `failed saves retain draft and show error without success`() = runBlocking<Unit> {
        val draft = PatientDraft(name = "شاي", labDraft = "نتيجة")
        state.value = WardUiState(patientDraft = draft)
        guard.saveAction(onSuccess = { fail("Unexpected success") }) { error("save failed") }
        assertEquals("save failed", state.value.snackbar)
        assertEquals(draft, state.value.patientDraft)
        assertFalse(state.value.saving)
    }
    @Test fun `handled unavailable patient keeps caller recovery in control`() = runBlocking<Unit> {
        var reviewed = false
        guard.saveAction(onSuccess = { fail("Unexpected success") }, onUnavailable = {
            reviewed = true; assertFalse(state.value.saving); true
        }) { error("stale") }
        assertTrue(reviewed)
        assertNull(state.value.snackbar)
    }
    @Test fun `unavailable patient reload failure is shown and busy state cleared`() = runBlocking<Unit> {
        guard.saveAction(onSuccess = { fail("Unexpected success") }, onUnavailable = { error("reload failed") }) {
            error("stale")
        }
        assertEquals("reload failed", state.value.snackbar)
        assertFalse(state.value.saving)
    }
    @Test fun `save cancellation propagates without snackbar or success`() = runBlocking<Unit> {
        val cancelled = CancellationException("cancelled")
        val caught = assertThrows(CancellationException::class.java) { runBlocking {
            guard.saveAction(onSuccess = { fail("Unexpected success") }) { throw cancelled }
        } }
        assertSame(cancelled, caught)
        assertFalse(state.value.saving)
        assertNull(state.value.snackbar)
    }
    @Test fun `unavailable patient reload cancellation propagates`() = runBlocking<Unit> {
        assertThrows(CancellationException::class.java) { runBlocking {
            guard.saveAction(onSuccess = { fail("Unexpected success") },
                onUnavailable = { throw CancellationException("reload cancelled") }) { error("stale") }
        } }
        assertFalse(state.value.saving)
        assertNull(state.value.snackbar)
    }
    @Test fun `optional profiles are accepted but malformed task text is rejected`() = runBlocking<Unit> {
        assertTrue(guard.validateForSave(Fixtures.patient(name = "")))
        assertFalse(guard.validateForSave(Fixtures.patient().copy(tasks = listOf(Fixtures.task().copy(description = " ")))))
        assertTrue(guard.validateForSave(Fixtures.patient()))
    }
    @Test fun `form guard rejects duplicate team selection while repository owns live role checks`() = runBlocking<Unit> {
        assertFalse(guard.validateForSave(Fixtures.patient(responsibleResidentId = "same", responsibleSpecialistId = "same")))
        assertTrue(guard.validateForSave(Fixtures.patient(responsibleResidentId = "resident", responsibleSpecialistId = "supervisor")))
        // Current doctor existence/roles are enforced transactionally by WardMutationRepository.
        assertTrue(guard.validateForSave(Fixtures.patient(responsibleResidentId = "missing")))
    }
    @Test fun `stored section date does not impose a clinical save deadline`() = runBlocking<Unit> {
        val before = java.time.LocalDate.of(2026, 10, 22)
        state.value = WardUiState(loading = false, shift = Fixtures.shift(date = before))
        var mutated = false
        var succeeded = false
        guard.saveAction(onSuccess = { succeeded = true }) {
            assertEquals("shift-1", guard.editableShiftId())
            mutated = true
        }
        assertTrue(mutated)
        assertTrue(succeeded)
        assertFalse(state.value.saving)
        assertNull(state.value.snackbar)
    }

}
