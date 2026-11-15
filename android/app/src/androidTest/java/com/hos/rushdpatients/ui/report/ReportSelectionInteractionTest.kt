package com.hos.rushdpatients.ui.report

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real report selection components, offline and without application repositories. */
@RunWith(AndroidJUnit4::class)
class ReportSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)
    private fun doctor(index: Int) = Doctor("tree-$index", "طبيب تجريبي $index", "شجرة", "$index", Gender.MALE)
    private fun show(scale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, scale)) {
                MaterialTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() } }
            }
        }
    }
    private val targets = listOf(SupervisorReportTarget("cedar", "أرز", -100, 1),
        SupervisorReportTarget("oak", "بلوط", -200, 2), SupervisorReportTarget("pine", "صنوبر", null, 3))
    private var selectedAll: Pair<Boolean, Set<String>>? = null
    private val formatChanges = mutableListOf<Boolean>()
    private fun destinations(busy: Boolean = false, changeFormat: Boolean = true, pdf: Boolean = true) {
        var general by mutableStateOf(true)
        var selected by mutableStateOf(emptySet<String>())
        var asPdf by mutableStateOf(pdf)
        show {
            ReportDestinationSelection(targets, asPdf, changeFormat, busy, "تقرير تجريبي عام", general, selected,
                onGeneralSelected = { general = it },
                onSelectAll = { checked, ids -> general = checked; selected = if (checked) ids else emptySet(); selectedAll = checked to ids },
                onSupervisorSelected = { id, checked -> selected = if (checked) selected + id else selected - id },
                onReportAsPdf = { asPdf = it; formatChanges += it })
        }
    }
    private fun expandDestinations() {
        compose.onNodeWithText(text(R.string.ui_report_preview_screen_024)).performScrollTo().performClick()
    }
    @Test fun summaryContainsEveryDoctorWithoutTwoNameCollapse() {
        val roster = (1..8).map(::doctor)
        show { ReportDoctorSummary(roster, true, {}) }
        roster.forEach { compose.onNodeWithText(it.fullName).assertExists() }
        compose.onNodeWithText(roster.last().fullName).performScrollTo().assertIsDisplayed()
    }
    @Test fun longRosterRemainsReachableAtLargeFont() {
        val roster = (1..40).map { doctor(it).copy(fullName = "طبيب تجريبي باسم عربي طويل رقم $it") }
        show(scale = 1.6f) { ReportDoctorSummary(roster, true, {}) }
        compose.onNodeWithText(roster.last().fullName).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.ui_report_preview_screen_023)).performScrollTo().assertIsEnabled()
    }
    @Test fun emptyRosterStillOffersActionableDoctorSelection() {
        var opened = 0
        show { ReportDoctorSummary(emptyList(), true, { opened++ }) }
        compose.onNodeWithText(text(R.string.ui_report_preview_screen_022)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
    @Test fun busyRosterDisablesSelectionWithoutHidingNames() {
        show { ReportDoctorSummary(listOf(doctor(1)), false, { fail("Selection while busy") }) }
        compose.onNodeWithText(doctor(1).fullName).assertExists()
        compose.onNodeWithText(text(R.string.ui_report_preview_screen_023)).assertIsNotEnabled()
    }
    @Test fun destinationControlsInitiallyCollapseWithoutChangingFormat() {
        destinations()
        compose.onNode(hasText(text(R.string.ui_report_preview_screen_031)) and isToggleable()).assertDoesNotExist()
        compose.onNodeWithText("تقرير تجريبي عام • " + text(R.string.ui_report_preview_screen_031)).assertExists()
        compose.runOnIdle { assertTrue(formatChanges.isEmpty()); assertNull(selectedAll) }
    }
    @Test fun expandingAndCollapsingDestinationsDoesNotChangeSelections() {
        destinations(); expandDestinations()
        compose.onNodeWithText(text(R.string.ui_report_select_all_destinations)).performClick()
        compose.runOnIdle { assertEquals(true to setOf("cedar", "oak"), selectedAll) }
        expandDestinations(); expandDestinations()
        compose.onNodeWithText(text(R.string.ui_report_select_all_destinations)).assertIsOn()
        compose.runOnIdle { assertTrue(formatChanges.isEmpty()) }
    }
    @Test fun selectAllExcludesUnconfiguredSupervisorDestination() {
        destinations(); expandDestinations()
        compose.onNodeWithText(text(R.string.ui_report_select_all_destinations)).performClick()
        compose.runOnIdle {
            assertEquals(setOf("cedar", "oak"), selectedAll?.second)
            assertFalse(requireNotNull(selectedAll).second.contains("pine"))
        }
    }
    @Test fun reportFormatChangesOnlyAfterExplicitExpandedToggle() {
        destinations(); expandDestinations()
        compose.onNode(hasText(text(R.string.ui_report_preview_screen_031)) and isToggleable())
            .performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(listOf(false), formatChanges) }
    }
    @Test fun lockedReportFormatCannotBeChangedFromExpandedControls() {
        destinations(changeFormat = false); expandDestinations()
        compose.onNode(hasText(text(R.string.ui_report_preview_screen_031)) and isToggleable())
            .performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertTrue(formatChanges.isEmpty()) }
    }
    @Test fun busyDestinationHeaderCannotExposeMutationControls() {
        destinations(busy = true); expandDestinations()
        compose.onNode(hasText(text(R.string.ui_report_preview_screen_031)) and isToggleable()).assertDoesNotExist()
        compose.runOnIdle { assertTrue(formatChanges.isEmpty()); assertNull(selectedAll) }
    }
}
