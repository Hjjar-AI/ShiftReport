package com.hos.rushdpatients.ui.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hos.rushdpatients.R
import com.hos.rushdpatients.data.model.Doctor
import com.hos.rushdpatients.data.model.Gender
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerificationClipboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = compose.activity
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var previous: ClipData? = null
    private val doctor = Doctor(id = "synthetic", fullName = "أرز", firstName = "أرز", lastName = "شجرة", gender = Gender.MALE)
    private val code = "123456"
    private var submitted: String? = null
    private var visible by mutableStateOf(true)
    private var seconds by mutableStateOf(30)
    @Before fun captureClipboard() {
        compose.waitUntil(5000) { compose.activity.window.decorView.hasWindowFocus() }
        compose.runOnIdle { previous = clipboard.primaryClip }
    }
    @After fun restoreClipboard() {
        compose.runOnIdle {
            val label = clipboard.primaryClip?.description?.label?.toString()
            if (label in setOf("ShiftReport verification", "synthetic-user-replacement") || label == null) {
                previous?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
            }
        }
    }
    private fun show() {
        compose.setContent { MaterialTheme {
            if (visible) VerifyingView(LoginStep.Verifying(doctor, seconds), false, null,
                { submitted = it }, {}) else Text("finished")
        } }
    }
    private fun enter() = compose.onNode(hasSetTextAction()).performTextInput(code)
    private fun confirm() = compose.onNodeWithText(context.getString(R.string.login_private_code_confirm))
    @Test fun enteredCodeSubmitsWithoutCopyingToClipboard() {
        show(); enter(); confirm().performClick()
        assertEquals(code, submitted)
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.runOnIdle {
            assertNotEquals(code, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
    }
    @Test fun expiryDisablesSubmissionWithoutChangingClipboard() {
        show(); enter(); compose.runOnIdle { seconds = 0 }
        compose.waitForIdle()
        confirm().assertIsNotEnabled()
        assertNull(submitted)
        compose.runOnIdle { assertNotEquals(code, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
    }
    @Test fun leavingVerificationDiscardsEnteredCode() {
        show(); enter(); compose.runOnIdle { visible = false }; compose.waitForIdle()
        compose.runOnIdle { visible = true }; compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertNull(submitted)
    }
    @Test fun leavingVerificationPreservesUserClipboardContent() {
        show(); enter()
        compose.runOnIdle {
            clipboard.setPrimaryClip(ClipData.newPlainText("synthetic-user-replacement", "synthetic later copy"))
            visible = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("synthetic later copy", clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
    }
}
