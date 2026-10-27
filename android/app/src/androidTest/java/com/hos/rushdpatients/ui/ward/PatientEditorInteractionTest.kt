package com.hos.rushdpatients.ui.ward

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PatientEditorInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)
    private fun patient() = Patient(id = "synthetic", name = "شاي", admittanceNumber = "1",
        admittanceDate = LocalDate.now(), birthDate = LocalDate.of(1990, 1, 1), initialDiagnosis = "تشخيص تجريبي",
        treatmentPlan = "خطة تجريبية", followUp = "متابعة تجريبية", tasks = listOf(PatientTask("t", "فحص تجريبي")))
    @Test fun realSavePreservesLabsAndStructuredTasksInRtlEditor() {
        val patient = patient()
        val store = PatientFormStateStore()
        var saved: Patient? = null
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme { PatientFormDialog("تعديل تجريبي", patient, stateStore = store, doctors = emptyList(),
                saving = false, onConfirm = { saved = it }, onDismiss = {}) }
        } }
        compose.runOnIdle { store.obtain(patient, null).labText = "نتيجة غير محفوظة" }
        compose.onNodeWithText(label(R.string.ui_save)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(requireNotNull(saved).labs.contains("نتيجة غير محفوظة"))
            assertEquals(patient.tasks, saved?.tasks)
            assertEquals(patient.id, saved?.id)
        }
    }
    @Test fun savingDisablesBothFooterActionsAtLargeFont() {
        compose.setContent { val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { PatientFormDialog("تعديل تجريبي", patient(), doctors = emptyList(), saving = true,
                    onConfirm = { fail("Save while busy") }, onDismiss = { fail("Dismiss while busy") }) }
            }
        }
        compose.onNodeWithText(label(R.string.ui_patient_form_dialog_036)).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.ui_cancel)).assertIsDisplayed().assertIsNotEnabled()
    }
    @Test fun taskCheckboxChangesOnlyDraftCompletionAndNeverCreatesActorMetadata() {
        var tasks by mutableStateOf(listOf(PatientTask("t", "فحص تجريبي")))
        val form = PatientFormState(null, null)
        compose.setContent { MaterialTheme { PatientTaskEditor(tasks, true, formState = form, onChange = { tasks = it }) } }
        compose.onNode(isToggleable()).performClick()
        compose.runOnIdle {
            assertTrue(tasks.single().done)
            assertNull(tasks.single().completedByDoctorId)
            assertNull(tasks.single().completedAtEpochMillis)
        }
    }
    @Test fun delayedDraftCallbackForPreviousPatientCannotReplaceCurrentEditor() {
        var current by mutableStateOf(patient())
        val callbacks = mutableMapOf<String, (PatientDraft?) -> Unit>()
        var saved: Patient? = null
        compose.setContent { MaterialTheme {
            EditPatientDialog(current, emptyList(), onConfirm = { value, _ -> saved = value }, onDismiss = {},
                onLoadDraft = { id, done -> callbacks[id] = done })
        } }
        compose.runOnIdle { assertTrue(callbacks.containsKey("synthetic")); current = patient().copy(id = "coffee", name = "قهوة") }
        compose.runOnIdle { callbacks.getValue("coffee")(PatientDraft(name = "قهوة", labDraft = "current draft")) }
        compose.runOnIdle { callbacks.getValue("synthetic")(PatientDraft(name = "شاي", labDraft = "obsolete result")) }
        compose.onNodeWithText(label(R.string.ui_save)).performClick()
        compose.runOnIdle {
            assertEquals("coffee", saved?.id)
            assertEquals("قهوة", saved?.name)
            assertEquals("current draft", saved?.labs)
        }
    }
    @Test fun taskAccessibilityNamesAndToggleStateFollowCompletionAndBusyState() {
        var tasks by mutableStateOf(listOf(PatientTask("t", "فحص تجريبي")))
        var enabled by mutableStateOf(true)
        val form = PatientFormState(null, null)
        compose.setContent { MaterialTheme { PatientTaskEditor(tasks, enabled, formState = form, onChange = { tasks = it }) } }
        val pending = label(R.string.ui_patient_task_editor_009) + "فحص تجريبي"
        val completed = label(R.string.ui_patient_task_editor_008) + "فحص تجريبي"
        compose.onNodeWithContentDescription(pending).assertIsOff().assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(completed).assertIsOn()
        compose.onNodeWithContentDescription(pending).assertDoesNotExist()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithContentDescription(completed).assertIsNotEnabled()
    }

}
