package com.hos.rushdpatients.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
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
import java.io.File

/** Reusable OS/device-matrix journey with actual density, font scale and window size. */
@RunWith(AndroidJUnit4::class)
class NativeDeviceLayoutTest : ProductionUiFixture() {
    @get:Rule val compose = createEmptyComposeRule()
    private fun label(id: Int, vararg args: Any) = client.context.getString(id, *args)
    private fun start() {
        nativeWindow = true
        launch()
        compose.waitUntil(10000) { route == Routes.WARD && !ward.state.value.loading }
    }
    private fun record(name: String) {
        val directory = File(client.context.getExternalFilesDir(null), "verification-artifacts/native-layout").apply { mkdirs() }
        var configuration = ""
        scenario!!.onActivity { activity ->
            val config = activity.resources.configuration
            configuration = "API=${Build.VERSION.SDK_INT}\nmodel=${Build.MODEL}\n" +
                "widthDp=${config.screenWidthDp}\nheightDp=${config.screenHeightDp}\n" +
                "fontScale=${config.fontScale}\norientation=${config.orientation}\n" +
                "multiWindow=${Build.VERSION.SDK_INT >= 24 && activity.isInMultiWindowMode}\n"
        }
        File(directory, "$name.txt").writeText(configuration)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun nativeWindowReportKeepsRosterAndFooterReachableAcrossRecreation() {
        start()
        onMain { nav.navigate(Routes.reportPreview("today")) }
        compose.waitUntil(10000) { route == Routes.REPORT_PREVIEW && !report.state.value.loading }
        val next = label(R.string.ui_report_preview_screen_017, label(R.string.ui_report_flow_step_003))
        compose.onNodeWithText(label(R.string.ui_shift_doctor_picker_001)).assertExists()
        compose.onNodeWithText(next).assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        compose.onNodeWithText(label(R.string.ui_cancel)).assertIsDisplayed().assertHasClickAction()
        record("report-before-recreation")
        recreateActivity()
        compose.waitUntil(10000) { route == Routes.REPORT_PREVIEW && !report.state.value.loading }
        compose.onNodeWithText(next).assertIsDisplayed().assertIsEnabled()
        record("report-after-recreation")
        assertTrue(server.deliveredTexts.isEmpty())
    }

    @Test fun nativeWindowEditorKeepsFocusedDraftAndFooterAfterRecreation() = runBlocking<Unit> {
        start()
        val before = client.patients.getById("tea")
        val card = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and
            hasAnyDescendant(hasText("شاي", substring = true))
        val actions = compose.onNode(card, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.first { it.label == label(R.string.ui_edit_patient_dialog_001) }.action()) }
        val labs = hasSetTextAction() and hasText(label(R.string.ui_labs), substring = true)
        compose.onNode(labs).performScrollTo().performClick().performTextInput("مسودة إعدادات الجهاز")
        compose.onNode(labs).assertIsFocused()
        compose.onNodeWithText(label(R.string.ui_save)).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(label(R.string.ui_cancel)).assertIsDisplayed().assertIsEnabled()
        recreateActivity()
        compose.onNode(labs).performScrollTo().assertTextContains("مسودة إعدادات الجهاز", substring = true)
        compose.onNodeWithText(label(R.string.ui_save)).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(label(R.string.ui_cancel)).assertIsDisplayed().performClick()
        assertEquals(before, client.patients.getById("tea"))
        assertTrue(server.deliveredTexts.isEmpty())
    }
}
