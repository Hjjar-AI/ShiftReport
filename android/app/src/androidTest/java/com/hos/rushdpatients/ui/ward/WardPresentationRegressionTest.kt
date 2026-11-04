package com.hos.rushdpatients.ui.ward

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.config.AppConstants
import com.hos.rushdpatients.domain.sort.GroupByMode
import com.hos.rushdpatients.testutil.ProductionUiFixture
import com.hos.rushdpatients.ui.navigation.Routes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Shared production workspace, disposable database and intercepted transport. */
@RunWith(AndroidJUnit4::class)
class WardPresentationRegressionTest : ProductionUiFixture() {
    @get:Rule val compose = createEmptyComposeRule()
    private fun label(id: Int) = client.context.getString(id)
    private fun start() {
        runBlocking { client.settings.put(AppConstants.SETTING_GROUP_BY_MODE, GroupByMode.NONE.name) }
        launch()
        compose.waitUntil(15000) { route == Routes.WARD && !ward.state.value.loading }
        compose.waitForIdle()
    }
    private fun bulk(expand: Boolean) {
        compose.onNodeWithContentDescription(label(if (expand) R.string.ui_ward_navigation_chrome_006
            else R.string.ui_ward_navigation_chrome_005)).performClick()
        compose.waitUntil(10000) { ward.state.value.patientDetailsExpanded == expand }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(label(if (expand) R.string.ui_patient_card_027
            else R.string.ui_patient_card_028)).assertExists()
    }
    @Test fun repeatedBulkExpansionAndCollapseSurviveRecreationWithoutChangingPatients() {
        start()
        val before = runBlocking { client.patients.getForShift("today") }
        repeat(3) { bulk(true); bulk(false) }
        recreateActivity()
        compose.waitUntil(15000) { route == Routes.WARD && !ward.state.value.loading }
        compose.onNodeWithContentDescription(label(R.string.ui_patient_card_028)).assertExists()
        bulk(true)
        recreateActivity()
        compose.waitUntil(15000) { route == Routes.WARD && !ward.state.value.loading }
        compose.onNodeWithContentDescription(label(R.string.ui_patient_card_027)).assertExists()
        bulk(false)
        assertEquals(before, runBlocking { client.patients.getForShift("today") })
        assertTrue(server.deliveredTexts.isEmpty())
    }
    @Test fun bulkCollapseClearsAnIndividualOverrideAndNextExpansionWorks() {
        start()
        compose.onNodeWithContentDescription(label(R.string.ui_patient_card_028)).performClick()
        compose.onNodeWithContentDescription(label(R.string.ui_patient_card_027)).assertExists()
        bulk(false)
        bulk(true)
        compose.onNodeWithContentDescription(label(R.string.ui_patient_card_027)).performClick()
        compose.onNodeWithContentDescription(label(R.string.ui_patient_card_028)).assertExists()
        bulk(true)
        bulk(false)
    }
    @Test fun floatingBackToTopAppearsAtSeventhPatientAndReturnsToStart() {
        runBlocking {
            val sample = requireNotNull(client.patients.getById("tea"))
            client.patients.upsertAll((1..30).map { sample.copy(id = "tea-$it", name = "شاي $it", sortOrder = it) }, "today")
        }
        start()
        val top = label(R.string.ui_back_to_top)
        compose.onNodeWithContentDescription(top).assertDoesNotExist()
        // Index 0 is the ward header; 6 is the sixth patient, 7 is the seventh.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        compose.onNodeWithContentDescription(top).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(7)
        compose.onNodeWithContentDescription(top).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(top).assertDoesNotExist()
        compose.onNodeWithText("شاي").assertIsDisplayed()
    }
}
