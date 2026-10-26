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
        form.labText = "نتيجة لم تحفظ"
        form.tasks = listOf(Fixtures.task())
        val next = store.obtain(null, PatientDraft(name = "شاي", labDraft = "old"))
        assertSame(form, next)
        assertEquals("قهوة", next.name)
        assertEquals("نتيجة لم تحفظ", next.labText)
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
        assertEquals("نتيجة\nمسودة تحاليل", state.labText)
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
}
