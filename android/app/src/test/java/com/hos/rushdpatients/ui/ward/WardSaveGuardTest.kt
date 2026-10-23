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
    private val guard = WardSaveGuard(text, state, shifts)
    @Test fun `second save during active mutation cannot execute or claim success`() = runBlocking<Unit> {
        guard.saveAction(onSuccess = {}) {
            guard.saveAction(onSuccess = { fail("Duplicate success") }) { fail("Duplicate mutation") }
            assertTrue(state.value.saving)
        }
        assertFalse(state.value.saving)
    }
    @Test fun `missing and archived shifts cannot be edited`() {
        assertNull(guard.editableShiftId())
        state.value = WardUiState(shift = Fixtures.shift(date = ShiftDate.current().minusDays(1)))
        assertNull(guard.editableShiftId())
        assertNotNull(state.value.snackbar)
        state.value = WardUiState(shift = Fixtures.shift(date = ShiftDate.current()))
        assertEquals("shift-1", guard.editableShiftId())
    }
    @Test fun `editable shift is rechecked against repository`() = runBlocking<Unit> {
        coEvery { shifts.getById("shift-1") } returns Fixtures.shift(date = ShiftDate.current().minusDays(1))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { guard.requireEditableShift("shift-1") } }
        coEvery { shifts.getById("shift-1") } returns Fixtures.shift(date = ShiftDate.current())
        guard.requireEditableShift("shift-1")
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
    @Test fun `handled stale edit leaves conflict review in control`() = runBlocking<Unit> {
        var reviewed = false
        guard.saveAction(onSuccess = { fail("Unexpected success") }, onStale = {
            reviewed = true; assertFalse(state.value.saving); true
        }) { error("stale") }
        assertTrue(reviewed)
        assertNull(state.value.snackbar)
    }
    @Test fun `conflict reload failure is shown and busy state cleared`() = runBlocking<Unit> {
        guard.saveAction(onSuccess = { fail("Unexpected success") }, onStale = { error("reload failed") }) {
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
    @Test fun `conflict reload cancellation propagates`() = runBlocking<Unit> {
        assertThrows(CancellationException::class.java) { runBlocking {
            guard.saveAction(onSuccess = { fail("Unexpected success") },
                onStale = { throw CancellationException("reload cancelled") }) { error("stale") }
        } }
        assertFalse(state.value.saving)
        assertNull(state.value.snackbar)
    }
    @Test fun `clinical validation rejects incomplete data while ownership remains optional`() = runBlocking<Unit> {
        assertFalse(guard.validateForSave(Fixtures.patient(name = "")))
        state.value = WardUiState()
        assertTrue(guard.validateForSave(Fixtures.patient()))
    }
    @Test fun `responsible assignments require available doctors in matching roles`() = runBlocking<Unit> {
        val resident = Fixtures.doctor(id = "resident").copy(clinicalRole = ClinicalRole.RESIDENT)
        val supervisor = Fixtures.doctor(id = "supervisor").copy(clinicalRole = ClinicalRole.SUPERVISOR)
        state.value = WardUiState(doctors = listOf(resident, supervisor))
        assertTrue(guard.validateForSave(Fixtures.patient(responsibleResidentId = resident.id,
            responsibleSpecialistId = supervisor.id)))
        assertFalse(guard.validateForSave(Fixtures.patient(responsibleResidentId = supervisor.id)))
        assertFalse(guard.validateForSave(Fixtures.patient(responsibleSpecialistId = resident.id)))
        assertFalse(guard.validateForSave(Fixtures.patient(responsibleResidentId = "missing")))
    }
}
