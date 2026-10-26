package com.hos.rushdpatients.ui.demo

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.hos.rushdpatients.domain.sort.SortSpec
import com.hos.rushdpatients.pdf.PdfReportExporter
import com.hos.rushdpatients.testutil.Fixtures
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.ui.ward.PatientDraft
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoWardViewModelTest {
    private val store = ViewModelStore()
    private val repository = InMemoryDemoWardRepository()
    private val text = mockk<UiStrings> { every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية" }
    private val context = mockk<Context>()
    private val pdf = mockk<PdfReportExporter>()
    private lateinit var vm: DemoWardViewModel

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = DemoWardViewModel(text, context, repository, pdf)
        store.put("demo", vm)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun patient() = vm.state.value.patients.first()
    private fun saved(id: String) = vm.state.value.patients.single { it.id == id }

    @Test fun `save stamps actor and advances patient and shift revisions`() {
        val before = patient()
        val revision = vm.state.value.revision
        val actor = vm.state.value.actor
        assertTrue(vm.save(before.copy(labs = "نتيجة معدلة")))
        val after = saved(before.id)
        assertEquals("نتيجة معدلة", after.labs)
        assertEquals(before.revision + 1, after.revision)
        assertEquals(revision + 1, vm.state.value.revision)
        assertEquals(actor.id, after.lastEditedByDoctorId)
        assertEquals(actor.fullName, after.lastEditedByName)
        assertEquals(before.sortOrder, after.sortOrder)
    }

    @Test fun `stale edit returns live record and leaves entire state unchanged`() {
        val old = patient()
        vm.save(old.copy(labs = "محفوظ"))
        val snapshot = vm.state.value
        var stale: com.hos.rushdpatients.data.model.Patient? = null
        assertFalse(vm.save(old.copy(labs = "قديم")) { stale = it })
        assertEquals(saved(old.id), stale)
        assertEquals(snapshot, vm.state.value)
    }

    @Test fun `editing recycled patient cannot silently resurrect it`() {
        val old = patient()
        vm.controller.deletePatient(old) { }
        val snapshot = vm.state.value
        var called = false
        assertFalse(vm.save(old) { called = true; assertNull(it) })
        assertTrue(called)
        assertEquals(snapshot, vm.state.value)
    }

    @Test fun `new patient receives independent revision and final stored position`() {
        val size = vm.state.value.patients.size
        val fresh = Fixtures.patient(id = "new-demo").copy(revision = 99, sortOrder = 999)
        assertTrue(vm.save(fresh))
        assertEquals(1L, saved(fresh.id).revision)
        assertEquals(size + 1, saved(fresh.id).sortOrder)
    }

    @Test fun `task completion ignores submitted attribution and uses current actor`() {
        val row = patient().copy(tasks = listOf(Fixtures.task("new-task").copy(done = true,
            completedByDoctorId = "forged", completedByName = "forged", completedAtEpochMillis = 1)))
        assertTrue(vm.save(row))
        val task = saved(row.id).tasks.single()
        assertEquals(vm.state.value.actor.id, task.completedByDoctorId)
        assertEquals(vm.state.value.actor.fullName, task.completedByName)
        assertTrue(requireNotNull(task.completedAtEpochMillis) > 1)
    }

    @Test fun `editing completed task under another actor preserves original completion`() {
        val row = patient().copy(tasks = listOf(Fixtures.task("new-task").copy(done = true)))
        vm.save(row)
        val first = saved(row.id)
        val completion = first.tasks.single()
        vm.selectActor(vm.state.value.doctors.first { it.id != vm.state.value.actorId }.id)
        vm.save(first.copy(tasks = listOf(completion.copy(description = "وصف معدل"))))
        val after = saved(row.id).tasks.single()
        assertEquals(completion.completedByDoctorId, after.completedByDoctorId)
        assertEquals(completion.completedByName, after.completedByName)
        assertEquals(completion.completedAtEpochMillis, after.completedAtEpochMillis)
    }

    @Test fun `reopening task clears all completion attribution`() {
        val row = patient().copy(tasks = listOf(Fixtures.task("new-task").copy(done = true)))
        vm.save(row)
        vm.controller.setTaskDone(saved(row.id), "new-task", false) { }
        val task = saved(row.id).tasks.single()
        assertFalse(task.done)
        assertNull(task.completedByDoctorId)
        assertNull(task.completedByName)
        assertNull(task.completedAtEpochMillis)
    }

    @Test fun `completion board callback receives stamped saved revision`() {
        val row = patient().copy(tasks = listOf(Fixtures.task("new-task")))
        vm.save(row)
        val before = saved(row.id)
        var called = false
        vm.controller.setTaskDone(before, "new-task", true) { after ->
            called = true
            assertEquals(saved(row.id), after)
            assertEquals(before.revision + 1, after.revision)
            assertEquals(vm.state.value.actorId, after.tasks.single().completedByDoctorId)
        }
        assertTrue(called)
    }

    @Test fun `missing and unchanged task actions do not save or call success`() {
        val row = patient().copy(tasks = listOf(Fixtures.task("new-task")))
        vm.save(row)
        val snapshot = vm.state.value
        vm.controller.setTaskDone(saved(row.id), "absent", true) { fail("Unexpected success") }
        vm.controller.setTaskDone(saved(row.id), "new-task", false) { fail("Unexpected success") }
        assertEquals(snapshot, vm.state.value)
    }

    @Test fun `legacy task owner text is preserved without resolving active ownership`() {
        val owner = vm.state.value.doctors.last()
        val row = patient().copy(tasks = listOf(Fixtures.task().copy(ownerDoctorId = owner.id, ownerName = "قديم")))
        vm.save(row)
        assertEquals("قديم", saved(row.id).tasks.single().ownerName)
    }

    @Test fun `invalid task rejects save without changing state`() {
        val snapshot = vm.state.value
        assertThrows(IllegalArgumentException::class.java) {
            vm.save(patient().copy(tasks = listOf(Fixtures.task().copy(description = " "))))
        }
        assertEquals(snapshot, vm.state.value)
    }

    @Test fun `duplicate add never overwrites saved clinical content`() {
        val row = patient()
        vm.controller.addPatient(row.copy(labs = "overwrite"), null, null, null) { fail("Unexpected success") }
        assertEquals(row, saved(row.id))
    }

    @Test fun `add rejects stale shift revision and patient snapshot`() {
        val snapshot = vm.state.value
        val fresh = Fixtures.patient(id = "new-demo")
        vm.controller.addPatient(fresh, null, snapshot.patients, snapshot.revision + 1) { fail("Unexpected success") }
        vm.controller.addPatient(fresh, null, emptyList(), snapshot.revision) { fail("Unexpected success") }
        assertEquals(snapshot.patients, vm.state.value.patients)
        assertEquals(snapshot.revision, vm.state.value.revision)
    }

    @Test fun `positioned add stores consecutive positions and clears saved draft`() {
        vm.controller.saveDraft(PatientDraft(name = "مسودة"))
        val before = vm.state.value
        val fresh = Fixtures.patient(id = "new-demo")
        var called = false
        vm.controller.addPatient(fresh, 2, before.patients, before.revision) { called = true }
        assertTrue(called)
        assertEquals(fresh.id, vm.state.value.ordered[1].id)
        assertEquals((1..vm.state.value.patients.size).toList(), vm.state.value.ordered.map { it.sortOrder })
        assertEquals(SortSpec.EMPTY, vm.state.value.sort)
        assertNull(vm.state.value.draft)
    }

    @Test fun `stale delete preserves live row and never invokes success`() {
        val old = patient()
        vm.save(old.copy(labs = "جديد"))
        val before = saved(old.id)
        vm.controller.deletePatient(old) { fail("Unexpected success") }
        assertEquals(before, saved(old.id))
        assertTrue(vm.state.value.deleted.isEmpty())
    }

    @Test fun `delete restore cycle retains clinical content without duplicate restoration`() {
        val row = patient()
        vm.controller.deletePatient(row) { assertEquals(row, it) }
        assertTrue(vm.state.value.patients.none { it.id == row.id })
        assertEquals(listOf(row), vm.state.value.deleted)
        vm.controller.restorePatient(row)
        val restored = vm.state.value
        assertEquals(row, saved(row.id))
        assertTrue(restored.deleted.isEmpty())
        vm.controller.restorePatient(row)
        assertEquals(restored, vm.state.value)
    }

    @Test fun `sort rejects stale revision before changing stored order`() {
        val before = vm.state.value
        var stale = false
        vm.controller.applySort(SortSpec.DEFAULT, before.revision + 1, {
            stale = true; assertEquals(before.revision, it?.revision)
        }) { fail("Unexpected success") }
        assertTrue(stale)
        assertEquals(before, vm.state.value)
    }

    @Test fun `accepted sort persists displayed order and advances shift revision`() {
        val before = vm.state.value
        var called = false
        vm.controller.applySort(SortSpec.DEFAULT, before.revision, { fail("Unexpected stale") }) { called = true }
        assertTrue(called)
        assertEquals(before.revision + 1, vm.state.value.revision)
        assertEquals(vm.state.value.ordered.map { it.id }, vm.state.value.patients.map { it.id })
        assertEquals((1..before.patients.size).toList(), vm.state.value.patients.map { it.sortOrder })
    }

    @Test fun `invalid roster selections preserve existing roster and revision`() {
        val before = vm.state.value
        listOf(emptyList(), listOf("absent"), listOf(before.actorId, before.actorId)).forEach(vm::selectDoctors)
        assertEquals(before, vm.state.value)
    }

    @Test fun `valid roster selection retains supplied order and increments revision`() {
        val before = vm.state.value
        val ids = before.doctors.reversed().map { it.id }
        vm.selectDoctors(ids)
        assertEquals(ids, vm.state.value.shiftDoctorIds)
        assertEquals(ids, vm.state.value.shiftDoctors.map { it.id })
        assertEquals(before.revision + 1, vm.state.value.revision)
    }

    @Test fun `invalid actor selection leaves state untouched`() {
        val before = vm.state.value
        vm.selectActor("absent")
        assertEquals(before, vm.state.value)
    }

    @Test fun `reset discards edits recycle draft and editor without affecting another repository`() {
        val other = InMemoryDemoWardRepository()
        val independent = other.state.value
        val originalShift = vm.state.value.shiftId
        vm.controller.deletePatient(patient()) { }
        vm.controller.saveDraft(PatientDraft(name = "مسودة"))
        vm.editor.openAdd(emptyList(), 0)
        vm.setSaveAndNext(true)
        vm.reset()
        assertNotEquals(originalShift, vm.state.value.shiftId)
        assertTrue(vm.state.value.deleted.isEmpty())
        assertNull(vm.state.value.draft)
        assertNull(vm.editor.dialog)
        assertFalse(vm.state.value.saveAndNext)
        assertEquals(0L, vm.state.value.revision)
        assertEquals(independent, other.state.value)
    }

    @Test fun `session end clears transient workspace and never invokes external dependencies`() {
        vm.message("مسودة")
        vm.editor.editTarget = patient()
        vm.endSession()
        assertNull(vm.editor.dialog)
        assertNull(vm.state.value.message)
        verify { context wasNot Called; pdf wasNot Called }
    }

    @Test fun `remote actions remain local notices without changing clinical snapshot`() {
        val before = vm.state.value
        vm.controller.bringLatestData()
        vm.controller.bringPreviousData()
        vm.controller.applyRollover(true)
        vm.controller.retryLastAction()
        assertEquals(before.patients, vm.state.value.patients)
        assertEquals(before.revision, vm.state.value.revision)
        assertFalse(vm.controller.liveProject)
        assertFalse(vm.controller.online.value)
        assertNotNull(vm.state.value.message)
        verify { context wasNot Called; pdf wasNot Called }
    }
}
