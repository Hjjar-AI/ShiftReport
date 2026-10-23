package com.hos.rushdpatients.ui.ward

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.testutil.ProductionUiFixture
import com.hos.rushdpatients.ui.navigation.Routes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionAdaptiveScreensTest : ProductionUiFixture() {
    @get:Rule val compose = createEmptyComposeRule()
    private fun label(id: Int, vararg args: Any) = client.context.getString(id, *args)
    private fun startNarrow() {
        widthDp = 320; heightDp = 600; fontScale = 1.6f; launch()
        compose.waitUntil(10000) { route == Routes.WARD && !ward.state.value.loading }
    }
    @Test fun narrowLargeFontDrawerStillNavigatesToRealSettings() {
        startNarrow()
        compose.onNodeWithContentDescription(label(R.string.ui_ward_navigation_chrome_004)).performClick()
        compose.onNodeWithText(label(R.string.ui_ward_drawer_017)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.ui_ward_drawer_018)).performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10000) { route == Routes.SETTINGS && settingsVm.state.value.doctorName.isNotEmpty() }
        compose.onNodeWithText(client.actor.fullName).assertExists()
        assertTrue(server.deliveredTexts.isEmpty())
    }
    @Test fun largeFontSettingsTogglePersistsAcrossNavigationAndWidthChange() = runBlocking<Unit> {
        startNarrow(); onMain { nav.navigate(Routes.SETTINGS) }
        compose.waitUntil(10000) { route == Routes.SETTINGS && settingsVm.state.value.doctorName.isNotEmpty() }
        compose.onNodeWithText(label(R.string.ui_settings_section_layout_029)).performScrollTo().performClick()
        compose.waitUntil(10000) { route == Routes.SETTINGS_DETAIL }
        val label = label(R.string.ui_edit_patient_dialog_002)
        compose.onNode(hasText(label) and isToggleable()).performScrollTo().performClick()
        compose.waitUntil(10000) { settingsVm.state.value.saveAndNext }
        onMain { widthDp = 480; heightDp = 420 }
        compose.onNode(hasText(label) and isToggleable()).performScrollTo().assertIsOn()
        onMain { nav.popBackStack() }
        compose.onNodeWithText(label(R.string.ui_settings_section_layout_029)).performScrollTo().performClick()
        compose.waitUntil(10000) { route == Routes.SETTINGS_DETAIL && settingsVm.state.value.saveAndNext }
        compose.onNode(hasText(label) and isToggleable()).performScrollTo().assertIsOn()
        assertTrue(client.settings.getBoolean(com.hos.rushdpatients.config.AppConstants.SETTING_SAVE_AND_NEXT, false))
    }
    @Test fun largeFontDoctorFormKeepsSaveReachableWhileTypingWithIme() = runBlocking<Unit> {
        startNarrow(); onMain { nav.navigate(Routes.DOCTORS) }
        compose.waitUntil(10000) { route == Routes.DOCTORS && !doctorVm.state.value.loading }
        compose.onNodeWithContentDescription(label(R.string.ui_doctors_screen_050, client.actor.fullName)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(label(R.string.ui_add_edit_doctor_dialog_003)))
            .performScrollTo().performClick().performTextReplacement("صنوبر")
        compose.onNodeWithText(label(R.string.ui_save)).assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(10000) { !doctorVm.state.value.saving && doctorVm.state.value.doctors.any { it.firstName == "صنوبر" } }
        assertEquals("صنوبر", client.doctors.getById(client.actor.id)?.firstName)
    }
    @Test fun shortLargeFontReportKeepsDoctorSelectorAndFooterActionReachable() {
        startNarrow(); onMain { heightDp = 400; nav.navigate(Routes.reportPreview("today")) }
        compose.waitUntil(10000) { route == Routes.REPORT_PREVIEW && !report.state.value.loading }
        compose.onNodeWithText(label(R.string.ui_shift_doctor_picker_001)).assertExists()
        compose.onNodeWithText(label(R.string.ui_report_preview_screen_017, label(R.string.ui_report_flow_step_003)))
            .assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(label(R.string.ui_cancel)).assertIsDisplayed().assertIsEnabled()
        assertTrue(server.deliveredTexts.isEmpty())
    }
}
