package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.data.model.*
import com.hos.rushdpatients.testutil.Fixtures
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PatientLabGroupFormTest {
    @Test fun `draft recovery retains unfinished empty groups dates and manual order`() {
        val groups = Fixtures.labGroups().reversed() + PatientLabGroup("unfinished", title = "عنوان لم يكتمل") + PatientLabGroup("blank")
        val draft = PatientDraft(labGroups = groups)
        assertFalse(draft.isEmpty)
        val restored = Json.decodeFromString<PatientDraft>(Json.encodeToString(draft))
        assertEquals(groups, PatientFormState(null, restored).labGroups)
        assertEquals(groups, restored.labGroups)
    }
    @Test fun `explicitly cleared draft groups override any retained flat draft text`() {
        val draft = PatientDraft(labGroups = emptyList(), labDraft = "previous result", labItems = listOf("previous panel"))
        assertTrue(PatientFormState(null, draft).labGroups.isEmpty())
        val unstructured = PatientFormState(null, draft.copy(labGroups = null)).labGroups.single()
        assertEquals("previous panel\nprevious result", unstructured.results)
        assertEquals("", unstructured.title)
        assertNull(unstructured.date)
    }
    @Test fun `retained editor does not reset newly reordered or dated groups on draft callbacks`() {
        val store = PatientFormStateStore()
        val initial = Fixtures.patientWithLabs()
        val form = store.obtain(initial, null)
        val changed = form.labGroups.reversed().map { it.copy(date = "2026-11-02") }
        form.labGroups = changed
        val again = store.obtain(initial.copy(), PatientDraft(labGroups = Fixtures.labGroups()))
        assertSame(form, again)
        assertEquals(changed, again.labGroups)
        store.restore(initial, PatientDraft(labGroups = changed + PatientLabGroup("pending")))
        assertEquals(changed + PatientLabGroup("pending"), store.obtain(initial, null).labGroups)
    }
    private fun save(initial: Patient, groups: List<PatientLabGroup>) = patientForSubmission(initial,
        initial.admittanceNumber, initial.admittanceDate, initial.gender, initial.name, initial.birthDate,
        initial.hasCompanion, initial.diagnosisType, initial.initialDiagnosis, initial.treatmentPlan,
        initial.followUp, PatientLabGroups.text(groups), initial.responsibleResidentId,
        initial.responsibleSpecialistId, initial.badges, initial.tasks, initial.isPriority, groups)

    @Test fun `saving only lab dates keeps patient identity assignment day and bookkeeping`() {
        val initial = Fixtures.patientWithLabs().copy(revision = 14, assignmentChangedOn = LocalDate.of(2026, 1, 1),
            lastEditedByDoctorId = "cedar", lastEditedByName = "أرز", tasks = listOf(Fixtures.task()), sortOrder = 8)
        val groups = initial.labGroups.map { it.copy(date = "2026-11-02") }
        assertEquals(initial.copy(labGroups = groups, labs = PatientLabGroups.text(groups)), save(initial, groups))
    }
    @Test fun `deleting every group does not revive the prior saved results`() {
        val saved = save(Fixtures.patientWithLabs(), emptyList())
        assertTrue(saved.labGroups.isEmpty())
        assertTrue(saved.effectiveLabGroups.isEmpty())
        assertEquals("", saved.labs)
        assertEquals("", saved.labText)
    }
    @Test fun `results retain leading trailing and blank lines through explicit save`() {
        val groups = listOf(PatientLabGroup("whitespace", results = "  Na = 140\r\n\nK = 4.1  "))
        val saved = save(Fixtures.patientWithLabs(), groups)
        assertEquals(groups.single().results, saved.labs)
        assertEquals(groups, PatientFormState(saved, null).labGroups)
    }
}
