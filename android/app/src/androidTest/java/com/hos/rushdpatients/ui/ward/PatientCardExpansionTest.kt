package com.hos.rushdpatients.ui.ward

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.data.model.Patient
import com.hos.rushdpatients.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Clock control verifies expansion behavior only; native-clock tests measure scrolling. */
@RunWith(AndroidJUnit4::class)
class PatientCardExpansionTest {
    @get:Rule val compose = createComposeRule()
    private var expanded by mutableStateOf(false)
    private var individual by mutableStateOf(false)
    private fun show() {
        val patient = Patient(name = "شاي", initialDiagnosis = "تشخيص تجريبي", treatmentPlan = "خطة تجريبية",
            followUp = "متابعة تجريبية", labs = "نتائج تجريبية")
        compose.setContent {
            RushdPatientsTheme(uiPreferences = UiPreferences()) {
                CompositionLocalProvider(LocalUiStyle provides LocalUiStyle.current.copy(expandMillis = 400, collapseMillis = 400)) {
                    PatientCard(PatientCardState(patient, expanded = expanded, animateExpansion = individual,
                        taskNowEpochMillis = 100), PatientCardActions.None, Modifier.testTag("card"))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }
    private fun height() = compose.onNodeWithTag("card").getUnclippedBoundsInRoot().height.value
    @Test fun firstIndividualExpansionRetainsIntermediateHeight() {
        show()
        val collapsed = height()
        compose.runOnIdle { individual = true; expanded = true }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val intermediate = height()
        compose.mainClock.advanceTimeBy(600)
        val complete = height()
        assertTrue("Individual expansion did not start", intermediate > collapsed)
        assertTrue("First expansion snapped instead of animating", intermediate < complete)
    }
    @Test fun bulkExpansionCompletesWithoutWaitingForMotionDuration() {
        show()
        val collapsed = height()
        compose.runOnIdle { expanded = true }
        compose.mainClock.advanceTimeBy(48)
        val immediate = height()
        compose.mainClock.advanceTimeBy(600)
        assertTrue(immediate > collapsed)
        assertEquals("Bulk expansion must remain immediate", immediate, height(), .5f)
    }
}
