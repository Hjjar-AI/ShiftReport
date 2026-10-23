package com.hos.rushdpatients.ui.login

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinUnlockInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)
    @Test fun pinFiltersNonDigitsSubmitsWithImeAndClearsImmediately() {
        var received: String? = null
        compose.setContent { MaterialTheme { PinUnlockScreen("أرز", null, false, { received = it }, null, {}) } }
        compose.onNode(hasSetTextAction()).performTextInput("2a468")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals("2468", received) }
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText(label(R.string.ui_pin_unlock_screen_002)).assertIsNotEnabled()
    }
    @Test fun biometricAndSignOutRequireExplicitActions() {
        var biometric = false
        var signedOut = false
        compose.setContent { MaterialTheme { PinUnlockScreen("أرز", "خطأ تجريبي", false, {},
            { biometric = true }, { signedOut = true }) } }
        compose.runOnIdle { assertFalse(biometric); assertFalse(signedOut) }
        compose.onNodeWithText(label(R.string.ui_pin_unlock_screen_003)).performClick()
        compose.onNodeWithText(label(R.string.ui_settings_preference_sections_019)).performClick()
        compose.runOnIdle { assertTrue(biometric); assertTrue(signedOut) }
    }
}
