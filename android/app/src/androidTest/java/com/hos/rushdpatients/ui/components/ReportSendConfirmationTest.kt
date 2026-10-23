package com.hos.rushdpatients.ui.components

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.hos.rushdpatients.R
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class ReportSendConfirmationTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)
    private fun show(enabled: Boolean, onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { ReportSendConfirmation(listOf("وجهة تجريبية"), "PDF", 1, enabled,
                    onConfirm = onConfirm, onDismiss = onDismiss) }
            }
        }
    }
    @Test fun publicationNeedsExplicitEnabledConfirmation() {
        var confirmed = false
        show(true, { confirmed = it }, {})
        compose.onNodeWithText("• وجهة تجريبية").assertExists()
        compose.runOnIdle { assertFalse(confirmed) }
        compose.onNodeWithText(label(R.string.ui_report_send_confirmation_008)).performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
    @Test fun unavailableSendCannotInvokeConfirmationAndCancelRemainsAvailable() {
        var confirmations = 0
        var dismissed = false
        show(false, { confirmations++ }, { dismissed = true })
        compose.onNodeWithText(label(R.string.ui_report_send_confirmation_008)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.ui_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, confirmations); assertTrue(dismissed) }
    }
}
