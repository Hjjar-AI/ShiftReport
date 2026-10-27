package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.ui.theme.RushdPatientsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatientCardActionsTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private var expanded by mutableStateOf(true)
    private var edits = 0
    private fun show(readOnly: Boolean) {
        val patient = Patient(name = "شاي", initialDiagnosis = "تشخيص توضيحي", treatmentPlan = "خطة توضيحية", labs = "نتيجة توضيحية")
        val actions = PatientCardActions({}, {}, { edits++ }, {}, {}, {}, {}, { expanded = !expanded })
        compose.setContent { RushdPatientsTheme { PatientCard(PatientCardState(patient, expanded = expanded, readOnly = readOnly), actions) } }
    }
    @Test fun expandedCardOmitsFullFileKeepsEditAndExpansion() {
        show(false)
        compose.onNodeWithText(label(R.string.ui_ui_action_002)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.ui_ward_form_dialog_001)).performClick()
        compose.runOnIdle { assertEquals(1, edits) }
        compose.onNodeWithContentDescription(label(R.string.ui_patient_card_027)).performClick()
        compose.runOnIdle { assertFalse(expanded) }
        compose.onNodeWithContentDescription(label(R.string.ui_patient_card_028)).performClick()
        compose.runOnIdle { assertTrue(expanded) }
        compose.onNodeWithText("خطة توضيحية").assertExists()
    }
    @Test fun readOnlyExpandedCardHasNoEditOrFullFileAction() {
        show(true)
        compose.onNodeWithText(label(R.string.ui_ui_action_002)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.ui_ward_form_dialog_001)).assertDoesNotExist()
        compose.onNodeWithText("خطة توضيحية").assertExists()
        compose.onNodeWithText("نتيجة توضيحية").assertExists()
        compose.runOnIdle { assertEquals(0, edits) }
    }
}
