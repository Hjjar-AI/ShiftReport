package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.testutil.Fixtures
import org.junit.Assert.*
import org.junit.Test

class WardEditorStateTest {
    private val patient = Fixtures.patient().copy(revision = 7)

    @Test fun `add captures insertion snapshot independently of caller list`() {
        val rows = mutableListOf(patient)
        val state = WardEditorState()
        state.openAdd(rows)
        rows.clear()
        assertEquals(listOf(patient), state.insertionPatients)
    }

    @Test fun `reopening add preserves original revision position and unsaved draft`() {
        val state = WardEditorState()
        state.openAdd(listOf(patient))
        val form = state.addForm.obtain(null, null)
        form.labText = "نتيجة لم تحفظ"
        state.insertionPosition = "2"
        state.openAdd(emptyList())
        assertSame(form, state.addForm.obtain(null, null))
        assertEquals("نتيجة لم تحفظ", form.labText)
        assertEquals("2", state.insertionPosition)
        assertEquals(listOf(patient), state.insertionPatients)
    }

    @Test fun `reopening equal edit preserves loaded draft and unsaved fields`() {
        val state = WardEditorState()
        state.editTarget = patient
        val session = requireNotNull(state.editSession)
        session.form.obtain(patient, null).labText = "مسودة"
        session.draftLoaded = true
        state.editTarget = patient.copy()
        assertSame(session, state.editSession)
        assertTrue(requireNotNull(state.editSession).draftLoaded)
        assertEquals("مسودة", session.form.obtain(patient, null).labText)
    }

    @Test fun `explicitly opening changed revision creates a fresh edit session`() {
        val state = WardEditorState()
        state.editTarget = patient
        val old = state.editSession
        val fresh = patient.copy(revision = 8, labs = "نتيجة جديدة")
        state.editTarget = fresh
        assertNotSame(old, state.editSession)
        assertEquals(fresh, state.editSession?.initial)
        assertFalse(requireNotNull(state.editSession).draftLoaded)
    }

    @Test fun `switching dialog types leaves exactly one clinical session`() {
        val state = WardEditorState()
        state.openAdd(listOf(patient))
        state.editTarget = patient
        assertFalse(state.showAdd)
        state.copyTarget = patient
        assertNull(state.editTarget)
        assertNull(state.editSession)
        state.deleteTarget = patient
        assertNull(state.copyTarget)
        assertNull(state.copyForm)
        assertEquals(patient, state.deleteTarget)
        state.openAdd(listOf(patient))
        assertNull(state.deleteTarget)
        assertTrue(state.showAdd)
    }

    @Test fun `late dismiss callbacks cannot close another dialog type`() {
        val state = WardEditorState()
        state.editTarget = patient
        val edit = state.dialog
        state.showAdd = false
        state.copyTarget = null
        state.deleteTarget = null
        assertSame(edit, state.dialog)
        state.copyTarget = patient
        val copy = state.dialog
        state.editTarget = null
        state.showAdd = false
        assertSame(copy, state.dialog)
    }

    @Test fun `equal copy target preserves generated identity and draft`() {
        val state = WardEditorState()
        state.copyTarget = patient
        val initial = state.copyInitial
        val form = requireNotNull(state.copyForm).obtain(initial, null)
        form.name = "قهوة"
        state.copyTarget = patient.copy()
        assertSame(initial, state.copyInitial)
        assertSame(form, requireNotNull(state.copyForm).obtain(state.copyInitial, null))
        assertEquals("قهوة", form.name)
    }

    @Test fun `copy clears admission tasks priority and editor attribution`() {
        val source = patient.copy(tasks = listOf(Fixtures.task()), isPriority = true,
            lastEditedByDoctorId = "doc-1", lastEditedByName = "أرز", deletedAt = Fixtures.timestamp)
        val state = WardEditorState()
        state.copyTarget = source
        val copy = requireNotNull(state.copyInitial)
        assertNotEquals(source.id, copy.id)
        assertEquals("", copy.admittanceNumber)
        assertNull(copy.admittanceDate)
        assertTrue(copy.tasks.isEmpty())
        assertFalse(copy.isPriority)
        assertNull(copy.lastEditedByDoctorId)
        assertNull(copy.lastEditedByName)
        assertNull(copy.deletedAt)
        assertEquals(0, copy.sortOrder)
        assertEquals(source.labs, copy.labs)
        assertEquals(source.treatmentPlan, copy.treatmentPlan)
        assertEquals(listOf(Fixtures.task()), source.tasks)
    }

    @Test fun `closing and reopening copy creates independent patient identity`() {
        val state = WardEditorState()
        state.copyTarget = patient
        val id = state.copyInitial?.id
        state.copyTarget = null
        state.copyTarget = patient
        assertNotEquals(id, state.copyInitial?.id)
    }

    @Test fun `clear discards draft position and snapshot before next add`() {
        val state = WardEditorState()
        state.openAdd(listOf(patient))
        state.insertionPosition = "3"
        val form = state.addForm.obtain(null, null)
        form.name = "مسودة"
        state.clear()
        assertNull(state.dialog)
        state.openAdd(emptyList())
        assertEquals("", state.insertionPosition)
        assertTrue(state.insertionPatients.isEmpty())
        assertNotSame(form, state.addForm.obtain(null, null))
        assertEquals("", state.addForm.obtain(null, null).name)
    }
}
