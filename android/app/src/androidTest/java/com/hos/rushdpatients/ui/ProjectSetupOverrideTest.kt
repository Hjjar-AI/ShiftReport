package com.hos.rushdpatients.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.R
import com.hos.rushdpatients.config.ProjectProvisioningManager
import com.hos.rushdpatients.domain.auth.BootstrapManager
import com.hos.rushdpatients.domain.auth.BootstrapSeeder
import com.hos.rushdpatients.network.telegram.dto.TgMessage
import com.hos.rushdpatients.testutil.IndependentClient
import com.hos.rushdpatients.testutil.IsolatedWorkflowActivity
import com.hos.rushdpatients.testutil.SharedTelegramServer
import com.hos.rushdpatients.ui.components.UiStrings
import com.hos.rushdpatients.ui.setup.ProjectSetupMode
import com.hos.rushdpatients.ui.setup.ProjectSetupScreen
import com.hos.rushdpatients.ui.setup.ProjectSetupViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual setup screen/models, fresh isolated Room/preferences and intercepted HTTP only. */
@RunWith(AndroidJUnit4::class)
class ProjectSetupOverrideTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val server = SharedTelegramServer()
    private lateinit var client: IndependentClient
    private lateinit var setup: ProjectSetupViewModel
    private var scenario: ActivityScenario<IsolatedWorkflowActivity>? = null
    private val previousFile = "synthetic previous project file".toByteArray()
    private fun label(id: Int) = client.context.getString(id)
    private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    @Before fun freshProject() {
        client = IndependentClient("setup-override", server)
        client.projects.saveDraft(client.projects.current())
        server.bootstrapPin = TgMessage(9, text = "synthetic old pin")
        server.files["previous-project-file"] = previousFile
    }
    private fun launch(scale: Float = 1f) {
        IsolatedWorkflowActivity.install = { activity ->
            val existing = activity.viewModelStore["setup-override"] as? ProjectSetupViewModel
            setup = existing ?: ProjectSetupViewModel(UiStrings(client.context), client.context, client.projects,
                client.telegram, BootstrapManager(client.context, client.telegram, client.topics, server.json, client.doctors),
                BootstrapSeeder(client.telegram, client.topics, client.projects), client.sessions, client.settings,
                ProjectProvisioningManager(client.context, client.dispatchers)).also {
                activity.viewModelStore.put("setup-override", it)
                it.setMode(ProjectSetupMode.CREATE); it.setHospitalName("مشفى تجريبي")
                it.setBotToken("12345:" + "a".repeat(24)); it.setChatId("-100")
                it.setAdminName("أرز شجرة"); it.setAdminTelegramId("123")
            }
        }
        IsolatedWorkflowActivity.content = {
            // Dialog creates another Android composition owner and resets LocalDensity.
            // Explicit scaled typography crosses that boundary; assert its rendered size below.
            fun TextStyle.scaled() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
            val base = Typography()
            val typography = base.copy(headlineSmall = base.headlineSmall.scaled(),
                bodyMedium = base.bodyMedium.scaled(), bodyLarge = base.bodyLarge.scaled(),
                labelLarge = base.labelLarge.scaled())
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(typography = typography) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { ProjectSetupScreen(setup) }
                }
            }
        }
        scenario = ActivityScenario.launch(IsolatedWorkflowActivity::class.java)
        compose.onNodeWithText(label(R.string.ui_project_setup_screen_040)).assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { setup.state.value.confirmPinnedProjectReplacement }
        compose.onNodeWithText(label(R.string.ui_setup_replace_pin_title)).assertIsDisplayed()
    }
    private fun record(name: String) {
        val directory = File(client.context.getExternalFilesDir(null), "verification-artifacts/setup-override").apply { mkdirs() }
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    private fun assertNoPublication() {
        assertFalse(client.projects.current().initialized)
        assertTrue(server.calls.none { it.second in setOf("sendDocument", "pinChatMessage") })
        assertArrayEquals(previousFile, server.files["previous-project-file"])
    }
    @Test fun cancelLeavesOldPinAndFilesUntouched() {
        launch(); record("cancel")
        compose.onNodeWithText(label(R.string.ui_cancel)).performClick()
        compose.onNodeWithText(label(R.string.ui_setup_replace_pin_title)).assertDoesNotExist()
        assertEquals(9L, server.bootstrapPin?.messageId)
        assertNoPublication()
    }
    @Test fun confirmCreatesFreshRegistryAndInitializesProject() = runBlocking<Unit> {
        launch(); record("confirm")
        assertNoPublication()
        compose.onNodeWithText(label(R.string.ui_setup_replace_pin_confirm)).assertIsDisplayed().performClick()
        compose.waitUntil(20_000) { client.projects.current().initialized }
        val doctors = client.doctors.getAll()
        assertEquals(123L, doctors.single().telegramId)
        assertTrue(doctors.single().isPermanentAdmin)
        assertTrue(server.bootstrapPin?.document != null)
        assertEquals(1, server.calls.count { it.second == "sendDocument" })
        assertEquals(1, server.calls.count { it.second == "pinChatMessage" })
        assertArrayEquals(previousFile, server.files["previous-project-file"])
    }
    @Test fun pinChangedAfterDialogCannotBeOverriddenWithoutNewReview() {
        launch()
        onMain { server.bootstrapPin = TgMessage(20, text = "synthetic changed pin") }
        compose.onNodeWithText(label(R.string.ui_setup_replace_pin_confirm)).performClick()
        compose.waitUntil(15_000) { !setup.state.value.busy && setup.state.value.error != null }
        compose.onNodeWithText(requireNotNull(setup.state.value.error)).assertIsDisplayed()
        assertEquals(20L, server.bootstrapPin?.messageId)
        assertNoPublication()
    }
    @Test fun recreationRetainsReviewWithoutPublishingAndStillAllowsCancel() {
        launch()
        scenario!!.recreate()
        compose.onNodeWithText(label(R.string.ui_setup_replace_pin_title)).assertIsDisplayed()
        record("after-recreation")
        assertNoPublication()
        compose.onNodeWithText(label(R.string.ui_cancel)).performClick()
        assertNoPublication()
    }
    @Test fun twoHundredPercentFontKeepsWarningAndActionsReachable() {
        launch(scale = 2f)
        val confirm = compose.onNodeWithText(label(R.string.ui_setup_replace_pin_confirm))
        val cancel = compose.onNodeWithText(label(R.string.ui_cancel))
        for (node in listOf(confirm, cancel)) {
            node.assertIsDisplayed().assertIsEnabled().assertHasClickAction()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val minimum = 48 * client.context.resources.displayMetrics.density
            assertTrue(bounds.width >= minimum - 1); assertTrue(bounds.height >= minimum - 1)
        }
        val warning = compose.onNodeWithText(label(R.string.ui_setup_replace_pin_message)).assertIsDisplayed()
        record("large-font")
        val layouts = mutableListOf<TextLayoutResult>()
        warning.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertTrue("Dialog must actually render enlarged text", layouts.single().layoutInput.style.fontSize.value >= 28f)
        assertFalse("Warning must wrap without losing text", layouts.single().hasVisualOverflow)
        cancel.performClick()
        assertNoPublication()
    }
    @After fun close() {
        scenario?.close()
        IsolatedWorkflowActivity.clearFixture()
        if (::client.isInitialized) client.close()
    }
}
