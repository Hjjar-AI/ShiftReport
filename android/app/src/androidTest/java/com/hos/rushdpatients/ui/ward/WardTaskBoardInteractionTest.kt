package com.hos.rushdpatients.ui.ward

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.data.model.PatientTask
import com.hos.rushdpatients.domain.task.WardTaskEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WardTaskBoardInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val pending = PatientTask("pending", "فحص الشاي", ownerDoctorId = "cedar")
    private val patient = Patient("tea", name = "شاي", tasks = listOf(pending), revision = 19)
    private var completed: WardTaskEntry? = null
    private var opened: String? = null
    private var dismissed = 0
    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)
    private fun show(patients: List<Patient> = listOf(patient), editable: Boolean = false, currentDoctorId: String? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { WardTaskBoard(patients, emptyList(), 1000,
                    onDismiss = { dismissed++ }, onPatient = { opened = it }, currentDoctorId = currentDoctorId,
                    editable = editable, snackbarHost = remember { SnackbarHostState() }, onComplete = { completed = it }) }
            }
        }
    }
    @Test fun readOnlyTasksRemainVisibleButCannotBeCompleted() {
        show()
        compose.onNodeWithText(pending.description).assertExists()
        compose.onNodeWithContentDescription(label(R.string.ui_patient_task_editor_009) + pending.description).assertIsNotEnabled()
        compose.runOnIdle { assertNull(completed) }
    }
    @Test fun completionPassesExactPatientRevisionAndTaskWithoutOptimisticMutation() {
        show(editable = true)
        compose.onNodeWithContentDescription(label(R.string.ui_patient_task_editor_009) + pending.description).performClick()
        compose.runOnIdle {
            assertEquals(WardTaskEntry(patient, pending), completed)
            assertEquals(19L, completed!!.patient.revision)
            assertFalse(patient.tasks.single().done)
        }
    }
    @Test fun openingPatientUsesTaskPatientIdentityWithoutCompletingIt() {
        show()
        compose.onNodeWithText(label(R.string.ui_ward_task_board_012)).performClick()
        compose.runOnIdle { assertEquals(patient.id, opened); assertNull(completed) }
    }
    @Test fun completedTasksAreExcludedFromPendingBoard() {
        val done = PatientTask("done", "مهمة مكتملة", done = true,
            completedByDoctorId = "cedar", completedByName = "أرز", completedAtEpochMillis = 1)
        show(listOf(patient.copy(tasks = listOf(pending, done))))
        compose.onNodeWithText(pending.description).assertExists()
        compose.onNodeWithText(done.description).assertDoesNotExist()
    }
    @Test fun legacyTaskOwnersDoNotFilterChecklist() {
        val other = pending.copy(id = "other", description = "فحص القهوة", ownerDoctorId = null)
        show(listOf(patient.copy(tasks = listOf(pending, other))), currentDoctorId = "cedar")
        compose.onNodeWithText(label(R.string.ui_ward_task_board_005)).assertDoesNotExist()
        compose.onNodeWithText(pending.description).assertExists()
        compose.onNodeWithText(other.description).assertExists()
    }
    @Test fun noSessionDoesNotOfferPersonalOwnershipFilter() {
        show()
        compose.onNodeWithText(label(R.string.ui_ward_task_board_005)).assertDoesNotExist()
    }
    @Test fun emptyBoardOffersCloseWithoutAnyCompletionAction() {
        show(emptyList())
        compose.onNodeWithText(label(R.string.ui_ward_task_board_006)).assertExists()
        compose.onNodeWithContentDescription(label(R.string.ui_ward_task_board_001)).performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertNull(completed) }
    }
}
