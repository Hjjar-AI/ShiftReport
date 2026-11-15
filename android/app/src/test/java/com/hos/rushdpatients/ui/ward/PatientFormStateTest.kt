package com.hos.rushdpatients.ui.ward

import com.hos.rushdpatients.testutil.Fixtures
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PatientFormStateTest {
    @Test fun `add draft updates do not reset unsaved fields or tasks`() {
        val store = PatientFormStateStore()
        val form = store.obtain(null, PatientDraft(name = "شاي"))
        form.name = "قهوة"
        form.labGroups = listOf(com.hos.rushdpatients.data.model.PatientLabGroup("pending", results = "نتيجة لم تحفظ"))
        form.tasks = listOf(Fixtures.task())
        val next = store.obtain(null, PatientDraft(name = "شاي", labDraft = "old"))
        assertSame(form, next)
        assertEquals("قهوة", next.name)
        assertEquals("نتيجة لم تحفظ", com.hos.rushdpatients.data.model.PatientLabGroups.text(next.labGroups))
        assertEquals(form.tasks, next.tasks)
    }
    @Test fun `reviewing a new revision explicitly resets the editor seed`() {
        val patient = Fixtures.patient().copy(revision = 1)
        val store = PatientFormStateStore()
        val original = store.obtain(patient, null)
        original.name = "unsaved"
        assertSame(original, store.obtain(patient.copy(), null))
        val refreshed = store.obtain(patient.copy(revision = 2, name = "قهوة"), null)
        assertNotSame(original, refreshed)
        assertEquals("قهوة", refreshed.name)
    }
    @Test fun `clear discards the previous editor state`() {
        val store = PatientFormStateStore()
        val first = store.obtain(null, PatientDraft(name = "شاي"))
        store.clear()
        val second = store.obtain(null, PatientDraft(name = "قهوة"))
        assertNotSame(first, second)
        assertEquals("قهوة", second.name)
    }
    @Test fun `draft round trip retains structured tasks badges and unfinished labs`() {
        val draft = PatientDraft(name = "شاي", treatmentItems = listOf("خطة"), treatmentDraft = "مسودة",
            followUpItems = listOf("متابعة"), followUpDraft = "مسودة متابعة",
            labItems = listOf("نتيجة"), labDraft = "مسودة تحاليل", tasks = listOf(Fixtures.task()),
            badges = listOf(PatientBadgeDraft("تنبيه", "HIGH")), badgeDraftText = "تنبيه آخر",
            residentId = "resident", specialistId = "supervisor", isPriority = true)
        assertEquals(draft, Json.decodeFromString<PatientDraft>(Json.encodeToString(draft)))
        val state = PatientFormState(null, draft)
        assertEquals(draft.tasks, state.tasks)
        assertEquals("نتيجة\nمسودة تحاليل", com.hos.rushdpatients.data.model.PatientLabGroups.text(state.labGroups))
        assertEquals("خطة\nمسودة", state.treatmentText)
        assertEquals("متابعة\nمسودة متابعة", state.followUpText)
        assertEquals(draft.badgeDraftText, state.badgeDraftText)
        assertTrue(state.isPriority)
    }
    @Test fun `any saved or unfinished clinical content makes draft nonempty`() {
        assertTrue(PatientDraft().isEmpty)
        listOf(PatientDraft(name = "شاي"), PatientDraft(labDraft = "نتيجة"),
            PatientDraft(labItems = listOf("نتيجة")), PatientDraft(treatmentDraft = "خطة"),
            PatientDraft(followUpDraft = "متابعة"), PatientDraft(tasks = listOf(Fixtures.task())),
            PatientDraft(badgeDraftText = "تنبيه"), PatientDraft(badges = listOf(PatientBadgeDraft("تنبيه"))))
            .forEach { assertFalse(it.isEmpty) }
    }
    @Test fun emptyAddFormStartsWithOptionalSectionCollapsed() {
        assertFalse(PatientFormState(null, null).extrasExpanded)
    }
    @Test fun clinicalTextAloneDoesNotExpandOptionalSection() {
        assertFalse(PatientFormState(Fixtures.patient(labs = "نتيجة"), null).extrasExpanded)
    }
    @Test fun pendingTaskExpandsOptionalSectionOnEdit() {
        val task = Fixtures.task()
        val form = PatientFormState(Fixtures.patient().copy(tasks = listOf(task)), null)
        assertTrue(form.extrasExpanded)
        assertEquals(listOf(task), form.tasks)
    }
    @Test fun completedTaskExpandsWithoutLosingStoredCompletionMetadata() {
        val task = Fixtures.task(done = true)
        val form = PatientFormState(Fixtures.patient().copy(tasks = listOf(task)), null)
        assertTrue(form.extrasExpanded)
        assertEquals(task, form.tasks.single())
    }
    @Test fun badgeWithoutSeverityStillExpandsOptionalSection() {
        val badge = com.hos.rushdpatients.data.model.PatientBadge("مرافق")
        val form = PatientFormState(Fixtures.patient().copy(badges = listOf(badge)), null)
        assertTrue(form.extrasExpanded)
        assertEquals(listOf(badge), form.badges)
    }
    @Test fun priorityWithoutTasksOrBadgesExpandsOptionalSection() {
        val form = PatientFormState(Fixtures.patient().copy(isPriority = true), null)
        assertTrue(form.extrasExpanded)
        assertTrue(form.isPriority)
    }
    @Test fun unfinishedRestoredBadgeTextExpandsOptionalSection() {
        val form = PatientFormState(null, PatientDraft(badgeDraftText = "تنبيه غير محفوظ"))
        assertTrue(form.extrasExpanded)
        assertEquals("تنبيه غير محفوظ", form.badgeDraftText)
    }
    @Test fun blankRestoredBadgeEntryDoesNotExpandOptionalSection() {
        assertFalse(PatientFormState(null, PatientDraft(badgeDraftText = " \n\t")).extrasExpanded)
    }
    @Test fun restoredClinicalOnlyDraftKeepsOptionalSectionCollapsed() {
        val form = PatientFormState(null, PatientDraft(name = "شاي", treatmentDraft = "خطة", labDraft = "نتيجة"))
        assertFalse(form.extrasExpanded)
        assertEquals("خطة", form.treatmentText)
        assertEquals("نتيجة", com.hos.rushdpatients.data.model.PatientLabGroups.text(form.labGroups))
    }
    @Test fun restoredPriorityStartsExpanded() {
        assertTrue(PatientFormState(null, PatientDraft(isPriority = true)).extrasExpanded)
    }
    @Test fun restoredCompletedTasksStartExpanded() {
        val task = Fixtures.task(done = true)
        val form = PatientFormState(null, PatientDraft(tasks = listOf(task)))
        assertTrue(form.extrasExpanded)
        assertEquals(listOf(task), form.tasks)
    }
    @Test fun collapsingOptionalSectionRetainsUnfinishedTaskAndBadge() {
        val form = PatientFormState(null, PatientDraft(badgeDraftText = "ملاحظة"))
        val pending = Fixtures.task().copy(description = "مهمة لم تضف")
        form.taskEntry = pending
        form.extrasExpanded = false
        assertEquals(pending, form.taskEntry)
        assertEquals("ملاحظة", form.badgeDraftText)
        form.extrasExpanded = true
        assertEquals(pending, form.taskEntry)
    }
    @Test fun draftCallbacksDoNotResetManualExpansionOrPendingTask() {
        val store = PatientFormStateStore()
        val form = store.obtain(null, PatientDraft())
        form.extrasExpanded = true
        form.taskEntry = Fixtures.task()
        val updated = store.obtain(null, PatientDraft(name = "مسودة أحدث"))
        assertSame(form, updated)
        assertTrue(updated.extrasExpanded)
        assertEquals(form.taskEntry, updated.taskEntry)
    }
    @Test fun explicitlyRestoredEditDraftRetainsItsFieldsAndExpansion() {
        val patient = Fixtures.patient()
        val store = PatientFormStateStore()
        store.obtain(patient, null)
        val draft = PatientDraft(name = "قهوة", tasks = listOf(Fixtures.task()), badgeDraftText = "تنبيه")
        store.restore(patient, draft)
        val restored = store.obtain(patient, null)
        assertEquals("قهوة", restored.name)
        assertEquals(draft.tasks, restored.tasks)
        assertEquals("تنبيه", restored.badgeDraftText)
        assertTrue(restored.extrasExpanded)
    }
    @Test fun movingToAnotherPatientDoesNotCarryUnfinishedTask() {
        val store = PatientFormStateStore()
        val first = Fixtures.patient()
        store.obtain(first, null).taskEntry = Fixtures.task()
        val next = store.obtain(first.copy(id = "other", name = "قهوة"), null)
        assertNull(next.taskEntry)
        assertEquals("قهوة", next.name)
    }
    @Test fun invalidRestoredAdmissionDateStaysMissing() {
        val form = PatientFormState(null, PatientDraft(admittanceDate = "2026-02-30"))
        assertNull(form.admittanceDate)
    }
    @Test fun collapsingExtrasDoesNotChangeClinicalTextAssignmentsOrPriority() {
        val patient = Fixtures.patient().copy(isPriority = true, responsibleResidentId = "cedar",
            responsibleSpecialistId = "oak", tasks = listOf(Fixtures.task()))
        val form = PatientFormState(patient, null)
        form.extrasExpanded = false
        assertEquals(patient.initialDiagnosis, form.initialDiagnosis)
        assertEquals(patient.treatmentPlan, form.treatmentText)
        assertEquals(patient.followUp, form.followUpText)
        assertEquals(patient.tasks, form.tasks)
        assertEquals(patient.responsibleResidentId, form.residentId)
        assertEquals(patient.responsibleSpecialistId, form.specialistId)
        assertTrue(form.isPriority)
    }
}
