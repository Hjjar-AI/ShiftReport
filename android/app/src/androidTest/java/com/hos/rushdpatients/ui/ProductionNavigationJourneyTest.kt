package com.hos.rushdpatients.ui

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.config.ProjectProvisioningManager
import com.hos.rushdpatients.sync.CsvBundleCodec
import com.hos.rushdpatients.testutil.ProductionUiFixture
import com.hos.rushdpatients.ui.navigation.Routes
import com.hos.rushdpatients.ui.report.ReportStepOutcome
import com.hos.rushdpatients.ui.setup.ProjectSetupMode
import com.hos.rushdpatients.util.NetworkStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual WardAppRoot and WardNavHost with preinstalled isolated production ViewModels. */
@RunWith(AndroidJUnit4::class)
class ProductionNavigationJourneyTest : ProductionUiFixture() {
    @get:Rule val compose = createEmptyComposeRule()
    private fun label(id: Int, vararg args: Any) = client.context.getString(id, *args)
    private fun awaitWard() = compose.waitUntil(15000) { route == Routes.WARD && !ward.state.value.loading }
    private fun loginThroughUi() {
        compose.waitUntil(10000) { !login.state.value.busy }
        compose.onNodeWithText(client.actor.fullName).performClick()
        compose.onNode(hasSetTextAction() and hasText(label(R.string.ui_local_pin_login_screen_003)))
            .performTextInput("2468")
        compose.onNodeWithText(label(R.string.ui_local_pin_login_screen_004)).performClick()
        awaitWard()
    }
    private fun openPatientEditor() {
        val card = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and
            hasAnyDescendant(hasText("شاي", substring = true))
        val actions = compose.onNode(card, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertTrue(actions.first { it.label == label(R.string.ui_edit_patient_dialog_001) }.action())
        }
    }
    private fun editLabs(value: String, recreate: Boolean = false) {
        openPatientEditor()
        val field = hasSetTextAction() and hasText(label(R.string.ui_labs), substring = true)
        compose.onNode(field).performScrollTo().performTextInput(value)
        if (recreate) {
            recreateActivity()
            compose.onNode(field).performScrollTo().assertTextContains(value, substring = true)
        }
        compose.onNodeWithText(label(R.string.ui_save)).assertIsDisplayed().performClick()
        compose.waitUntil(10000) { !ward.state.value.saving && ward.state.value.patients.any { it.labs.contains(value) } }
    }
    private fun openReportThroughDrawer() {
        compose.onNodeWithContentDescription(label(R.string.ui_ward_navigation_chrome_004)).performClick()
        compose.onNode(hasText(label(R.string.ui_send_report)) and hasClickAction() and
            hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))).performScrollTo().performClick()
        compose.waitUntil(15000) { route == Routes.REPORT_PREVIEW && !report.state.value.loading }
    }
    private fun next(stepLabel: Int) = compose.onNodeWithText(label(R.string.ui_report_preview_screen_017, label(stepLabel)))
        .assertIsEnabled().performClick()
    private fun reviewAndOpenConfirmation() {
        compose.onNode(hasText(label(R.string.ui_report_preview_screen_031)) and isToggleable())
            .performScrollTo().performClick()
        compose.waitUntil(10000) { !report.state.value.reportAsPdf && !report.state.value.isBusy }
        next(R.string.ui_report_review_send_step)
        compose.onNode(hasText(label(R.string.report_preview_destination, label(R.string.ui_report_preview_screen_025))) and
            hasClickAction() and hasAnyAncestor(hasScrollAction()).not()).assertIsDisplayed().performClick()
        compose.onNodeWithText(label(R.string.ui_close)).performClick()
        compose.onNodeWithText(label(R.string.ui_send_report)).assertIsEnabled().performClick()
        compose.onNode(hasText(label(R.string.ui_report_send_confirmation_001)) and
            hasAnyAncestor(isDialog()) and hasClickAction().not()).assertExists()
        assertTrue(server.deliveredTexts.isEmpty())
    }
    @Test fun protectedJoinLoginEditReviewAndExplicitPublishUseProductionRootAndGraph() = runBlocking<Unit> {
        assumeTrue("Production send gate requires validated device connectivity; HTTP still stays intercepted",
            NetworkStatus.isOnline(client.context))
        val provisioning = ProjectProvisioningManager(client.context, client.dispatchers)
        val file = File(client.context.cacheDir, "synthetic.srjoin.json")
        provisioning.writeExport(Uri.fromFile(file), provisioning.prepareExport(client.projects.current(), "synthetic password".toCharArray()))
        launch(loggedIn = false, joinSetup = true)
        onMain { setup.setMode(ProjectSetupMode.JOIN); setup.setProvisioningPassphrase("synthetic password"); setup.importProvisioning(Uri.fromFile(file)) }
        compose.waitUntil(10000) { setup.state.value.importedProvisioning && !setup.state.value.busy }
        compose.onNodeWithText(label(R.string.ui_project_setup_screen_039)).assertIsDisplayed().performClick()
        compose.waitUntil(15000) { client.projects.current().initialized }
        loginThroughUi(); editLabs("نتيجة رحلة تجريبية", recreate = true)
        openReportThroughDrawer(); reviewAndOpenConfirmation()
        compose.onNode(hasText(label(R.string.ui_report_send_confirmation_008)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(20000) { !report.state.value.sending && report.state.value.deliveryOutcome == ReportStepOutcome.CONFIRMED }
        assertTrue(server.deliveredTexts.joinToString().contains("نتيجة رحلة تجريبية"))
        val fileId = requireNotNull(server.state.csvFileId)
        val snapshot = CsvBundleCodec.decode(requireNotNull(server.files[fileId]).toString(Charsets.UTF_8))
        assertTrue(snapshot.flatMap { it.patients }.single { it.id == "tea" }.labs.contains("نتيجة رحلة تجريبية"))
    }
    @Test fun recreatedReportConfirmationNeverSendsAndRequiresPreviewAgain() {
        launch(); awaitWard(); openReportThroughDrawer(); reviewAndOpenConfirmation()
        recreateActivity()
        compose.waitUntil(10000) { route == Routes.REPORT_PREVIEW && !report.state.value.loading }
        compose.onNode(hasText(label(R.string.ui_report_send_confirmation_001)) and
            hasAnyAncestor(isDialog()) and hasClickAction().not()).assertDoesNotExist()
        compose.onNode(hasText(label(R.string.ui_send_report)) and hasClickAction()).assertDoesNotExist()
        compose.onNode(hasText(label(R.string.report_preview_destination, label(R.string.ui_report_preview_screen_025))) and
            hasClickAction() and hasAnyAncestor(hasScrollAction()).not()).assertIsEnabled()
        assertTrue(server.deliveredTexts.isEmpty())
    }
    @Test fun backgroundAndResumeWhileEditingRetainsDraftWithoutSavingOrSending() = runBlocking<Unit> {
        launch(); awaitWard()
        val before = requireNotNull(client.patients.getById("tea"))
        openPatientEditor()
        val field = hasSetTextAction() and hasText(label(R.string.ui_labs), substring = true)
        compose.onNode(field).performScrollTo().performTextInput("مسودة قبل الخلفية")
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        // Foreground authorization reads Room before returning from the temporary lock screen.
        compose.waitUntil(10000) { compose.onAllNodes(field).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(field).performScrollTo().assertTextContains("مسودة قبل الخلفية", substring = true)
        assertEquals(before, client.patients.getById("tea"))
        assertTrue(server.deliveredTexts.isEmpty())
    }
    @Test fun signOutFromRealSettingsReturnsToLoginAndRemovesClinicalNavigation() {
        launch(); awaitWard()
        onMain { nav.navigate(Routes.SETTINGS) }
        compose.waitUntil(10000) { route == Routes.SETTINGS && settingsVm.state.value.doctorName.isNotEmpty() }
        onMain { root.signOut() }
        compose.waitUntil(10000) { client.sessions.current() == null }
        compose.onNodeWithText(label(R.string.ui_local_pin_login_screen_004)).assertDoesNotExist()
        compose.onNodeWithText(client.actor.fullName).assertExists()
        assertTrue(server.deliveredTexts.isEmpty())
    }

    @Test fun actorDeactivatedWhileSendConfirmationIsOpenCannotPublishOrDeliver() = runBlocking<Unit> {
        assumeTrue("Production send gate requires validated device connectivity; HTTP stays intercepted",
            NetworkStatus.isOnline(client.context))
        // A removable ordinary admin, rather than the fixture's permanent admin.
        val actor = requireNotNull(client.doctors.getById(client.actor.id)).copy(isPermanentAdmin = false)
        client.doctors.upsert(actor)
        client.sessions.start(requireNotNull(client.sessions.current()).copy(role = actor.role))
        launch(); awaitWard(); openReportThroughDrawer(); reviewAndOpenConfirmation()
        client.doctors.softDelete(actor.id)
        val beforeShift = client.shifts.getById("today")
        val beforePatients = client.patients.getForShift("today")
        compose.onNode(hasText(label(R.string.ui_report_send_confirmation_008)) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { !report.state.value.sending && !report.state.value.loading &&
            (report.state.value.error != null || report.state.value.summary == null) }
        assertEquals(beforeShift, client.shifts.getById("today"))
        assertEquals(beforePatients, client.patients.getForShift("today"))
        assertTrue(server.deliveredTexts.isEmpty())
        assertTrue(server.calls.none { it.second in setOf("sendDocument", "editMessageText", "sendMessage") })
    }
}
