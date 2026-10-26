package com.hos.rushdpatients.ui.demo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.R
import com.hos.rushdpatients.domain.report.TextReportBuilder
import com.hos.rushdpatients.pdf.*
import com.hos.rushdpatients.testutil.*
import com.hos.rushdpatients.ui.components.UiStrings
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Exercises the actual demo shell and shared production workspaces without a clinical fixture. */
@RunWith(AndroidJUnit4::class)
class DemoWorkspaceInteractionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var context: IsolatedStorageContext
    private lateinit var vm: DemoWardViewModel
    private val repository = InMemoryDemoWardRepository()
    private var scenario: ActivityScenario<IsolatedWorkflowActivity>? = null
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    @Before fun launchDemo() {
        context = IsolatedStorageContext()
        IsolatedWorkflowActivity.install = { activity ->
            val retained = activity.viewModelStore["demo"]
            vm = if (retained is DemoWardViewModel) retained else DemoWardViewModel(UiStrings(context), context,
                repository, PdfReportExporter(context), PdfAssignmentRenderer(context),
                MediaStoreSaver(), TextReportBuilder()).also { activity.viewModelStore.put("demo", it) }
        }
        IsolatedWorkflowActivity.content = { DemoWardScreen({}, vm) }
        scenario = ActivityScenario.launch(IsolatedWorkflowActivity::class.java)
        compose.waitForIdle()
    }
    @After fun closeDemo() { scenario?.close(); IsolatedWorkflowActivity.clearFixture(); context.cleanUp() }
    private fun drawer() {
        compose.onNodeWithContentDescription(text(R.string.ui_ward_navigation_chrome_004)).performClick()
    }
    private fun drawerButton(label: String) = compose.onNode(hasText(label) and hasClickAction() and
        hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)))

    @Test fun sharedTwoStepReportReviewSurvivesRecreationWithoutSending() {
        drawer()
        drawerButton(text(R.string.ui_send_report)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.ui_shift_doctor_picker_001)).assertExists()
        compose.onNode(hasText(text(R.string.ui_report_preview_screen_031)) and isToggleable())
            .performScrollTo().performClick()
        compose.waitUntil(10000) { !vm.report.state.value.reportAsPdf }
        compose.onNodeWithText(text(R.string.ui_report_preview_screen_017, text(R.string.ui_report_review_send_step))).performClick()
        val preview = hasText(text(R.string.report_preview_destination, text(R.string.ui_report_preview_screen_025))) and
            hasClickAction() and hasAnyAncestor(hasScrollAction()).not()
        compose.onNode(preview).performClick()
        compose.onNodeWithText(text(R.string.ui_close)).performClick()
        compose.onNode(hasText(text(R.string.ui_send_report)) and hasClickAction()).performClick()
        compose.onNode(hasText(text(R.string.ui_report_send_confirmation_001)) and hasAnyAncestor(isDialog()) and hasClickAction().not()).assertExists()
        assertTrue(vm.report.state.value.deliveryRecords.isEmpty())
        scenario!!.recreate()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNode(preview).assertIsEnabled().assertIsDisplayed()
        assertTrue(vm.report.state.value.deliveryRecords.isEmpty())
        assertTrue(context.filesDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun sharedAssignmentsDrawerRouteOpensLocalPreviewWithoutReportRoster() {
        main { repository.state.value = repository.state.value.copy(shiftDoctorIds = emptyList()) }
        drawer()
        drawerButton(text(R.string.assignments_export_title)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.assignments_export_title)).assertExists()
        assertTrue(vm.report.state.value.doctors.isEmpty())
        assertTrue(vm.report.state.value.deliveryRecords.isEmpty())
        assertTrue(context.filesDir.listFiles().orEmpty().isEmpty())
    }
}
