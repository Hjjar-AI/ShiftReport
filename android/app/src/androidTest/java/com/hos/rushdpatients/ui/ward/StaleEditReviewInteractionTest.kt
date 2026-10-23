package com.hos.rushdpatients.ui.ward

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StaleEditReviewInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var kept = 0; private var usedSaved = 0; private var dismissed = 0
    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)
    private fun show(available: Boolean) {
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme { StaleEditReviewDialog(listOf(Triple("تحاليل", "مسودة محلية", "نتيجة محفوظة")), available,
                onKeepDraft = { kept++ }, onUseSaved = { usedSaved++ }, onDismiss = { dismissed++ }) }
        } }
    }
    @Test fun reviewDisplaysBothVersionsWithoutApplyingEither() {
        show(true)
        compose.onNodeWithText("مسودة محلية", substring = true).assertExists()
        compose.onNodeWithText("نتيجة محفوظة", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, kept); assertEquals(0, usedSaved) }
    }
    @Test fun unavailableRecordDisablesBothReplacementActionsButAllowsReturn() {
        show(false)
        compose.onNodeWithText(label(R.string.ui_stale_edit_review_dialog_008)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.ui_stale_edit_review_dialog_009)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.ui_stale_edit_review_dialog_007)).performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertEquals(0, kept); assertEquals(0, usedSaved) }
    }
    @Test fun keepDraftRequiresExplicitActionAndDoesNotUseSavedVersion() {
        show(true)
        compose.onNodeWithText(label(R.string.ui_stale_edit_review_dialog_008)).performClick()
        compose.runOnIdle { assertEquals(1, kept); assertEquals(0, usedSaved); assertEquals(0, dismissed) }
    }
    @Test fun useSavedRequiresExplicitActionAndDoesNotKeepDraft() {
        show(true)
        compose.onNodeWithText(label(R.string.ui_stale_edit_review_dialog_009)).performClick()
        compose.runOnIdle { assertEquals(0, kept); assertEquals(1, usedSaved); assertEquals(0, dismissed) }
    }
}
