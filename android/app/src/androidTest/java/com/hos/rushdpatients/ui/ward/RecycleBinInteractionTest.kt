package com.hos.rushdpatients.ui.ward

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Patient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecycleBinInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    @Test fun loadingAndErrorNeverMasqueradeAsEmptyAndRetryIsActionable() {
        var loading by mutableStateOf(true)
        var failed by mutableStateOf(false)
        var retries = 0
        compose.setContent { MaterialTheme {
            PatientRecycleBinDialog(emptyList(), false, {}, {}, loading = loading, loadFailed = failed,
                onRetry = { retries++; loading = false; failed = false })
        } }
        compose.onNodeWithText(text(R.string.recycle_bin_loading)).assertExists()
        compose.onNodeWithText(text(R.string.ui_ward_review_dialogs_010)).assertDoesNotExist()
        compose.runOnIdle { loading = false; failed = true }
        compose.onNodeWithText(text(R.string.recycle_bin_load_failed)).assertExists()
        compose.onNodeWithText(text(R.string.ui_ward_review_dialogs_010)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.ui_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText(text(R.string.ui_ward_review_dialogs_010)).assertExists()
    }
    @Test fun busyOrReadOnlyBinCannotRestoreAndDismissRemainsAvailable() {
        var readOnly by mutableStateOf(false)
        var restores = 0
        var dismisses = 0
        compose.setContent { MaterialTheme {
            PatientRecycleBinDialog(listOf(Patient(id = "tea", name = "شاي")), readOnly,
                { restores++; readOnly = true }, { dismisses++ })
        } }
        compose.onNodeWithText(text(R.string.ui_restore)).performClick()
        compose.runOnIdle { assertEquals(1, restores) }
        compose.onNodeWithText(text(R.string.ui_restore)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.ui_close)).performClick()
        compose.runOnIdle { assertEquals(1, dismisses) }
    }
}
