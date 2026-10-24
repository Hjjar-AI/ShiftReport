package com.hos.rushdpatients.ui.doctors

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.R
import com.hos.rushdpatients.testutil.ProductionUiFixture
import com.hos.rushdpatients.ui.navigation.Routes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in native picker/grant path. Select only the supplied synthetic CSV. */
@RunWith(AndroidJUnit4::class)
class RealDocumentProviderImportTest : ProductionUiFixture() {
    @get:Rule val compose = createEmptyComposeRule()
    private val documentName = "shiftreport-provider-doctors.csv"
    private fun label(id: Int) = client.context.getString(id)

    @Suppress("DEPRECATION")
    private fun release(node: AccessibilityNodeInfo?) {
        if (Build.VERSION.SDK_INT < 33) node?.recycle()
    }

    private fun clickSyntheticDocument(): Boolean {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(documentName).orEmpty()
        try {
            for (match in matches) {
                if (!match.isVisibleToUser || match.text?.toString() != documentName) continue
                if (match.isClickable && match.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                var parent = match.parent
                repeat(4) {
                    val current = parent ?: return@repeat
                    try {
                        if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                        parent = current.parent
                    } finally { release(current) }
                }
            }
            return false
        } finally {
            matches.forEach(::release)
            release(root)
        }
    }

    @Test fun realSystemPickerGrantReachesPreviewAndIsolatedCommit() = runBlocking<Unit> {
        assumeTrue("Opt-in: place the supplied synthetic CSV in the provider and make it visible in the picker",
            InstrumentationRegistry.getArguments().getString("realDocumentProvider") == "true")
        // No controlled ActivityResultRegistry: this launches the device's actual picker.
        launch()
        compose.waitUntil(10000) { route == Routes.WARD && !ward.state.value.loading }
        onMain { nav.navigate(Routes.DOCTORS) }
        compose.waitUntil(10000) { route == Routes.DOCTORS && !doctorVm.state.value.loading }
        val before = client.doctors.getAllIncludingDeleted()
        compose.onNodeWithText(label(R.string.ui_doctor_registry_tools)).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.ui_doctors_screen_015)).performScrollTo().performClick()
        // OEM navigation can need more time than an already-visible fixture.
        // An explicit opt-in argument extends the selection window, never import timeouts.
        val selectionTimeout = InstrumentationRegistry.getArguments().getString("providerSelectionTimeoutMs")
            ?.toLongOrNull()?.coerceIn(30000L, 120000L) ?: 30000L
        compose.waitUntil(selectionTimeout) {
            // Native providers may expose filenames differently to accessibility.
            // A real manual selection is valid too; the exact fixture is checked below.
            val state = doctorVm.state.value
            state.importing || state.importPreview != null || state.importError != null || clickSyntheticDocument()
        }
        compose.waitUntil(15000) { !doctorVm.state.value.importing &&
            (doctorVm.state.value.importPreview != null || doctorVm.state.value.importError != null) }
        val artifacts = File(client.context.getExternalFilesDir(null), "verification-artifacts/real-provider").apply { mkdirs() }
        File(artifacts, "result.txt").writeText(
            "API=${Build.VERSION.SDK_INT}\nmodel=${Build.MODEL}\npreview=${doctorVm.state.value.importPreview != null}\n" +
                "error=${doctorVm.state.value.importError.orEmpty()}\n")
        assertNull("Synthetic real-provider import failed; see private verification artifact", doctorVm.state.value.importError)
        assertEquals(before, client.doctors.getAllIncludingDeleted())
        assertEquals(listOf("د. بلوط شجرة"), doctorVm.state.value.importPreview?.added)
        compose.onNodeWithText(label(R.string.ui_doctors_screen_043)).performClick()
        compose.waitUntil(15000) { !doctorVm.state.value.importing && doctorVm.state.value.importPreview == null }
        assertNotNull(client.doctors.getById("provider-oak"))
        assertEquals(before.size + 1, client.doctors.getAllIncludingDeleted().size)
        // Any publication goes only to the fixture's intercepted synthetic server.
        assertTrue(server.deliveredTexts.isEmpty())
    }
}
