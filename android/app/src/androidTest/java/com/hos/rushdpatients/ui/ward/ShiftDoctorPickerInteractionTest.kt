package com.hos.rushdpatients.ui.ward

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Shift
import com.hos.rushdpatients.data.model.Gender
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Exercises the production selector with synthetic doctors and no repositories. */
@RunWith(AndroidJUnit4::class)
class ShiftDoctorPickerInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)
    private fun doctor(id: String, name: String) = Doctor(id, name, name, "شجرة", Gender.MALE)
    private val cedar = doctor("cedar", "أرز")
    private val oak = doctor("oak", "بلوط")
    private var confirmed: List<String>? = null
    private var dismissed = 0
    private fun show(doctors: List<Doctor> = listOf(cedar, oak), selected: List<String> = emptyList(), saving: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    ShiftDoctorPicker(doctors, selected, saving = saving,
                        onConfirm = { ids -> confirmed = ids },
                        onDismiss = { dismissed++ })
                }
            }
        }
    }
    @Test fun emptySelectionCannotSaveAndDoesNotConfirmOnOpen() {
        show()
        compose.onNodeWithText(label(R.string.ui_save)).assertIsNotEnabled()
        compose.runOnIdle { assertNull(confirmed); assertEquals(0, dismissed) }
    }
    @Test fun checkingDoctorEnablesSaveAndConfirmsExplicitSelection() {
        show()
        compose.onNodeWithText(cedar.fullName).performClick().assertIsOn()
        compose.onNodeWithText(label(R.string.ui_save)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(cedar.id), confirmed)
        }
    }
    @Test fun clearingLastDoctorDisablesSave() {
        show(selected = listOf(cedar.id))
        compose.onNodeWithText(cedar.fullName).assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText(label(R.string.ui_save)).assertIsNotEnabled()
        compose.runOnIdle { assertNull(confirmed) }
    }
    @Test fun largeExistingRosterIsRetainedWithoutFixedCap() {
        val roster = (1..40).map { doctor("tree-$it", "أرز $it") }
        show(doctors = roster, selected = roster.map { it.id })
        compose.onNodeWithText(label(R.string.ui_save)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(roster.map { it.id }, confirmed) }
    }
    @Test fun deletedDoctorsAreNotOfferedForSelection() {
        show(doctors = listOf(cedar, oak.copy(deletedAt = Instant.EPOCH)))
        compose.onNodeWithText(oak.fullName).assertDoesNotExist()
        compose.onNodeWithText(cedar.fullName).assertExists()
    }
    @Test fun arabicSearchFindsNormalizedNameAndPreservesHiddenSelection() {
        show(selected = listOf(oak.id))
        compose.onNode(hasSetTextAction()).performTextInput("ارز")
        compose.onNodeWithText(cedar.fullName).performClick()
        compose.onNodeWithText(oak.fullName).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.ui_save)).performClick()
        compose.runOnIdle { assertEquals(listOf(oak.id, cedar.id), confirmed) }
    }
    @Test fun cleanCancelDismissesWithoutConfirming() {
        show(selected = listOf(cedar.id))
        compose.onNodeWithText(label(R.string.ui_cancel)).performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertNull(confirmed) }
    }
    @Test fun dirtyCancelRequiresExplicitDiscardConfirmation() {
        show()
        compose.onNodeWithText(cedar.fullName).performClick()
        compose.onNodeWithText(label(R.string.ui_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, dismissed); assertNull(confirmed) }
        compose.onNodeWithText(label(R.string.ui_shift_doctor_picker_008)).assertExists()
        compose.onNodeWithText(label(R.string.ui_patient_task_editor_029)).performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertNull(confirmed) }
    }
    @Test fun savingDisablesSelectionSearchAndDismissal() {
        show(selected = listOf(cedar.id), saving = true)
        compose.onNodeWithText(cedar.fullName).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.ui_cancel)).assertIsNotEnabled()
        compose.runOnIdle { assertNull(confirmed); assertEquals(0, dismissed) }
    }
    @Test fun savingChangedRosterDoesNotOpenRevisionConflictDialog() {
        show(selected = listOf(cedar.id))
        compose.onNodeWithText(oak.fullName).performClick()
        compose.onNodeWithText(label(R.string.ui_save)).performClick()
        compose.onNodeWithText(label(R.string.ui_stale_edit_review_dialog_001)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(cedar.id, oak.id), confirmed) }
    }
    @Test fun explicitRosterReplacementConfirmsOnlySelectedDoctors() {
        show(selected = listOf(cedar.id))
        compose.onNodeWithText(cedar.fullName).performClick()
        compose.onNodeWithText(oak.fullName).performClick()
        compose.onNodeWithText(label(R.string.ui_save)).performClick()
        compose.runOnIdle { assertEquals(listOf(oak.id), confirmed) }
    }
}
